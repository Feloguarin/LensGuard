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
import java.util.Locale;
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
        public final boolean stationary;
        public final boolean calibrated;
        public final boolean calibrating;
        public final int sensorCount;
        public final int liveSensorCount;

        private Snapshot(String summary, String inventory, String magnetic, String environment,
                         String motion, String calibration, double magneticMicroTesla,
                         double magneticDeltaMicroTesla, boolean stationary, boolean calibrated,
                         boolean calibrating, int sensorCount, int liveSensorCount) {
            this.summary = summary;
            this.inventory = inventory;
            this.magnetic = magnetic;
            this.environment = environment;
            this.motion = motion;
            this.calibration = calibration;
            this.magneticMicroTesla = magneticMicroTesla;
            this.magneticDeltaMicroTesla = magneticDeltaMicroTesla;
            this.stationary = stationary;
            this.calibrated = calibrated;
            this.calibrating = calibrating;
            this.sensorCount = sensorCount;
            this.liveSensorCount = liveSensorCount;
        }
    }

    private static final class Reading {
        final Sensor sensor;
        String status = "Paused";
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
    private String calibrationMessage = "Place the phone away from metal, then calibrate while still.";

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
                        reading.status = "Disconnected";
                    }
                    selectPrimarySensors();
                }
            };

    public SensorMonitor(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.listener = listener;
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
                reading.status = "Paused";
            }
            if (calibrating) {
                calibrating = false;
                calibrationWindow.reset();
                calibrationMessage = "Calibration paused. Start a fresh still-phone calibration.";
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
                calibrationMessage = "Calibration needs active magnetometer and accelerometer readings.";
            } else {
                calibrating = true;
                calibrationMessage = "Hold still for 3 seconds, away from metal and electronics.";
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
        if (defaultReading != null && !"Disconnected".equals(defaultReading.status)) return defaultSensor;
        for (Reading reading : readings.values()) {
            if (reading.sensor.getType() == type && !"Disconnected".equals(reading.status)) {
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
            reading.status = "Activity recognition permission needed";
            return;
        }
        try {
            boolean oneShot = reading.sensor.getReportingMode() == Sensor.REPORTING_MODE_ONE_SHOT;
            reading.registered = oneShot
                    ? manager.requestTriggerSensor(triggerListener, reading.sensor)
                    : manager.registerListener(this, reading.sensor, 60_000, main);
            reading.status = reading.registered
                    ? (oneShot ? "Trigger armed" : "Enabled; awaiting event")
                    : "Unavailable for this app (registration refused)";
        } catch (SecurityException exception) {
            reading.registered = false;
            reading.status = "Permission required or access restricted by Android";
        } catch (RuntimeException exception) {
            reading.registered = false;
            reading.status = "Could not enable this sensor";
        }
    }

    private void record(Sensor sensor, float[] values, long timestamp, int accuracy) {
        Reading reading = readings.get(sensor);
        if (reading == null) return;
        reading.values = values;
        reading.timestamp = timestamp;
        reading.accuracy = accuracy;
        reading.status = "Reading received";
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
            if (!usable) calibrationMessage = "Magnetometer accuracy is low. Move away from metal; gently make a figure eight, then hold still.";
            else if (!still) calibrationMessage = "Motion detected or motion readings pending. Hold the phone still; the 3-second window restarts.";
            else calibrationMessage = String.format(Locale.US, "Still-phone baseline: %.0f%% (%d samples)",
                        calibrationWindow.progress() * 100, calibrationWindow.sampleCount());
            if (complete) {
                baseline = calibrationWindow.mean();
                baselineSpread = calibrationWindow.standardDeviation();
                calibrating = false;
                calibrationMessage = String.format(Locale.US,
                        "Baseline %.1f µT • variation %.2f µT. Recalibrate when the environment changes.",
                        baseline, baselineSpread);
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

    private String scalar(int type, String label, String unit) {
        Sensor sensor = select(type);
        if (sensor == null) return label + ": unavailable";
        Reading reading = readings.get(sensor);
        if (reading.values == null || reading.values.length == 0) return label + ": " + reading.status;
        return String.format(Locale.US, "%s: %.1f %s", label, reading.values[0], unit);
    }

    private String orientation() {
        Sensor sensor = select(Sensor.TYPE_ROTATION_VECTOR);
        if (sensor == null) sensor = select(Sensor.TYPE_GAME_ROTATION_VECTOR);
        Reading reading = readings.get(sensor);
        if (reading == null) return "Orientation unavailable";
        if (reading.values == null || reading.values.length < 3) return "Orientation awaiting reading";
        float[] matrix = new float[9];
        float[] angles = new float[3];
        SensorManager.getRotationMatrixFromVector(matrix, reading.values);
        SensorManager.getOrientation(matrix, angles);
        return String.format(Locale.US, "Yaw %.0f° • pitch %.0f° • roll %.0f°",
                Math.toDegrees(angles[0]), Math.toDegrees(angles[1]), Math.toDegrees(angles[2]));
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
                    .append(sensor.isWakeUpSensor() ? " • wake-up" : "")
                    .append(" • ").append(reportingMode(sensor)).append("\n")
                    .append(String.format(Locale.US, "Range %.3g • resolution %.3g • power %.2f mA\n",
                            sensor.getMaximumRange(), sensor.getResolution(), sensor.getPower()))
                    .append(reading.status);
            if (reading.values != null) {
                inventory.append(" • accuracy ").append(accuracyName(reading.accuracy))
                        .append(String.format(Locale.US, " • last %.1fs ago\n", Math.max(0, now - reading.timestamp) / 1e9))
                        .append("Raw: ");
                for (int i = 0; i < reading.values.length; i++) {
                    if (i > 0) inventory.append(", ");
                    inventory.append(String.format(Locale.US, "%.4g", reading.values[i]));
                }
            }
            inventory.append("\n\n");
        }
        if (readings.isEmpty()) inventory.append("Android did not expose any SensorManager sensors.\n");
        inventory.append("Absent types: ").append(absentTypes())
                .append("\nVendor and virtual sensors are listed as Android exposes them. Raw units follow the Android sensor type. ")
                .append("Microphone, cameras, Wi-Fi and Bluetooth have separate controls.\n");

        double magnetic = valueMagnitude(magnetometer);
        double delta = Double.isFinite(baseline) && Double.isFinite(magnetic)
                ? Math.abs(magnetic - baseline) : Double.NaN;
        Reading magneticReading = readings.get(magnetometer);
        String magneticText;
        if (magnetometer == null) magneticText = "Magnetometer unavailable on this device.";
        else if (!Double.isFinite(magnetic)) magneticText = "Magnetometer: " + magneticReading.status;
        else {
            magneticText = String.format(Locale.US, "Field %.1f µT • accuracy %s", magnetic,
                    accuracyName(magneticReading.accuracy));
            if (Double.isFinite(delta)) {
                magneticText += String.format(Locale.US, "\nChange from baseline %.1f µT", delta);
                if (delta > Math.max(15, baselineSpread * 5)) magneticText += " • local magnetic change";
            }
            magneticText += "\nMagnets, metal and ordinary electronics can cause changes. A magnetometer cannot identify a camera or measure RF.";
        }
        boolean still = stationary(now);
        String motion = !running ? "Sensors paused" : still
                ? "Phone is still. Sweep slowly after calibration."
                : "Move slowly. Hold still to establish a baseline.";
        if (gyroscope == null) motion += " Gyroscope unavailable; motion guidance uses acceleration only.";
        motion += "\n" + orientation();
        String environment = scalar(Sensor.TYPE_LIGHT, "Ambient light", "lux") + "\n"
                + scalar(Sensor.TYPE_PROXIMITY, "Proximity", "cm") + "\n"
                + scalar(Sensor.TYPE_PRESSURE, "Pressure", "hPa")
                + "\nThese provide scanning context; they do not identify cameras.";
        String summary = String.format(Locale.US, "%d exposed sensors • %d enabled • %d with readings%s",
                readings.size(), enabled, live, running ? "" : " • paused");
        listener.onUpdate(new Snapshot(summary, inventory.toString(), magneticText, environment,
                motion, calibrationMessage, magnetic, delta, still, Double.isFinite(baseline),
                calibrating, readings.size(), live));
    }

    private String absentTypes() {
        int[] types = {Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD,
                Sensor.TYPE_LIGHT, Sensor.TYPE_PROXIMITY, Sensor.TYPE_PRESSURE, Sensor.TYPE_GRAVITY,
                Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_STEP_COUNTER,
                Sensor.TYPE_AMBIENT_TEMPERATURE, Sensor.TYPE_RELATIVE_HUMIDITY, Sensor.TYPE_HEART_RATE};
        String[] names = {"accelerometer", "gyroscope", "magnetometer", "light", "proximity", "pressure",
                "gravity", "linear acceleration", "rotation vector", "steps", "ambient temperature",
                "humidity", "heart rate"};
        StringBuilder missing = new StringBuilder();
        for (int i = 0; i < types.length; i++) {
            if (select(types[i]) == null) {
                if (missing.length() > 0) missing.append(", ");
                missing.append(names[i]);
            }
        }
        return missing.length() == 0 ? "none of the common types above" : missing.toString();
    }

    private static String reportingMode(Sensor sensor) {
        switch (sensor.getReportingMode()) {
            case Sensor.REPORTING_MODE_CONTINUOUS: return "continuous";
            case Sensor.REPORTING_MODE_ON_CHANGE: return "on change";
            case Sensor.REPORTING_MODE_ONE_SHOT: return "one shot";
            default: return "special trigger";
        }
    }

    private static String accuracyName(int accuracy) {
        switch (accuracy) {
            case SensorManager.SENSOR_STATUS_ACCURACY_HIGH: return "high";
            case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM: return "medium";
            case SensorManager.SENSOR_STATUS_ACCURACY_LOW: return "low";
            case SensorManager.SENSOR_STATUS_UNRELIABLE: return "unreliable";
            default: return "not reported";
        }
    }
}
