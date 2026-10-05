package com.feloguarin.lensguard;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A user-started, bounded survey of public radio, service and discovery advertisements. */
public final class WirelessProbe {
    public interface Listener {
        void onUpdate(Survey survey);
    }

    public static final String WIFI = "wifi";
    public static final String BLE = "ble";
    public static final String SERVICE = "service";
    public static final String ONVIF = "onvif";
    public static final String UPNP = "upnp";
    public static final String HINT_CAMERA_NAME = "camera_name";
    public static final String HINT_VIDEO_SERVICE = "video_service";
    public static final String HINT_CAMERA_ROLE = "camera_role";
    public static final int READY = 0, SCANNING = 1, COMPLETE = 2, STOPPED = 3;
    public static final int NO_RSSI = Integer.MIN_VALUE;

    static final long SCAN_DURATION_MS = 15_000;
    private static final long UPDATE_INTERVAL_MS = 500;
    private static final int MAX_OBSERVATIONS = 64;
    private static final String[] SERVICE_TYPES = {
            "_rtsp._tcp.", "_http._tcp.", "_onvif._tcp."
    };

    /** One observed advertiser. Names and addresses are cleaned, untrusted radio text. */
    public static final class Device {
        public final String kind;
        public final String key;
        public final String name;
        public final String address;
        public final String detail;
        public final int rssi;
        public final int frequency;
        public final long seenMs;
        public final boolean fresh;
        public final boolean lost;
        public final List<String> hints;

        Device(String kind, String key, String name, String address, String detail, int rssi,
               int frequency, long seenMs, boolean fresh, boolean lost, List<String> hints) {
            this.kind = kind;
            this.key = key;
            this.name = name;
            this.address = address;
            this.detail = detail;
            this.rssi = rssi;
            this.frequency = frequency;
            this.seenMs = seenMs;
            this.fresh = fresh;
            this.lost = lost;
            this.hints = Collections.unmodifiableList(hints);
        }

        public boolean followable() {
            return BLE.equals(kind) && address != null && BluetoothAdapter.checkBluetoothAddress(address);
        }

        JSONObject toJson(long nowMs) throws JSONException {
            JSONObject json = new JSONObject();
            json.put("kind", kind);
            json.put("name", name);
            if (address != null) json.put("address", address);
            if (detail != null) json.put("detail", detail);
            if (rssi != NO_RSSI) json.put("rssiDbm", rssi);
            if (frequency > 0) json.put("frequencyMHz", frequency);
            if (seenMs > 0 && seenMs <= nowMs) json.put("secondsSinceSeen", (nowMs - seenMs) / 1000);
            json.put("thisSurvey", fresh);
            if (lost) json.put("advertisementLost", true);
            JSONArray list = new JSONArray();
            for (String hint : hints) list.put(hint);
            json.put("hints", list);
            return json;
        }
    }

    /** An immutable view of the survey for the screen and for reports. */
    public static final class Survey {
        public final int state;
        public final int remainingSeconds;
        public final String wifiStatus;
        public final String bleStatus;
        public final String networkStatus;
        public final List<Device> wifi;
        public final List<Device> ble;
        public final List<Device> network;
        public final long createdMs;

        Survey(int state, int remainingSeconds, String wifiStatus, String bleStatus, String networkStatus,
               List<Device> wifi, List<Device> ble, List<Device> network) {
            this.state = state;
            this.remainingSeconds = remainingSeconds;
            this.wifiStatus = wifiStatus;
            this.bleStatus = bleStatus;
            this.networkStatus = networkStatus;
            this.wifi = Collections.unmodifiableList(wifi);
            this.ble = Collections.unmodifiableList(ble);
            this.network = Collections.unmodifiableList(network);
            this.createdMs = SystemClock.elapsedRealtime();
        }

        public boolean running() { return state == SCANNING; }

        /** Every observation with a hint, strongest signal first. */
        public List<Device> leads() {
            List<Device> result = new ArrayList<>();
            for (List<Device> group : Arrays.asList(network, ble, wifi)) {
                for (Device device : group) if (!device.hints.isEmpty()) result.add(device);
            }
            return result;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("state", state == COMPLETE ? "complete" : state == SCANNING ? "scanning" : state == STOPPED ? "stopped" : "ready");
            json.put("wifiStatus", wifiStatus);
            json.put("bluetoothStatus", bleStatus);
            json.put("networkStatus", networkStatus);
            json.put("wifi", array(wifi));
            json.put("bluetooth", array(ble));
            json.put("network", array(network));
            return json;
        }

        private JSONArray array(List<Device> devices) throws JSONException {
            JSONArray array = new JSONArray();
            for (Device device : devices) array.put(device.toJson(createdMs));
            return array;
        }
    }

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final WifiManager wifi;
    private final NsdManager nsd;
    private final Map<String, Device> wifiResults = new LinkedHashMap<>();
    private final Map<String, Device> bleResults = new LinkedHashMap<>();
    private final Map<String, Device> networkResults = new LinkedHashMap<>();
    private final Map<String, Set<String>> upnpRoles = new LinkedHashMap<>();
    private final List<ServiceDiscovery> discoveries = new ArrayList<>();

    private BroadcastReceiver wifiReceiver;
    private BluetoothLeScanner bleScanner;
    private ScanCallback bleCallback;
    private WifiManager.MulticastLock multicastLock;
    private NetworkDiscovery networkDiscovery;
    private Runnable timeout;
    private int state = READY;
    private boolean updatePending;
    private boolean mdnsActive;
    private boolean probeActive;
    private boolean bleListening;
    private boolean wifiAwaiting;
    private int session;
    private long startedAtMs;
    private long lastPublishedMs;
    private String wifiStatus;
    private String bleStatus;
    private String networkStatus;

    private final Runnable publishTask = () -> {
        updatePending = false;
        publishNow();
    };

    private final Runnable countdown = new Runnable() {
        @Override public void run() {
            if (state != SCANNING) return;
            publishNow();
            handler.postDelayed(this, 1000);
        }
    };

    public WirelessProbe(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        wifi = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
        nsd = (NsdManager) this.context.getSystemService(Context.NSD_SERVICE);
        wifiStatus = bleStatus = networkStatus = this.context.getString(R.string.nearby_not_started);
    }

    /** Call only from an explicit user action, after requesting runtime permissions. */
    public void start() {
        runOnMain(this::startOnMain);
    }

    /** Idempotent. The activity must call this when it leaves the foreground. */
    public void stop() {
        runOnMain(() -> {
            if (state != SCANNING) return;
            stopOnMain(STOPPED);
        });
    }

    public boolean isRunning() { return state == SCANNING; }

    public Survey survey() {
        long remaining = state == SCANNING
                ? Math.max(0, (SCAN_DURATION_MS - (SystemClock.elapsedRealtime() - startedAtMs) + 999) / 1000) : 0;
        return new Survey(state, (int) remaining, wifiStatus, bleStatus, networkStatus,
                sorted(wifiResults), sorted(bleResults), new ArrayList<>(networkResults.values()));
    }

    private static List<Device> sorted(Map<String, Device> devices) {
        List<Device> list = new ArrayList<>(devices.values());
        list.sort(Comparator.comparingInt((Device device) -> device.rssi).reversed());
        return list;
    }

    private void startOnMain() {
        if (state == SCANNING) stopOnMain(STOPPED);
        session++;
        final int currentSession = session;
        state = SCANNING;
        startedAtMs = SystemClock.elapsedRealtime();
        wifiResults.clear();
        bleResults.clear();
        networkResults.clear();
        upnpRoles.clear();
        wifiStatus = bleStatus = networkStatus = context.getString(R.string.nearby_starting);
        startWifi(currentSession);
        startBle(currentSession);
        startNetwork(currentSession);
        timeout = () -> {
            if (isCurrent(currentSession)) stopOnMain(COMPLETE);
        };
        handler.postDelayed(timeout, SCAN_DURATION_MS);
        publishNow();
        handler.postDelayed(countdown, 1000);
    }

    private void stopOnMain(int endState) {
        state = endState;
        handler.removeCallbacks(countdown);
        session++; // Discard late broadcasts and callbacks from the previous survey.
        if (timeout != null) handler.removeCallbacks(timeout);
        timeout = null;
        handler.removeCallbacks(publishTask);
        updatePending = false;
        if (wifiReceiver != null) {
            try {
                context.unregisterReceiver(wifiReceiver);
            } catch (RuntimeException ignored) {
                // It was already unregistered or registration never completed.
            }
            wifiReceiver = null;
        }
        if (bleScanner != null && bleCallback != null) {
            try {
                bleScanner.stopScan(bleCallback);
            } catch (SecurityException ignored) {
                // Android can revoke scan permission during an active survey.
            } catch (RuntimeException ignored) {
                // Permission or radio state can change while a survey is running.
            }
        }
        bleScanner = null;
        bleCallback = null;
        for (ServiceDiscovery discovery : new ArrayList<>(discoveries)) {
            discovery.stop();
        }
        discoveries.clear();
        if (networkDiscovery != null) networkDiscovery.stop();
        networkDiscovery = null;
        if (multicastLock != null) {
            try {
                if (multicastLock.isHeld()) multicastLock.release();
            } catch (RuntimeException ignored) {
                // Never retain the optional lock after the survey.
            }
            multicastLock = null;
        }
        if (bleListening) bleStatus = context.getString(R.string.nearby_ble_ended);
        if (mdnsActive || probeActive) networkStatus = context.getString(R.string.nearby_network_ended, networkResults.size());
        if (wifiAwaiting) wifiStatus = context.getString(R.string.nearby_wifi_no_new_scan);
        mdnsActive = probeActive = bleListening = wifiAwaiting = false;
        publishNow();
    }

    private void startWifi(final int currentSession) {
        if (wifi == null) {
            wifiStatus = context.getString(R.string.nearby_wifi_unavailable);
            return;
        }
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            wifiStatus = context.getString(R.string.nearby_wifi_needs_location_permission);
            return;
        }
        if (!locationEnabled()) {
            wifiStatus = context.getString(R.string.nearby_wifi_needs_location);
            return;
        }
        try {
            if (!wifi.isWifiEnabled()) {
                wifiStatus = context.getString(R.string.nearby_wifi_off);
                return;
            }
            wifiReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context receiverContext, Intent intent) {
                    if (!isCurrent(currentSession)) return;
                    boolean updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                    wifiAwaiting = false;
                    wifiStatus = context.getString(updated ? R.string.nearby_wifi_updated : R.string.nearby_wifi_cached);
                    readWifiResults();
                    schedulePublish();
                }
            };
            IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(wifiReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(wifiReceiver, filter);
            }
            readWifiResults();
            @SuppressWarnings("deprecation") boolean accepted = wifi.startScan();
            wifiAwaiting = accepted;
            wifiStatus = context.getString(accepted ? R.string.nearby_wifi_requested : R.string.nearby_wifi_throttled);
        } catch (SecurityException denied) {
            wifiStatus = context.getString(R.string.nearby_wifi_denied);
        } catch (RuntimeException unavailable) {
            wifiStatus = context.getString(R.string.nearby_wifi_failed);
        }
    }

    @SuppressWarnings("deprecation")
    private void readWifiResults() {
        try {
            List<ScanResult> results = new ArrayList<>(wifi.getScanResults());
            results.sort(Comparator.comparingInt((ScanResult result) -> result.level).reversed());
            wifiResults.clear();
            long nowMs = SystemClock.elapsedRealtime();
            for (ScanResult result : results) {
                if (wifiResults.size() >= MAX_OBSERVATIONS) break;
                String address = Text.clean(result.BSSID, null);
                String name = Text.clean(result.SSID, null);
                String key = address + "/" + result.frequency;
                long observedMs = result.timestamp / 1000;
                boolean known = result.timestamp > 0 && observedMs <= nowMs;
                List<String> hints = new ArrayList<>();
                if (DeviceHints.cameraLikeName(name)) hints.add(HINT_CAMERA_NAME);
                wifiResults.put(key, new Device(WIFI, key, name, address, null, result.level, result.frequency,
                        known ? observedMs : 0, known && observedMs >= startedAtMs, false, hints));
            }
        } catch (SecurityException denied) {
            wifiStatus = context.getString(R.string.nearby_wifi_results_denied);
        } catch (RuntimeException unavailable) {
            wifiStatus = context.getString(R.string.nearby_wifi_results_unavailable);
        }
    }

    private void startBle(final int currentSession) {
        if (Build.VERSION.SDK_INT >= 31
                && (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)
                || !hasPermission(Manifest.permission.BLUETOOTH_CONNECT))) {
            bleStatus = context.getString(R.string.nearby_ble_needs_permission);
            return;
        }
        if (Build.VERSION.SDK_INT < 31
                && (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || !locationEnabled())) {
            bleStatus = context.getString(R.string.nearby_ble_needs_location);
            return;
        }
        try {
            BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null) {
                bleStatus = context.getString(R.string.nearby_ble_unavailable);
                return;
            }
            if (!adapter.isEnabled()) {
                bleStatus = context.getString(R.string.nearby_ble_off);
                return;
            }
            bleScanner = adapter.getBluetoothLeScanner();
            if (bleScanner == null) {
                bleStatus = context.getString(R.string.nearby_ble_scanner_unavailable);
                return;
            }
            bleCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, android.bluetooth.le.ScanResult result) {
                    runOnMain(() -> acceptBle(currentSession, result));
                }

                @Override
                public void onBatchScanResults(List<android.bluetooth.le.ScanResult> results) {
                    runOnMain(() -> {
                        if (!isCurrent(currentSession)) return;
                        for (android.bluetooth.le.ScanResult result : results) acceptBle(currentSession, result);
                    });
                }

                @Override
                public void onScanFailed(int errorCode) {
                    runOnMain(() -> {
                        if (!isCurrent(currentSession)) return;
                        bleListening = false;
                        bleStatus = context.getString(R.string.nearby_ble_failed, errorCode);
                        schedulePublish();
                    });
                }
            };
            bleScanner.startScan(Collections.emptyList(), new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_BALANCED).build(), bleCallback);
            bleListening = true;
            bleStatus = context.getString(R.string.nearby_ble_listening);
        } catch (SecurityException denied) {
            bleStatus = context.getString(R.string.nearby_ble_denied);
        } catch (RuntimeException unavailable) {
            bleStatus = context.getString(R.string.nearby_ble_start_failed);
        }
    }

    private void acceptBle(int currentSession, android.bluetooth.le.ScanResult result) {
        if (!isCurrent(currentSession) || result == null) return;
        ScanRecord record = result.getScanRecord();
        String name = record == null ? null : record.getDeviceName();
        String address = null;
        BluetoothDevice device = result.getDevice();
        if (device != null) {
            try {
                if (Build.VERSION.SDK_INT < 31 || hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                    address = device.getAddress();
                    if (name == null || name.trim().isEmpty()) name = device.getName();
                }
            } catch (SecurityException ignored) {
                // Advertised names remain readable without querying the device object.
            }
        }
        String key = address != null ? address : "anonymous-"
                + Arrays.hashCode(record == null ? null : record.getBytes());
        if (!bleResults.containsKey(key) && bleResults.size() >= MAX_OBSERVATIONS) return;
        String cleanName = Text.clean(name, null);
        List<String> hints = new ArrayList<>();
        if (DeviceHints.cameraLikeName(cleanName)) hints.add(HINT_CAMERA_NAME);
        bleResults.put(key, new Device(BLE, key, cleanName, Text.clean(address, null), null, result.getRssi(), 0,
                SystemClock.elapsedRealtime(), true, false, hints));
        schedulePublish();
    }

    private void startNetwork(int currentSession) {
        if (wifi != null && hasPermission(Manifest.permission.CHANGE_WIFI_MULTICAST_STATE)) {
            try {
                multicastLock = wifi.createMulticastLock("LensGuard-survey");
                multicastLock.setReferenceCounted(false);
                multicastLock.acquire();
            } catch (RuntimeException ignored) {
                multicastLock = null; // Discovery can still work without an app multicast lock.
            }
        }
        if (nsd != null) {
            mdnsActive = true;
            for (String type : SERVICE_TYPES) {
                ServiceDiscovery discovery = new ServiceDiscovery(currentSession, type);
                try {
                    discoveries.add(discovery);
                    nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discovery);
                } catch (SecurityException | IllegalArgumentException denied) {
                    discoveries.remove(discovery);
                } catch (RuntimeException unavailable) {
                    discoveries.remove(discovery);
                }
            }
            if (discoveries.isEmpty()) mdnsActive = false;
        }
        networkDiscovery = new NetworkDiscovery(context, handler);
        probeActive = networkDiscovery.start(new NetworkDiscovery.Listener() {
            @Override public void onReply(DiscoveryMessages.Reply reply) {
                if (isCurrent(currentSession)) acceptReply(reply);
            }

            @Override public void onFinished(int replies, String failure) {
                if (!isCurrent(currentSession)) return;
                probeActive = false;
                networkStatus = failure == null
                        ? context.getString(R.string.nearby_network_finished, networkResults.size())
                        : context.getString(R.string.nearby_network_failed, failure);
                schedulePublish();
            }
        });
        if (!probeActive) networkDiscovery = null;
        if (probeActive) networkStatus = context.getString(R.string.nearby_network_discovering);
        else if (mdnsActive) networkStatus = context.getString(R.string.nearby_network_mdns_only);
        else networkStatus = context.getString(R.string.nearby_network_unavailable);
    }

    private void acceptReply(DiscoveryMessages.Reply reply) {
        if (!networkResults.containsKey(reply.key) && networkResults.size() >= MAX_OBSERVATIONS) return;
        String address = reply.key.substring(reply.key.indexOf('/') + 1);
        Device previous = networkResults.get(reply.key);
        Set<String> roles = upnpRoles.get(reply.key);
        if (roles == null) upnpRoles.put(reply.key, roles = new LinkedHashSet<>());
        if (reply.detail != null) roles.add(reply.detail);
        String name = reply.name != null ? reply.name : previous != null ? previous.name : null;
        boolean cameraRole = reply.cameraRole || (previous != null && previous.hints.contains(HINT_CAMERA_ROLE));
        List<String> hints = new ArrayList<>();
        if (cameraRole) hints.add(HINT_CAMERA_ROLE);
        if (DeviceHints.cameraLikeName(name)) hints.add(HINT_CAMERA_NAME);
        String detail = roles.isEmpty() ? null : Text.clean(String.join(" · ", roles), null);
        String kind = "onvif".equals(reply.protocol) ? ONVIF : UPNP;
        networkResults.put(reply.key, new Device(kind, reply.key, name, address, detail, NO_RSSI, 0,
                SystemClock.elapsedRealtime(), true, false, hints));
        schedulePublish();
    }

    private final class ServiceDiscovery implements NsdManager.DiscoveryListener {
        private final int survey;
        private final String type;
        private boolean started;
        private boolean stopRequested;
        private boolean stopInFlight;
        private boolean ended;
        private int stopAttempts;

        ServiceDiscovery(int survey, String type) {
            this.survey = survey;
            this.type = type;
        }

        void stop() {
            stopRequested = true;
            if (started) requestStop();
        }

        private void requestStop() {
            if (ended || stopInFlight) return;
            stopInFlight = true;
            stopAttempts++;
            try {
                nsd.stopServiceDiscovery(this);
            } catch (RuntimeException ignored) {
                ended = true;
            }
        }

        @Override
        public void onDiscoveryStarted(String serviceType) {
            runOnMain(() -> {
                started = true;
                // A queued discovery start may complete after the activity already stopped.
                if (stopRequested || !isCurrent(survey)) stop();
            });
        }

        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            runOnMain(() -> {
                ended = true;
                discoveries.remove(this);
            });
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
            runOnMain(() -> {
                ended = true;
                discoveries.remove(this);
            });
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            runOnMain(() -> {
                // The framework reports a failed stop while retaining the listener; retry once.
                stopInFlight = false;
                if (!ended && stopRequested && stopAttempts < 2) {
                    requestStop();
                }
            });
        }

        @Override
        public void onServiceFound(NsdServiceInfo info) {
            runOnMain(() -> {
                if (!isCurrent(survey) || info == null) return;
                String name = Text.clean(info.getServiceName(), null);
                String key = type + "/" + name;
                if (!networkResults.containsKey(key) && networkResults.size() >= MAX_OBSERVATIONS) return;
                networkResults.put(key, service(key, name, false));
                schedulePublish();
            });
        }

        @Override
        public void onServiceLost(NsdServiceInfo info) {
            runOnMain(() -> {
                if (!isCurrent(survey) || info == null) return;
                String name = Text.clean(info.getServiceName(), null);
                String key = type + "/" + name;
                if (networkResults.containsKey(key)) {
                    networkResults.put(key, service(key, name, true));
                    schedulePublish();
                }
            });
        }

        private Device service(String key, String name, boolean lost) {
            List<String> hints = new ArrayList<>();
            if (DeviceHints.videoServiceType(type)) hints.add(HINT_VIDEO_SERVICE);
            if (DeviceHints.cameraLikeName(name)) hints.add(HINT_CAMERA_NAME);
            return new Device(SERVICE, key, name, null, type, NO_RSSI, 0, SystemClock.elapsedRealtime(),
                    true, lost, hints);
        }
    }

    private void schedulePublish() {
        if (updatePending) return;
        updatePending = true;
        long delay = Math.max(0, UPDATE_INTERVAL_MS - (SystemClock.elapsedRealtime() - lastPublishedMs));
        handler.postDelayed(publishTask, delay);
    }

    private void publishNow() {
        handler.removeCallbacks(publishTask);
        updatePending = false;
        lastPublishedMs = SystemClock.elapsedRealtime();
        listener.onUpdate(survey());
    }

    private boolean hasPermission(String permission) {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean locationEnabled() {
        try {
            LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            return manager != null && manager.isLocationEnabled();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private boolean isCurrent(int survey) {
        return state == SCANNING && session == survey;
    }

    private void runOnMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else handler.post(action);
    }
}
