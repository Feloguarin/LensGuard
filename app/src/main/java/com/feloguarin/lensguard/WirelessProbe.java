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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A user-started, bounded survey of public radio and service advertisements. */
public final class WirelessProbe {
    public interface Listener {
        void onUpdate(String text);
    }

    private static final long SCAN_DURATION_MS = 15_000;
    private static final long UPDATE_INTERVAL_MS = 500;
    private static final int MAX_OBSERVATIONS = 64;
    private static final int DISPLAY_LIMIT = 20;
    private static final String[] SERVICE_TYPES = {
            "_rtsp._tcp.", "_http._tcp.", "_onvif._tcp."
    };

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final WifiManager wifi;
    private final NsdManager nsd;
    private final Map<String, WifiObservation> wifiResults = new LinkedHashMap<>();
    private final Map<String, BleObservation> bleResults = new LinkedHashMap<>();
    private final Map<String, ServiceObservation> services = new LinkedHashMap<>();
    private final List<ServiceDiscovery> discoveries = new ArrayList<>();

    private BroadcastReceiver wifiReceiver;
    private BluetoothLeScanner bleScanner;
    private ScanCallback bleCallback;
    private WifiManager.MulticastLock multicastLock;
    private Runnable timeout;
    private boolean running, hasSurvey;
    private boolean updatePending;
    private int session;
    private long startedAtMs;
    private long lastPublishedMs;
    private String lastPublishedText = "";
    private String scanStatus = "Ready for a 15-second survey.";
    private String wifiStatus = "Not started.";
    private String bleStatus = "Not started.";
    private String serviceStatus = "Not started.";

    private final Runnable publishTask = () -> {
        updatePending = false;
        publishNow();
    };

    public WirelessProbe(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        wifi = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
        nsd = (NsdManager) this.context.getSystemService(Context.NSD_SERVICE);
    }

    /** Call only from an explicit user action, after requesting runtime permissions. */
    public void start() {
        runOnMain(this::startOnMain);
    }

    /** Idempotent. The activity must call this when it leaves the foreground. */
    public void stop() {
        runOnMain(() -> {
            if (!running) return;
            stopOnMain("Survey stopped. These are observations from the last survey.");
        });
    }

    public boolean isRunning() { return running; }

    public String summary() {
        if (!hasSurvey) return "READY · Tap Start scan to begin";
        long remaining = Math.max(0, (SCAN_DURATION_MS - (SystemClock.elapsedRealtime() - startedAtMs) + 999) / 1000);
        return (running ? "SCANNING · " + remaining + " seconds left" : scanStatus.startsWith("15-second survey complete") ? "COMPLETE · Review observations below" : "STOPPED · Last survey observations")
                + "\n" + wifiResults.size() + " Wi-Fi · " + bleResults.size() + " Bluetooth · " + services.size() + " services";
    }

    private final Runnable countdown = new Runnable() {
        @Override public void run() {
            if (!running) return;
            publishNow();
            handler.postDelayed(this, 1000);
        }
    };

    private void startOnMain() {
        if (running) stopOnMain("Restarting survey.");
        session++;
        final int currentSession = session;
        running = true; hasSurvey = true;
        startedAtMs = SystemClock.elapsedRealtime();
        wifiResults.clear();
        bleResults.clear();
        services.clear();
        scanStatus = "Survey running for up to 15 seconds…";
        wifiStatus = "Starting…";
        bleStatus = "Starting…";
        serviceStatus = "Starting…";
        lastPublishedText = "";
        startWifi(currentSession);
        startBle(currentSession);
        startServices(currentSession);
        timeout = () -> {
            if (isCurrent(currentSession)) {
                stopOnMain("15-second survey complete. Start another survey to refresh.");
            }
        };
        handler.postDelayed(timeout, SCAN_DURATION_MS);
        publishNow();
        handler.postDelayed(countdown, 1000);
    }

    private void stopOnMain(String status) {
        running = false;
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
        if (multicastLock != null) {
            try {
                if (multicastLock.isHeld()) multicastLock.release();
            } catch (RuntimeException ignored) {
                // Never retain the optional lock after the survey.
            }
            multicastLock = null;
        }
        scanStatus = status;
        if (bleStatus.startsWith("Listening for")) bleStatus = "Bluetooth survey ended; results from this survey are shown below.";
        if (serviceStatus.startsWith("Discovering advertised")) serviceStatus = "Local service discovery ended; results from this survey are shown below.";
        if (wifiStatus.startsWith("One Wi-Fi scan requested")) wifiStatus = "Survey ended before a new system Wi-Fi scan arrived. Results may be cached.";
        publishNow();
    }

    private void startWifi(final int currentSession) {
        if (wifi == null) {
            wifiStatus = "Wi-Fi scanning is unavailable on this device.";
            return;
        }
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            wifiStatus = "Grant precise Location permission to inspect Wi-Fi scans.";
            return;
        }
        if (!locationEnabled()) {
            wifiStatus = "Turn on Android Location to inspect Wi-Fi scans.";
            return;
        }
        try {
            if (!wifi.isWifiEnabled()) {
                wifiStatus = "Wi-Fi is off. Turn it on for an access-point survey.";
                return;
            }
            wifiReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context receiverContext, Intent intent) {
                    if (!isCurrent(currentSession)) return;
                    boolean updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                    wifiStatus = updated ? "System scan updated; check each observation's age."
                            : "No new system scan. Cached observations may be shown.";
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
            boolean accepted = wifi.startScan();
            wifiStatus = accepted ? "One Wi-Fi scan requested; awaiting system results."
                    : "Wi-Fi scan not accepted (throttling, idle state, or radio error). Cached results only.";
        } catch (SecurityException denied) {
            wifiStatus = "Wi-Fi access denied. Check precise Location permission and the Location switch.";
        } catch (RuntimeException unavailable) {
            wifiStatus = "Wi-Fi scan could not start on this device.";
        }
    }

    @SuppressWarnings("deprecation")
    private void readWifiResults() {
        try {
            List<ScanResult> results = new ArrayList<>(wifi.getScanResults());
            results.sort(Comparator.comparingInt((ScanResult result) -> result.level).reversed());
            wifiResults.clear();
            for (ScanResult result : results) {
                if (wifiResults.size() >= MAX_OBSERVATIONS) break;
                String address = clean(result.BSSID, "Address unavailable");
                String key = address + "/" + result.frequency;
                wifiResults.put(key, new WifiObservation(clean(result.SSID, "Hidden/unnamed SSID"),
                        address, result.level, result.frequency, result.timestamp));
            }
        } catch (SecurityException denied) {
            wifiStatus = "Wi-Fi results denied; precise Location and Android Location must be enabled.";
        } catch (RuntimeException unavailable) {
            wifiStatus = "Wi-Fi results are currently unavailable.";
        }
    }

    private void startBle(final int currentSession) {
        if (Build.VERSION.SDK_INT >= 31
                && (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)
                || !hasPermission(Manifest.permission.BLUETOOTH_CONNECT))) {
            bleStatus = "Grant Nearby devices permission to inspect BLE advertisements.";
            return;
        }
        if (Build.VERSION.SDK_INT < 31
                && (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || !locationEnabled())) {
            bleStatus = "BLE scanning needs precise Location permission and Android Location enabled.";
            return;
        }
        try {
            BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null) {
                bleStatus = "Bluetooth LE is unavailable on this device.";
                return;
            }
            if (!adapter.isEnabled()) {
                bleStatus = "Bluetooth is off. Turn it on to inspect BLE advertisements.";
                return;
            }
            bleScanner = adapter.getBluetoothLeScanner();
            if (bleScanner == null) {
                bleStatus = "Bluetooth LE scanner is unavailable.";
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
                        bleStatus = "BLE scan failed (Android error " + errorCode + "). Try again later.";
                        schedulePublish();
                    });
                }
            };
            bleScanner.startScan(Collections.emptyList(), new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_BALANCED).build(), bleCallback);
            bleStatus = "Listening for BLE advertisements; no pairing or connections.";
        } catch (SecurityException denied) {
            bleStatus = "Bluetooth permission was denied or revoked.";
        } catch (RuntimeException unavailable) {
            bleStatus = "Bluetooth LE scan could not start.";
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
        bleResults.put(key, new BleObservation(clean(name, "Unnamed BLE advertiser"),
                clean(address, "Address unavailable"), result.getRssi(), SystemClock.elapsedRealtime()));
        schedulePublish();
    }

    private void startServices(int currentSession) {
        if (nsd == null) {
            serviceStatus = "Local service discovery is unavailable.";
            return;
        }
        if (wifi != null && hasPermission(Manifest.permission.CHANGE_WIFI_MULTICAST_STATE)) {
            try {
                multicastLock = wifi.createMulticastLock("LensGuard-survey");
                multicastLock.setReferenceCounted(false);
                multicastLock.acquire();
            } catch (RuntimeException ignored) {
                multicastLock = null; // Android NSD can still work without an app multicast lock.
            }
        }
        serviceStatus = "Discovering advertised RTSP, HTTP, and ONVIF services via Android mDNS.";
        for (String type : SERVICE_TYPES) {
            ServiceDiscovery discovery = new ServiceDiscovery(currentSession, type);
            try {
                discoveries.add(discovery);
                nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discovery);
            } catch (SecurityException denied) {
                discoveries.remove(discovery);
                serviceStatus = "Local service discovery denied by Android.";
            } catch (RuntimeException unavailable) {
                discoveries.remove(discovery);
                serviceStatus = "One or more service types could not be discovered on this device.";
            }
        }
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
                if (!isCurrent(survey)) return;
                serviceStatus = "mDNS " + type + " unavailable (Android error " + errorCode + ").";
                schedulePublish();
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
                String name = clean(info.getServiceName(), "Unnamed service");
                String key = type + "/" + name;
                if (!services.containsKey(key) && services.size() >= MAX_OBSERVATIONS) return;
                services.put(key, new ServiceObservation(name, type, SystemClock.elapsedRealtime(), false));
                schedulePublish();
            });
        }

        @Override
        public void onServiceLost(NsdServiceInfo info) {
            runOnMain(() -> {
                if (!isCurrent(survey) || info == null) return;
                String key = type + "/" + clean(info.getServiceName(), "Unnamed service");
                ServiceObservation observation = services.get(key);
                if (observation != null) {
                    observation.lost = true;
                    schedulePublish();
                }
            });
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
        String text = render();
        if (text.equals(lastPublishedText) && !running) return;
        lastPublishedText = text;
        lastPublishedMs = SystemClock.elapsedRealtime();
        listener.onUpdate(text);
    }

    private String render() {
        long nowMs = SystemClock.elapsedRealtime();
        StringBuilder out = new StringBuilder(scanStatus)
                .append("\n\nWI-FI ACCESS POINTS\n").append(wifiStatus)
                .append("\nAndroid normally limits foreground Wi-Fi scans to four per two minutes. ")
                .append("Results can be cached; Wi-Fi client devices may never appear.\n");
        List<WifiObservation> wifiList = new ArrayList<>(wifiResults.values());
        wifiList.sort(Comparator.comparingInt((WifiObservation item) -> item.rssi).reversed());
        int displayed = 0;
        for (WifiObservation item : wifiList) {
            if (displayed++ >= DISPLAY_LIMIT) break;
            long observedMs = item.timestampUs / 1000;
            String age = item.timestampUs <= 0 || observedMs > nowMs ? "age unknown"
                    : String.format(Locale.US, "%.1f seconds old", (nowMs - observedMs) / 1000.0);
            String freshness = item.timestampUs > 0 && observedMs >= startedAtMs ? "this survey" : "cached";
            out.append("\n• ").append(item.name).append("\n  ").append(item.address)
                    .append(" · ").append(item.rssi).append(" dBm · ").append(item.frequency).append(" MHz")
                    .append("\n  ").append(freshness).append("; ").append(age).append('\n');
        }
        appendOverflow(out, wifiList.size());
        if (wifiList.isEmpty()) out.append("\nNo access-point observations available.\n");
        out.append("\nBLUETOOTH LE ADVERTISEMENTS\n").append(bleStatus).append('\n');
        List<BleObservation> bleList = new ArrayList<>(bleResults.values());
        bleList.sort(Comparator.comparingInt((BleObservation item) -> item.rssi).reversed());
        displayed = 0;
        for (BleObservation item : bleList) {
            if (displayed++ >= DISPLAY_LIMIT) break;
            out.append("\n• ").append(item.name).append("\n  ").append(item.address)
                    .append(" · ").append(item.rssi).append(" dBm · seen ")
                    .append(Math.max(0, nowMs - item.observedMs) / 1000).append(" seconds ago\n");
        }
        appendOverflow(out, bleList.size());
        if (bleList.isEmpty()) out.append("\nNo BLE advertisements observed.\n");
        out.append("\nADVERTISED LOCAL SERVICES\n").append(serviceStatus)
                .append("\nRTSP/ONVIF names are leads to inspect, not confirmed cameras.\n");
        displayed = 0;
        for (ServiceObservation item : services.values()) {
            if (displayed++ >= DISPLAY_LIMIT) break;
            out.append("\n• ").append(item.name).append(" · ").append(item.type)
                    .append(item.lost ? " · advertisement lost" : " · advertised")
                    .append("\n  Seen ").append(Math.max(0, nowMs - item.observedMs) / 1000)
                    .append(" seconds ago; endpoint not resolved\n");
        }
        appendOverflow(out, services.size());
        if (services.isEmpty()) out.append("\nNo matching advertised services observed.\n");
        return out.toString();
    }

    private static void appendOverflow(StringBuilder out, int count) {
        if (count > DISPLAY_LIMIT) out.append("\n").append(count - DISPLAY_LIMIT)
                .append(" additional observations omitted from this view.\n");
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
        return running && session == survey;
    }

    private void runOnMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else handler.post(action);
    }

    private static String clean(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        String safe = value.replaceAll("[\\p{Cc}\\p{Cf}]", " ").trim();
        if (safe.isEmpty()) return fallback;
        return safe.length() > 80 ? safe.substring(0, 80) + "…" : safe;
    }

    private static final class WifiObservation {
        final String name;
        final String address;
        final int rssi;
        final int frequency;
        final long timestampUs;

        WifiObservation(String name, String address, int rssi, int frequency, long timestampUs) {
            this.name = name;
            this.address = address;
            this.rssi = rssi;
            this.frequency = frequency;
            this.timestampUs = timestampUs;
        }
    }

    private static final class BleObservation {
        final String name;
        final String address;
        final int rssi;
        final long observedMs;

        BleObservation(String name, String address, int rssi, long observedMs) {
            this.name = name;
            this.address = address;
            this.rssi = rssi;
            this.observedMs = observedMs;
        }
    }

    private static final class ServiceObservation {
        final String name;
        final String type;
        final long observedMs;
        boolean lost;

        ServiceObservation(String name, String type, long observedMs, boolean lost) {
            this.name = name;
            this.type = type;
            this.observedMs = observedMs;
            this.lost = lost;
        }
    }
}
