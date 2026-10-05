package com.feloguarin.lensguard;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.Collections;
import java.util.List;

/**
 * Follows one Bluetooth LE advertiser's signal strength for a bounded time so the user can walk
 * toward it. It listens only; it never pairs or connects.
 */
final class SignalFollower {
    interface Listener { void onUpdate(State state); }

    static final long DURATION_MS = 60_000;
    static final long QUIET_MS = 8_000;
    private static final long TICK_MS = 500;
    static final long HISTORY_MS = 30_000;

    static final class State {
        final boolean running;
        final String name;
        final String address;
        final int remainingSeconds;
        final int latest;
        final double smoothed;
        final int strongest;
        final int trend;
        final float[] history;
        final long quietMs;
        final String status;

        State(boolean running, String name, String address, int remainingSeconds, int latest, double smoothed,
              int strongest, int trend, float[] history, long quietMs, String status) {
            this.running = running;
            this.name = name;
            this.address = address;
            this.remainingSeconds = remainingSeconds;
            this.latest = latest;
            this.smoothed = smoothed;
            this.strongest = strongest;
            this.trend = trend;
            this.history = history;
            this.quietMs = quietMs;
            this.status = status;
        }

        boolean hasSignal() { return latest != Integer.MIN_VALUE; }
    }

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SignalTrend trend = new SignalTrend();
    private BluetoothLeScanner scanner;
    private ScanCallback callback;
    private boolean running;
    private int session;
    private long startedMs;
    private int strongest = Integer.MIN_VALUE;
    private String name;
    private String address;
    private String status;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (SystemClock.elapsedRealtime() - startedMs >= DURATION_MS) {
                stopScan(context.getString(R.string.follow_finished));
                return;
            }
            publish();
            handler.postDelayed(this, TICK_MS);
        }
    };

    SignalFollower(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        status = this.context.getString(R.string.follow_idle);
    }

    boolean isRunning() { return running; }

    String address() { return address; }

    /** Call from an explicit user action after Nearby permissions were requested. */
    void start(String targetAddress, String targetName) {
        if (targetAddress == null) return;
        stop();
        if (!targetAddress.equals(address)) {
            trend.clear();
            strongest = Integer.MIN_VALUE;
        }
        address = targetAddress;
        name = targetName;
        String problem = startScan();
        if (problem != null) {
            status = problem;
            publish();
            return;
        }
        running = true;
        startedMs = SystemClock.elapsedRealtime();
        status = context.getString(R.string.follow_listening);
        handler.removeCallbacks(ticker);
        ticker.run();
    }

    void stop() {
        if (running) stopScan(context.getString(R.string.follow_stopped));
    }

    State state() {
        long now = SystemClock.elapsedRealtime();
        long last = trend.lastSampleMs();
        long quiet = last == Long.MIN_VALUE ? (running ? now - startedMs : 0) : now - last;
        String message = status;
        if (running && quiet > QUIET_MS) message = context.getString(R.string.follow_quiet, quiet / 1000);
        int remaining = running ? (int) Math.max(0, (DURATION_MS - (now - startedMs) + 999) / 1000) : 0;
        return new State(running, name, address, remaining, trend.latest(), trend.smoothed(), strongest,
                trend.trend(), trend.recent(now, HISTORY_MS), quiet, message);
    }

    private String startScan() {
        if (Build.VERSION.SDK_INT >= 31 && (!granted(Manifest.permission.BLUETOOTH_SCAN)
                || !granted(Manifest.permission.BLUETOOTH_CONNECT))) {
            return context.getString(R.string.nearby_ble_needs_permission);
        }
        if (Build.VERSION.SDK_INT < 31 && (!granted(Manifest.permission.ACCESS_FINE_LOCATION) || !locationEnabled())) {
            return context.getString(R.string.nearby_ble_needs_location);
        }
        try {
            BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null) return context.getString(R.string.nearby_ble_unavailable);
            if (!adapter.isEnabled()) return context.getString(R.string.nearby_ble_off);
            scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) return context.getString(R.string.nearby_ble_scanner_unavailable);
            final int current = ++session;
            callback = new ScanCallback() {
                @Override public void onScanResult(int callbackType, ScanResult result) {
                    handler.post(() -> accept(current, result));
                }

                @Override public void onBatchScanResults(List<ScanResult> results) {
                    handler.post(() -> { for (ScanResult result : results) accept(current, result); });
                }

                @Override public void onScanFailed(int errorCode) {
                    handler.post(() -> {
                        if (current == session && running) stopScan(context.getString(R.string.nearby_ble_failed, errorCode));
                    });
                }
            };
            List<ScanFilter> filters = Collections.singletonList(new ScanFilter.Builder().setDeviceAddress(address).build());
            scanner.startScan(filters, new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback);
            return null;
        } catch (SecurityException denied) {
            return context.getString(R.string.nearby_ble_denied);
        } catch (RuntimeException unavailable) {
            return context.getString(R.string.nearby_ble_start_failed);
        }
    }

    private void accept(int current, ScanResult result) {
        if (!running || current != session || result == null) return;
        int rssi = result.getRssi();
        trend.add(SystemClock.elapsedRealtime(), rssi);
        strongest = Math.max(strongest, rssi);
        status = context.getString(R.string.follow_listening);
    }

    private void stopScan(String message) {
        running = false;
        session++;
        handler.removeCallbacks(ticker);
        if (scanner != null && callback != null) {
            try {
                scanner.stopScan(callback);
            } catch (SecurityException ignored) {
                // Android can revoke scan permission while following.
            } catch (RuntimeException ignored) {
                // The radio turned off while following; nothing remains to release.
            }
        }
        scanner = null;
        callback = null;
        status = message;
        publish();
    }

    private void publish() {
        listener.onUpdate(state());
    }

    private boolean granted(String permission) {
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
}
