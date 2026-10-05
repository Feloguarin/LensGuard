package com.feloguarin.lensguard;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Foreground-only inventory and readings from every publicly exposed Android sensor. */
public final class SensorMonitor implements SensorEventListener {
    public interface Listener { void onUpdate(Snapshot snapshot); }

    public static final class Snapshot {
        public final String summary;
        public final String inventory;
        public final String magnetic;
        public final String environment;
        public final String motion;
        public final String calibration;
        public final double magneticMicroTesla;
        public final double magneticDeltaMicroTesla;
        public final double baselineMicroTesla;
        /** Gyroscope magnitude in rad/s, or NaN when no fresh reading exists. */
        public final double rotationRate;
        public final boolean stationary;
        public final boolean calibrated;
        public final boolean calibrating;
        public final boolean running;
        public final int sensorCount;
        public final int liveSensorCount;

        private Snapshot(String summary, String inventory, String magnetic, String environment,
                         String motion, String calibration, double magneticMicroTesla,
                         double magneticDeltaMicroTesla, double baselineMicroTesla, double rotationRate,
                         boolean stationary, boolean calibrated, boolean calibrating, boolean running,
                         int sensorCount, int liveSensorCount) {
            this.summary = summary;
            this.inventory = inventory;
            this.magnetic = magnetic;
            this.environment = environment;
            this.motion = motion;
            this.calibration = calibration;
            this.magneticMicroTesla = magneticMicroTesla;
            this.magneticDeltaMicroTesla = magneticDeltaMicroTesla;
            this.baselineMicroTesla = baselineMicroTesla;
            this.rotationRate = rotationRate;
            this.stationary = stationary;
            this.calibrated = calibrated;
            this.calibrating = calibrating;
            this.running = running;
            this.sensorCount = sensorCount;
            this.liveSensorCount = liveSensorCount;
        }
    }

    private static final int PAUSED = 0, DISCONNECTED = 1, ARMED = 2, AWAITING = 3, REFUSED = 4,
            ACTIVITY_PERMISSION = 5, RESTRICTED = 6, FAILED = 7, RECEIVED = 8;

    private static final class Reading {
        final Sensor sensor;
        int status = PAUSED;
        float[] values;
        long timestamp;
        int accuracy = -1;
        boolean registered;

        Reading(Sensor sensor) { this.sensor = sensor; }
    }

    private final Context context;
    private final SensorManager manager;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Sensor, Reading> readings = new LinkedHashMap<>();
    private final DetectionMath.CalibrationWindow calibrationWindow =
            new DetectionMath.CalibrationWindow(3_000_000_000L, 30, 2.0);
    private Sensor magnetometer;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private float[] lastAcceleration;
    private double accelerationChange = Double.NaN;
    private double baseline = Double.NaN;
    private double baselineSpread;
    private boolean running;
    private boolean calibrating;
    private String calibrationMessage;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!running) return;
            publish();
            main.postDelayed(this, 333);
        }
    };

    private final TriggerEventListener triggerListener = new TriggerEventListener() {
        @Override public void onTrigger(TriggerEvent event) {
            Sensor sensor = event.sensor;
            float[] values = event.values.clone();
            long timestamp = event.timestamp;
            main.post(() -> {
                if (!running) return;
                record(sensor, values, timestamp, -1);
                Reading reading = readings.get(sensor);
                if (reading != null) register(reading);
            });
        }
    };

    private final SensorManager.DynamicSensorCallback dynamicCallback =
            new SensorManager.DynamicSensorCallback() {
                @Override public void onDynamicSensorConnected(Sensor sensor) {
                    if (!running) return;
                    Reading reading = add(sensor);
                    if (!reading.registered) register(reading);
                    selectPrimarySensors();
                }
                @Override public void onDynamicSensorDisconnected(Sensor sensor) {
                    Reading reading = readings.get(sensor);
                    if (reading != null) {
                        reading.registered = false;
                        reading.status = DISCONNECTED;
                    }
                    selectPrimarySensors();
                }
            };

    public SensorMonitor(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.listener = listener;
        calibrationMessage = this.context.getString(R.string.magnetic_calibration_ready);
        refreshInventory();
        selectPrimarySensors();
    }

    public void start() {
        onMain(() -> {
            if (running) return;
            running = true;
            // Prior readings remain clearly historical in the inventory, but cannot gate calibration.
            lastAcceleration = null;
            accelerationChange = Double.NaN;
            refreshInventory();
            selectPrimarySensors();
            if (manager != null) {
                for (Reading reading : new ArrayList<>(readings.values())) register(reading);
                try { manager.registerDynamicSensorCallback(dynamicCallback, main); }
                catch (RuntimeException ignored) { /* Static inventory still works. */ }
            }
            main.removeCallbacks(ticker);
            ticker.run();
        });
    }

    public void stop() {
        onMain(() -> {
            running = false;
            main.removeCallbacks(ticker);
            if (manager != null) {
                manager.unregisterListener(this);
                manager.cancelTriggerSensor(triggerListener, null);
                manager.unregisterDynamicSensorCallback(dynamicCallback);
            }
            for (Reading reading : readings.values()) {
                reading.registered = false;
                reading.status = PAUSED;
            }
            if (calibrating) {
                calibrating = false;
                calibrationWindow.reset();
                calibrationMessage = context.getString(R.string.magnetic_calibration_paused);
            }
            publish();
        });
    }

    public void calibrate() {
        onMain(() -> {
            calibrationWindow.reset();
            baseline = Double.NaN;
            if (!running || magnetometer == null || accelerometer == null) {
                calibrating = false;
                calibrationMessage = context.getString(R.string.magnetic_calibration_needs_sensors);
            } else {
                calibrating = true;
                calibrationMessage = context.getString(R.string.magnetic_calibration_hold);
            }
            publish();
        });
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (running) record(event.sensor, event.values.clone(), event.timestamp, event.accuracy);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Reading reading = readings.get(sensor);
        if (reading != null) reading.accuracy = accuracy;
    }

    private void onMain(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) runnable.run();
        else main.post(runnable);
    }

    private Reading add(Sensor sensor) {
        Reading reading = readings.get(sensor);
        if (reading == null) {
            reading = new Reading(sensor);
            readings.put(sensor, reading);
        }
        return reading;
    }

    private void refreshInventory() {
        if (manager == null) return;
        for (Sensor sensor : manager.getSensorList(Sensor.TYPE_ALL)) add(sensor);
        try {
            for (Sensor sensor : manager.getDynamicSensorList(Sensor.TYPE_ALL)) add(sensor);
        } catch (RuntimeException ignored) { /* Device may not support dynamic discovery. */ }
    }

    private void selectPrimarySensors() {
        magnetometer = select(Sensor.TYPE_MAGNETIC_FIELD);
        accelerometer = select(Sensor.TYPE_ACCELEROMETER);
        gyroscope = select(Sensor.TYPE_GYROSCOPE);
    }

    private Sensor select(int type) {
        if (manager == null) return null;
        Sensor defaultSensor = manager.getDefaultSensor(type);
        Reading defaultReading = readings.get(defaultSensor);
        if (defaultReading != null && defaultReading.status != DISCONNECTED) return defaultSensor;
        for (Reading reading : readings.values()) {
            if (reading.sensor.getType() == type && reading.status != DISCONNECTED) {
                return reading.sensor;
            }
        }
        return null;
    }

    private void register(Reading reading) {
        if (manager == null || !running) return;
        int type = reading.sensor.getType();
        if (Build.VERSION.SDK_INT >= 29
                && (type == Sensor.TYPE_STEP_COUNTER || type == Sensor.TYPE_STEP_DETECTOR)
                && context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            reading.registered = false;
            reading.status = ACTIVITY_PERMISSION;
            return;
        }
        try {
            boolean oneShot = reading.sensor.getReportingMode() == Sensor.REPORTING_MODE_ONE_SHOT;
            reading.registered = oneShot
                    ? manager.requestTriggerSensor(triggerListener, reading.sensor)
                    : manager.registerListener(this, reading.sensor, 60_000, main);
            reading.status = reading.registered ? (oneShot ? ARMED : AWAITING) : REFUSED;
        } catch (SecurityException exception) {
            reading.registered = false;
            reading.status = RESTRICTED;
        } catch (RuntimeException exception) {
            reading.registered = false;
            reading.status = FAILED;
        }
    }

    private void record(Sensor sensor, float[] values, long timestamp, int accuracy) {
        Reading reading = readings.get(sensor);
        if (reading == null) return;
        reading.values = values;
        reading.timestamp = timestamp;
        reading.accuracy = accuracy;
        reading.status = RECEIVED;
        long now = SystemClock.elapsedRealtimeNanos();
        if (sensor == accelerometer && values.length >= 3) {
            double distance = DetectionMath.vectorDistance(values, lastAcceleration);
            // Slow filtering avoids a single noisy sample declaring motion or stillness.
            if (Double.isFinite(distance)) {
                accelerationChange = Double.isFinite(accelerationChange)
                        ? 0.7 * accelerationChange + 0.3 * distance : distance;
            }
            lastAcceleration = values.clone();
            if (calibrating && !stationary(now)) calibrationWindow.reset();
        } else if (sensor == gyroscope && calibrating && !stationary(now)) {
            calibrationWindow.reset();
        }
        if (sensor == magnetometer && calibrating) {
            boolean still = stationary(now);
            boolean usable = accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM;
            boolean complete = calibrationWindow.add(DetectionMath.magnitude(values), timestamp,
                    still && usable);
            if (!usable) calibrationMessage = context.getString(R.string.magnetic_calibration_low_accuracy);
            else if (!still) calibrationMessage = context.getString(R.string.magnetic_calibration_motion);
            else calibrationMessage = context.getString(R.string.magnetic_calibration_progress,
                        Math.round(calibrationWindow.progress() * 100), calibrationWindow.sampleCount());
            if (complete) {
                baseline = calibrationWindow.mean();
                baselineSpread = calibrationWindow.standardDeviation();
                calibrating = false;
                calibrationMessage = context.getString(R.string.magnetic_baseline_set, baseline, baselineSpread);
            }
        }
    }

    private boolean fresh(Sensor sensor, long now) {
        Reading reading = readings.get(sensor);
        return reading != null && reading.registered && reading.values != null
                && now >= reading.timestamp && now - reading.timestamp < 600_000_000L;
    }

    private boolean stationary(long now) {
        if (!running || !fresh(accelerometer, now)) return false;
        if (gyroscope != null && !fresh(gyroscope, now)) return false;
        return DetectionMath.stationary(valueMagnitude(accelerometer), accelerationChange,
                valueMagnitude(gyroscope), gyroscope != null);
    }

    private double valueMagnitude(Sensor sensor) {
        Reading reading = readings.get(sensor);
        return reading == null ? Double.NaN : DetectionMath.magnitude(reading.values);
    }

    private String scalar(int type, int label, int unit) {
        Sensor sensor = select(type);
        String name = context.getString(label);
        if (sensor == null) return context.getString(R.string.sensor_value_unavailable, name);
        Reading reading = readings.get(sensor);
        if (reading.values == null || reading.values.length == 0) {
            return context.getString(R.string.sensor_value_status, name, status(reading.status));
        }
        return context.getString(R.string.sensor_value, name, reading.values[0], context.getString(unit));
    }

    private String orientation() {
        Sensor sensor = select(Sensor.TYPE_ROTATION_VECTOR);
        if (sensor == null) sensor = select(Sensor.TYPE_GAME_ROTATION_VECTOR);
        Reading reading = readings.get(sensor);
        if (reading == null) return context.getString(R.string.sensor_orientation_unavailable);
        if (reading.values == null || reading.values.length < 3) return context.getString(R.string.sensor_orientation_waiting);
        float[] matrix = new float[9];
        float[] angles = new float[3];
        SensorManager.getRotationMatrixFromVector(matrix, reading.values);
        SensorManager.getOrientation(matrix, angles);
        return context.getString(R.string.sensor_orientation, Math.round(Math.toDegrees(angles[0])),
                Math.round(Math.toDegrees(angles[1])), Math.round(Math.toDegrees(angles[2])));
    }

    private void publish() {
        long now = SystemClock.elapsedRealtimeNanos();
        int enabled = 0, live = 0;
        StringBuilder inventory = new StringBuilder();
        for (Reading reading : readings.values()) {
            if (reading.registered) enabled++;
            if (reading.values != null) live++;
            Sensor sensor = reading.sensor;
            inventory.append(sensor.getName()).append("\n")
                    .append(sensor.getStringType()).append(" • ").append(sensor.getVendor())
                    .append(sensor.isWakeUpSensor() ? " • " + context.getString(R.string.sensor_wake_up) : "")
                    .append(" • ").append(reportingMode(sensor)).append("\n")
                    .append(context.getString(R.string.sensor_specs, sensor.getMaximumRange(),
                            sensor.getResolution(), sensor.getPower())).append("\n")
                    .append(status(reading.status));
            if (reading.values != null) {
                inventory.append(" • ").append(context.getString(R.string.sensor_last_reading,
                        accuracyName(reading.accuracy), Math.max(0, now - reading.timestamp) / 1e9)).append("\n")
                        .append(context.getString(R.string.sensor_raw)).append(' ');
                for (int i = 0; i < reading.values.length; i++) {
                    if (i > 0) inventory.append(", ");
                    inventory.append(String.format(java.util.Locale.ROOT, "%.4g", reading.values[i]));
                }
            }
            inventory.append("\n\n");
        }
        if (readings.isEmpty()) inventory.append(context.getString(R.string.sensor_none)).append('\n');
        inventory.append(context.getString(R.string.sensor_absent, absentTypes())).append('\n')
                .append(context.getString(R.string.sensor_inventory_note)).append('\n');

        double magnetic = valueMagnitude(magnetometer);
        double delta = Double.isFinite(baseline) && Double.isFinite(magnetic)
                ? Math.abs(magnetic - baseline) : Double.NaN;
        Reading magneticReading = readings.get(magnetometer);
        String magneticText;
        if (magnetometer == null) magneticText = context.getString(R.string.magnetic_unavailable);
        else if (!Double.isFinite(magnetic)) {
            magneticText = context.getString(R.string.magnetic_status, status(magneticReading.status));
        } else {
            magneticText = context.getString(R.string.magnetic_field, magnetic, accuracyName(magneticReading.accuracy));
            if (Double.isFinite(delta)) {
                magneticText += "\n" + context.getString(R.string.magnetic_change, delta);
                if (delta > Math.max(15, baselineSpread * 5)) magneticText += " • " + context.getString(R.string.magnetic_local_change);
            }
        }
        boolean still = stationary(now);
        String motion = !running ? context.getString(R.string.sensor_paused) : context.getString(still
                ? R.string.motion_still : R.string.motion_moving);
        if (gyroscope == null) motion += " " + context.getString(R.string.motion_no_gyroscope);
        motion += "\n" + orientation();
        String environment = scalar(Sensor.TYPE_LIGHT, R.string.sensor_light, R.string.unit_lux) + "\n"
                + scalar(Sensor.TYPE_PROXIMITY, R.string.sensor_proximity, R.string.unit_cm) + "\n"
                + scalar(Sensor.TYPE_PRESSURE, R.string.sensor_pressure, R.string.unit_hpa) + "\n"
                + context.getString(R.string.sensor_environment_note);
        String summary = context.getString(R.string.sensor_summary, readings.size(), enabled, live)
                + (running ? "" : " • " + context.getString(R.string.sensor_paused_short));
        double rotation = gyroscope != null && fresh(gyroscope, now) ? valueMagnitude(gyroscope) : Double.NaN;
        listener.onUpdate(new Snapshot(summary, inventory.toString(), magneticText, environment,
                motion, calibrationMessage, magnetic, delta, baseline, rotation, still,
                Double.isFinite(baseline), calibrating, running, readings.size(), live));
    }

    private String status(int status) {
        switch (status) {
            case DISCONNECTED: return context.getString(R.string.sensor_status_disconnected);
            case ARMED: return context.getString(R.string.sensor_status_armed);
            case AWAITING: return context.getString(R.string.sensor_status_awaiting);
            case REFUSED: return context.getString(R.string.sensor_status_refused);
            case ACTIVITY_PERMISSION: return context.getString(R.string.sensor_status_activity_permission);
            case RESTRICTED: return context.getString(R.string.sensor_status_restricted);
            case FAILED: return context.getString(R.string.sensor_status_failed);
            case RECEIVED: return context.getString(R.string.sensor_status_received);
            default: return context.getString(R.string.sensor_status_paused);
        }
    }

    private String absentTypes() {
        int[] types = {Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD,
                Sensor.TYPE_LIGHT, Sensor.TYPE_PROXIMITY, Sensor.TYPE_PRESSURE, Sensor.TYPE_GRAVITY,
                Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_STEP_COUNTER,
                Sensor.TYPE_AMBIENT_TEMPERATURE, Sensor.TYPE_RELATIVE_HUMIDITY, Sensor.TYPE_HEART_RATE};
        String[] names = context.getResources().getStringArray(R.array.sensor_common_types);
        StringBuilder missing = new StringBuilder();
        for (int i = 0; i < types.length && i < names.length; i++) {
            if (select(types[i]) == null) {
                if (missing.length() > 0) missing.append(", ");
                missing.append(names[i]);
            }
        }
        return missing.length() == 0 ? context.getString(R.string.sensor_absent_none) : missing.toString();
    }

    private String reportingMode(Sensor sensor) {
        switch (sensor.getReportingMode()) {
            case Sensor.REPORTING_MODE_CONTINUOUS: return context.getString(R.string.sensor_mode_continuous);
            case Sensor.REPORTING_MODE_ON_CHANGE: return context.getString(R.string.sensor_mode_on_change);
            case Sensor.REPORTING_MODE_ONE_SHOT: return context.getString(R.string.sensor_mode_one_shot);
            default: return context.getString(R.string.sensor_mode_special);
        }
    }

    private String accuracyName(int accuracy) {
        switch (accuracy) {
            case SensorManager.SENSOR_STATUS_ACCURACY_HIGH: return context.getString(R.string.accuracy_high);
            case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM: return context.getString(R.string.accuracy_medium);
            case SensorManager.SENSOR_STATUS_ACCURACY_LOW: return context.getString(R.string.accuracy_low);
            case SensorManager.SENSOR_STATUS_UNRELIABLE: return context.getString(R.string.accuracy_unreliable);
            default: return context.getString(R.string.accuracy_unknown);
        }
    }
}
