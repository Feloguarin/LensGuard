package com.feloguarin.lensguard;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

/** Optional sensor tools: magnetic comparison, sensor diagnostics and the experimental sound check. */
final class SensorsScreen extends Screen {
    static final int LIST = 0, MAGNETIC = 1, READINGS = 2, SOUND = 3;
    /** About 30 seconds at the sensor monitor's three updates per second. */
    private static final int HISTORY = 90;

    int page = LIST;
    private final float[] history = new float[HISTORY];
    private int historyCount;
    private int historyNext;
    private double peakChange = Double.NaN;
    private double peakBaseline = Double.NaN;
    private SensorMonitor.Snapshot lastSnapshot;
    private TextView magneticView, motionView, calibrationView, environmentView, sensorView, audioView, peakView;
    private SparklineView chart;
    private Button audioButton, audioSave;

    SensorsScreen(MainActivity app) {
        super(app);
    }

    @Override void build(LinearLayout body) {
        if (page != LIST) ui.action(body, string(R.string.sensors_back), () -> app.openSensorPage(LIST));
        if (page == MAGNETIC) buildMagnetic(body);
        else if (page == READINGS) buildReadings(body);
        else if (page == SOUND) buildSound(body);
        else buildList(body);
    }

    private void buildList(LinearLayout body) {
        page = LIST;
        body.addView(ui.title(string(R.string.sensors_title)));
        body.addView(ui.text(string(R.string.sensors_intro), 14, Ui.MUTED));
        LinearLayout magnetic = ui.card(body, string(R.string.sensors_magnetic_title), string(R.string.sensors_magnetic_copy));
        magnetic.addView(ui.button(string(R.string.sensors_open_magnetic), () -> app.openSensorPage(MAGNETIC)));
        LinearLayout readings = ui.card(body, string(R.string.sensors_readings_title), string(R.string.sensors_readings_copy));
        readings.addView(ui.button(string(R.string.sensors_open_readings), () -> app.openSensorPage(READINGS)));
        LinearLayout sound = ui.card(body, string(R.string.sensors_sound_title), string(R.string.sensors_sound_copy));
        sound.addView(ui.button(string(R.string.sensors_open_sound), () -> app.openSensorPage(SOUND)));
    }

    private void buildMagnetic(LinearLayout body) {
        body.addView(ui.title(string(R.string.magnetic_title)));
        spotBanner(body);
        ui.card(body, string(R.string.magnetic_step1_title), string(R.string.magnetic_step1));
        calibrationView = ui.text(string(R.string.magnetic_no_baseline), 14, Ui.MINT);
        calibrationView.setPadding(0, ui.dp(8), 0, 0);
        body.addView(calibrationView);
        ui.full(body, ui.primary(string(R.string.magnetic_set_baseline), () -> {
            resetHistory();
            app.sensors.calibrate();
        }));
        LinearLayout field = ui.card(body, string(R.string.magnetic_step2_title), string(R.string.magnetic_step2));
        magneticView = ui.text(string(R.string.magnetic_waiting), 22, Ui.MINT);
        magneticView.setPadding(0, ui.dp(6), 0, 0);
        field.addView(magneticView);
        chart = new SparklineView(app);
        LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(-1, ui.dp(88));
        chartParams.setMargins(0, ui.dp(8), 0, ui.dp(4));
        field.addView(chart, chartParams);
        peakView = ui.text("", 13, Ui.TEXT);
        field.addView(peakView);
        motionView = ui.text(string(R.string.motion_waiting), 13, Ui.MUTED);
        field.addView(motionView);
        field.addView(ui.text(string(R.string.magnetic_caveat), 12, Ui.MUTED));
        ui.row(body, ui.button(string(R.string.magnetic_reset_peak), () -> {
            peakChange = Double.NaN;
            Ui.set(peakView, "");
        }), ui.button(string(R.string.magnetic_save), this::saveMagnetic));
        ui.card(body, string(R.string.magnetic_where_title), string(R.string.magnetic_where));
    }

    private void buildReadings(LinearLayout body) {
        body.addView(ui.title(string(R.string.readings_title)));
        ui.label(body, string(R.string.readings_device, Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE));
        LinearLayout environment = ui.card(body, string(R.string.readings_room_title), null);
        environmentView = ui.text(string(R.string.readings_waiting), 14, Ui.MINT);
        environment.addView(environmentView);
        motionView = ui.text(string(R.string.motion_waiting), 14, Ui.MUTED);
        environment.addView(motionView);
        ui.card(body, string(R.string.readings_access_title), string(R.string.readings_access));
        if (Build.VERSION.SDK_INT >= 29) {
            ui.action(body, string(R.string.readings_activity), () -> app.permissions(
                    new String[] {Manifest.permission.ACTIVITY_RECOGNITION}, () -> {
                        app.sensors.stop();
                        app.sensors.start();
                    }));
        }
        LinearLayout list = ui.card(body, string(R.string.readings_inventory_title), null);
        sensorView = ui.text(string(R.string.readings_loading), 12, Ui.TEXT);
        sensorView.setTextIsSelectable(true);
        list.addView(sensorView);
    }

    private void buildSound(LinearLayout body) {
        body.addView(ui.title(string(R.string.sound_title)));
        ui.card(body, string(R.string.sound_what_title), string(R.string.sound_what));
        audioView = ui.text(app.soundReading != null ? app.soundReading : string(R.string.sound_off), 14, Ui.MINT);
        audioView.setPadding(0, ui.dp(8), 0, 0);
        body.addView(audioView);
        audioButton = ui.button(string(app.listening ? R.string.sound_stop : R.string.sound_start), this::toggleAudio);
        audioSave = ui.button(string(R.string.sound_save), this::saveSound);
        ui.row(body, audioButton, audioSave);
        Ui.enabled(audioSave, app.listening);
        body.addView(ui.text(string(R.string.sound_privacy), 12, Ui.MUTED));
    }

    private void toggleAudio() {
        if (app.listening) {
            app.stopAudio();
            audioButton.setText(string(R.string.sound_start));
            return;
        }
        app.permissions(new String[] {Manifest.permission.RECORD_AUDIO}, () -> {
            if (!app.granted(Manifest.permission.RECORD_AUDIO)) {
                app.soundReading = string(R.string.sound_denied_retry);
                Ui.set(audioView, app.soundReading);
                return;
            }
            app.listening = true;
            app.audio.start();
            if (audioButton != null) audioButton.setText(string(R.string.sound_stop));
            Ui.enabled(audioSave, true);
        });
    }

    @Override void onAudio(String message) {
        Ui.set(audioView, message);
        if (audioButton != null) audioButton.setText(string(app.listening ? R.string.sound_stop : R.string.sound_start));
        Ui.enabled(audioSave, app.listening);
    }

    private void saveSound() {
        if (app.soundReading == null) return;
        app.record(Observation.SOUND, app.soundReading, null, null, null);
        app.toast(string(R.string.observation_saved));
    }

    @Override void onSensors(SensorMonitor.Snapshot snapshot) {
        boolean fresh = snapshot != lastSnapshot;
        lastSnapshot = snapshot;
        Ui.set(calibrationView, snapshot.calibration);
        if (motionView != null) Ui.set(motionView, snapshot.motion);
        Ui.set(environmentView, snapshot.environment);
        Ui.set(sensorView, snapshot.inventory);
        if (magneticView == null) return;
        if (fresh && snapshot.running && Double.isFinite(snapshot.magneticMicroTesla)) {
            history[historyNext] = (float) snapshot.magneticMicroTesla;
            historyNext = (historyNext + 1) % HISTORY;
            historyCount = Math.min(HISTORY, historyCount + 1);
        }
        if (snapshot.calibrated) {
            if (Double.compare(snapshot.baselineMicroTesla, peakBaseline) != 0) {
                peakBaseline = snapshot.baselineMicroTesla;
                peakChange = Double.NaN;
            }
            if (Double.isFinite(snapshot.magneticDeltaMicroTesla)
                    && (!Double.isFinite(peakChange) || snapshot.magneticDeltaMicroTesla > peakChange)) {
                peakChange = snapshot.magneticDeltaMicroTesla;
            }
        }
        String reading = !Double.isFinite(snapshot.magneticMicroTesla) ? snapshot.magnetic
                : string(R.string.magnetic_reading, snapshot.magneticMicroTesla);
        if (snapshot.calibrated && Double.isFinite(snapshot.magneticDeltaMicroTesla)) {
            reading += "\n" + string(R.string.magnetic_reading_change, snapshot.magneticDeltaMicroTesla);
        }
        Ui.set(magneticView, reading);
        Ui.set(peakView, Double.isFinite(peakChange) ? string(R.string.magnetic_peak, peakChange) : "");
        float[] ordered = new float[historyCount];
        for (int i = 0; i < historyCount; i++) ordered[i] = history[(historyNext - historyCount + i + HISTORY) % HISTORY];
        chart.setValues(ordered, snapshot.calibrated ? (float) snapshot.baselineMicroTesla : Float.NaN,
                Double.isFinite(snapshot.magneticMicroTesla)
                        ? string(R.string.magnetic_chart_description, snapshot.magneticMicroTesla) : snapshot.magnetic);
    }

    private void resetHistory() {
        historyCount = 0;
        historyNext = 0;
        peakChange = Double.NaN;
        Ui.set(peakView, "");
    }

    private void saveMagnetic() {
        SensorMonitor.Snapshot snapshot = lastSnapshot;
        if (snapshot == null || !Double.isFinite(snapshot.magneticMicroTesla)) {
            app.toast(string(R.string.magnetic_nothing_to_save));
            return;
        }
        JSONObject data = new JSONObject();
        StringBuilder text = new StringBuilder(string(R.string.magnetic_reading, snapshot.magneticMicroTesla));
        try {
            data.put("fieldMicroTesla", round(snapshot.magneticMicroTesla));
            if (snapshot.calibrated) {
                data.put("baselineMicroTesla", round(snapshot.baselineMicroTesla));
                data.put("changeMicroTesla", round(snapshot.magneticDeltaMicroTesla));
                text.append(" · ").append(string(R.string.magnetic_reading_change, snapshot.magneticDeltaMicroTesla));
            }
            if (Double.isFinite(peakChange)) {
                data.put("peakChangeMicroTesla", round(peakChange));
                text.append(" · ").append(string(R.string.magnetic_peak, peakChange));
            }
        } catch (JSONException ignored) {
            // Finite numbers only.
        }
        app.record(Observation.MAGNETIC, text.toString(), null, null, data);
        app.toast(string(R.string.observation_saved));
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    @Override void leave() {
        magneticView = motionView = calibrationView = environmentView = sensorView = audioView = peakView = null;
        chart = null;
        audioButton = audioSave = null;
    }

    @Override void save(Bundle state) {
        state.putInt("sensors_page", page);
    }

    @Override void restore(Bundle state) {
        page = state.getInt("sensors_page", LIST);
    }
}
