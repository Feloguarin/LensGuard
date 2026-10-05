package com.feloguarin.lensguard;

import android.Manifest;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.camera.core.MeteringPoint;
import androidx.camera.view.PreviewView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** The lens finder: live highlight markers, steady highlights and the light on/off comparison. */
final class CameraScreen extends Screen implements LensCamera.Listener {
    private static final long FINDINGS_VISIBLE_MS = 12_000;
    private static final long COMPARISON_RECENT_MS = 60_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PreviewView preview;
    private HighlightOverlay overlay;
    private final Runnable clearFindings = () -> { if (overlay != null) overlay.clearFindings(); };
    private TextView placeholder, status, summary, comparison, zoomLabel, exposureLabel;
    private Button cameraButton, switchButton, torchButton, compareButton, photoButton;
    private List<HighlightTracker.Track> tracks = new ArrayList<>();
    private boolean dark;
    private LightComparison.Result lastResult;
    private long lastResultMs;
    private boolean flashHint;

    CameraScreen(MainActivity app) {
        super(app);
    }

    @Override void build(LinearLayout body) {
        tracks = new ArrayList<>();
        flashHint = false;
        body.addView(ui.title(string(R.string.camera_title)));
        body.addView(ui.text(string(R.string.camera_intro), 14, Ui.MUTED));
        spotBanner(body);

        FrameLayout frame = new FrameLayout(app);
        frame.setBackground(ui.shape(Ui.CARD, 20));
        frame.setClipToOutline(true);
        preview = new PreviewView(app);
        preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        preview.setContentDescription(string(R.string.camera_preview_off));
        preview.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                view.performClick();
                if (app.lens.isRunning()) {
                    MeteringPoint point = preview.getMeteringPointFactory().createPoint(event.getX(), event.getY());
                    app.lens.focus(point);
                    app.toast(string(R.string.camera_focusing));
                }
            }
            return true;
        });
        frame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        overlay = new HighlightOverlay(app);
        frame.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        placeholder = ui.text(string(R.string.camera_placeholder_off), 18, Ui.MUTED);
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setBackgroundColor(Ui.CARD);
        frame.addView(placeholder, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(-1, previewHeight());
        frameParams.setMargins(0, ui.dp(12), 0, ui.dp(8));
        body.addView(frame, frameParams);

        status = ui.text(string(R.string.camera_status_off), 12, Ui.MUTED);
        body.addView(status);
        summary = ui.text(string(R.string.camera_summary_off), 14, Ui.TEXT);
        summary.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        summary.setPadding(0, ui.dp(4), 0, 0);
        body.addView(summary);

        // The controls used while sweeping sit directly under the preview.
        cameraButton = ui.primary(string(R.string.camera_start), this::toggleCamera);
        torchButton = ui.button(string(R.string.camera_light_on), () -> app.lens.setTorch(!app.lens.torch()));
        ui.row(body, cameraButton, torchButton);
        compareButton = ui.button(string(R.string.camera_compare), () -> app.lens.compareLight());
        photoButton = ui.button(string(R.string.camera_save_photo), this::savePhoto);
        ui.row(body, compareButton, photoButton);
        comparison = ui.text("", 14, Ui.MINT);
        comparison.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        comparison.setPadding(0, ui.dp(2), 0, ui.dp(4));
        comparison.setVisibility(View.GONE);
        body.addView(comparison);
        body.addView(legend());
        switchButton = ui.button(string(R.string.camera_switch), this::switchCamera);
        ui.row(body, switchButton, ui.button(string(R.string.camera_add_note), app::openNotes));
        body.addView(ui.text(string(R.string.camera_explain), 12, Ui.MUTED));
        buildAdvanced(body);
        if (lastResult != null && SystemClock.elapsedRealtime() - lastResultMs < COMPARISON_RECENT_MS) {
            Ui.set(comparison, describe(lastResult));
            comparison.setVisibility(View.VISIBLE);
        }
        onCameraChanged();
    }

    private int previewHeight() {
        DisplayMetrics metrics = app.getResources().getDisplayMetrics();
        int width = metrics.widthPixels - ui.dp(36);
        // Tall enough to aim at small objects, short enough to keep the sweep controls on screen.
        return Math.max(ui.dp(220), Math.min(width * 4 / 3, Math.round(metrics.heightPixels * 0.40f)));
    }

    private TextView legend() {
        SpannableStringBuilder text = new SpannableStringBuilder();
        legendItem(text, "○", HighlightOverlay.NEW, string(R.string.legend_new));
        legendItem(text, "◎", HighlightOverlay.STEADY, string(R.string.legend_steady));
        legendItem(text, "◉", HighlightOverlay.REFLECTION, string(R.string.legend_reflection));
        legendItem(text, "□", HighlightOverlay.SOURCE, string(R.string.legend_source));
        TextView legend = ui.text(text, 12, Ui.MUTED);
        legend.setPadding(0, ui.dp(2), 0, ui.dp(2));
        return legend;
    }

    private static void legendItem(SpannableStringBuilder text, String symbol, int style, String label) {
        if (text.length() > 0) text.append("   ");
        int start = text.length();
        text.append(symbol);
        text.setSpan(new ForegroundColorSpan(HighlightOverlay.color(style)), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.append(' ').append(label);
    }

    private void buildAdvanced(LinearLayout body) {
        LinearLayout advanced = new LinearLayout(app);
        advanced.setOrientation(LinearLayout.VERTICAL);
        advanced.setVisibility(View.GONE);
        ui.action(body, string(R.string.camera_advanced), () ->
                advanced.setVisibility(advanced.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        body.addView(advanced);
        zoomLabel = ui.text(string(R.string.camera_zoom, 1f), 12, Ui.MUTED);
        advanced.addView(zoomLabel);
        SeekBar zoom = new SeekBar(app);
        zoom.setMax(100);
        zoom.setContentDescription(string(R.string.camera_zoom_description));
        advanced.addView(zoom);
        zoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                float ratio = app.lens.zoom(progress / 100f);
                if (Float.isFinite(ratio)) Ui.set(zoomLabel, string(R.string.camera_zoom, ratio));
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        exposureLabel = ui.text(string(R.string.camera_exposure_neutral), 12, Ui.MUTED);
        advanced.addView(exposureLabel);
        SeekBar exposure = new SeekBar(app);
        exposure.setMax(100);
        exposure.setProgress(50);
        exposure.setContentDescription(string(R.string.camera_exposure_description));
        advanced.addView(exposure);
        exposure.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                if (!app.lens.isRunning()) return;
                Integer value = app.lens.exposure(progress / 100f);
                Ui.set(exposureLabel, value == null ? string(R.string.camera_exposure_unavailable)
                        : string(R.string.camera_exposure, value));
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
    }

    private void toggleCamera() {
        if (app.lens.isRunning() || app.lens.isStarting()) {
            app.lens.stop();
            Ui.set(summary, string(R.string.camera_summary_stopped));
        } else {
            app.permissions(new String[] {Manifest.permission.CAMERA}, this::startCamera);
        }
    }

    private void startCamera() {
        if (preview == null) return;
        if (!app.granted(Manifest.permission.CAMERA)) {
            Ui.set(summary, string(R.string.camera_permission_needed));
            return;
        }
        Ui.set(summary, string(app.lens.isFront() ? R.string.camera_starting_front : R.string.camera_starting_rear));
        app.lens.start(preview);
    }

    private void switchCamera() {
        app.lens.setFront(!app.lens.isFront());
        if (app.lens.isRunning() || app.lens.isStarting()) startCamera();
        else Ui.set(status, string(app.lens.isFront() ? R.string.camera_status_front_selected : R.string.camera_status_rear_selected));
    }

    private void savePhoto() {
        File target = app.store.newPhotoFile();
        if (target == null) {
            app.toast(string(R.string.storage_failed));
            return;
        }
        String summaryText = photoSummary();
        JSONObject data = photoData();
        Ui.enabled(photoButton, false);
        app.lens.capture(target, saved -> {
            Ui.enabled(photoButton, app.lens.isRunning() && !app.lens.isComparing());
            if (!saved) {
                app.toast(string(R.string.camera_photo_failed));
                return;
            }
            app.record(Observation.CAMERA, summaryText, null, target.getName(), data);
            app.toast(string(R.string.camera_photo_saved));
        });
    }

    private String photoSummary() {
        int steady = 0;
        for (HighlightTracker.Track track : tracks) if (track.steady) steady++;
        String camera = string(app.lens.isFront() ? R.string.camera_front : R.string.camera_rear);
        StringBuilder text = new StringBuilder(string(R.string.camera_photo_summary, camera,
                app.getResources().getQuantityString(R.plurals.camera_highlights, tracks.size(), tracks.size())));
        if (steady > 0) text.append(" · ").append(app.getResources().getQuantityString(R.plurals.camera_steady, steady, steady));
        if (lastResult != null && SystemClock.elapsedRealtime() - lastResultMs < COMPARISON_RECENT_MS) {
            text.append(" · ").append(string(R.string.camera_photo_comparison, lastResult.reflections, lastResult.lightSources));
        }
        return text.toString();
    }

    private JSONObject photoData() {
        JSONObject data = new JSONObject();
        try {
            int steady = 0;
            for (HighlightTracker.Track track : tracks) if (track.steady) steady++;
            data.put("camera", app.lens.isFront() ? "front" : "rear");
            data.put("highlights", tracks.size());
            data.put("steadyHighlights", steady);
            data.put("torch", app.lens.torch());
            if (lastResult != null && SystemClock.elapsedRealtime() - lastResultMs < COMPARISON_RECENT_MS) {
                JSONObject result = new JSONObject();
                result.put("reflectionsOfPhoneLight", lastResult.reflections);
                result.put("visibleWithoutPhoneLight", lastResult.lightSources);
                result.put("phoneMoved", lastResult.moved);
                result.put("secondsBeforePhoto", (SystemClock.elapsedRealtime() - lastResultMs) / 1000);
                data.put("lightComparison", result);
            }
        } catch (JSONException ignored) {
            // Every value above is a plain number or string.
        }
        return data;
    }

    @Override public void onCameraChanged() {
        LensCamera lens = app.lens;
        boolean running = lens.isRunning(), starting = lens.isStarting(), comparing = lens.isComparing();
        app.keepScreenOn(running);
        if (cameraButton == null) return;
        cameraButton.setText(string(running || starting ? R.string.camera_stop : R.string.camera_start));
        placeholder.setVisibility(running ? View.GONE : View.VISIBLE);
        Ui.set(placeholder, string(starting ? R.string.camera_placeholder_starting : R.string.camera_placeholder_off));
        boolean flash = running && lens.hasFlash();
        Ui.enabled(torchButton, flash && !comparing);
        torchButton.setText(string(lens.torch() ? R.string.camera_light_off : R.string.camera_light_on));
        Ui.enabled(compareButton, flash && !comparing);
        compareButton.setText(string(comparing ? R.string.camera_comparing : R.string.camera_compare));
        Ui.enabled(photoButton, running && !comparing);
        Ui.enabled(switchButton, !comparing);
        if (!running) {
            overlay.clear();
            tracks = new ArrayList<>();
            Ui.set(status, string(starting ? R.string.camera_status_starting : R.string.camera_status_off));
            preview.setContentDescription(string(R.string.camera_preview_off));
        } else if (lens.frames() == 0) {
            Ui.set(status, string(R.string.camera_status_starting));
        }
        // Explain why the light controls are disabled instead of leaving them silently greyed out.
        if (running && !lens.hasFlash()) {
            flashHint = true;
            comparison.setVisibility(View.VISIBLE);
            comparison.setTextColor(Ui.MUTED);
            Ui.set(comparison, string(R.string.camera_no_flash_hint));
        } else if (flashHint) {
            flashHint = false;
            comparison.setVisibility(View.GONE);
        }
    }

    @Override public void onFrame(List<HighlightTracker.Track> latest, float aspect, int frames, boolean isDark) {
        if (overlay == null) return;
        tracks = latest;
        dark = isDark;
        overlay.showTracks(latest, aspect);
        Ui.set(status, app.getResources().getQuantityString(app.lens.isFront() ? R.plurals.camera_status_live_front
                : R.plurals.camera_status_live_rear, frames, frames));
        String text = frameSummary();
        Ui.set(summary, text);
        preview.setContentDescription(text);
    }

    private String frameSummary() {
        if (dark) return string(R.string.camera_summary_dark);
        if (tracks.isEmpty()) return string(R.string.camera_summary_none);
        int steady = 0;
        for (HighlightTracker.Track track : tracks) if (track.steady) steady++;
        StringBuilder text = new StringBuilder(app.getResources().getQuantityString(R.plurals.camera_highlights,
                tracks.size(), tracks.size()));
        if (steady > 0) text.append(" · ").append(app.getResources().getQuantityString(R.plurals.camera_steady, steady, steady));
        text.append(" · ").append(string(steady > 0 ? R.string.camera_summary_steady_hint : R.string.camera_summary_hint));
        return text.toString();
    }

    @Override public void onComparisonProgress(int step, int steps) {
        if (comparison == null) return;
        flashHint = false;
        handler.removeCallbacks(clearFindings);
        overlay.clearFindings();
        comparison.setVisibility(View.VISIBLE);
        comparison.setTextColor(Ui.MINT);
        boolean lightOn = step < LightComparison.FRAMES_PER_PHASE;
        Ui.set(comparison, string(lightOn ? R.string.compare_progress_on : R.string.compare_progress_off, step, steps));
    }

    @Override public void onComparisonDone(LightComparison.Result result, float aspect) {
        lastResult = result;
        lastResultMs = SystemClock.elapsedRealtime();
        if (comparison == null) return;
        flashHint = false;
        overlay.showFindings(result.findings, aspect);
        handler.removeCallbacks(clearFindings);
        handler.postDelayed(clearFindings, FINDINGS_VISIBLE_MS);
        comparison.setVisibility(View.VISIBLE);
        comparison.setTextColor(result.moved ? Ui.AMBER : Ui.MINT);
        Ui.set(comparison, describe(result));
    }

    private String describe(LightComparison.Result result) {
        if (result.moved) return string(R.string.compare_moved);
        if (result.findings.isEmpty()) return string(R.string.compare_none);
        StringBuilder text = new StringBuilder();
        if (result.reflections > 0) {
            text.append(app.getResources().getQuantityString(R.plurals.compare_reflections, result.reflections, result.reflections));
        }
        if (result.lightSources > 0) {
            if (text.length() > 0) text.append("\n");
            text.append(app.getResources().getQuantityString(R.plurals.compare_sources, result.lightSources, result.lightSources));
        }
        return text.toString();
    }

    @Override public void onMessage(int message) {
        app.toast(string(message));
        if (summary != null && (message == R.string.camera_unavailable || message == R.string.camera_selected_unavailable)) {
            Ui.set(summary, string(message));
        }
    }

    @Override void onSensors(SensorMonitor.Snapshot snapshot) {
        if (status != null && app.lens.stalled()) Ui.set(status, string(R.string.camera_status_waiting));
    }

    @Override void leave() {
        handler.removeCallbacks(clearFindings);
        if (overlay != null) overlay.clear();
        preview = null;
        overlay = null;
        placeholder = status = summary = comparison = zoomLabel = exposureLabel = null;
        cameraButton = switchButton = torchButton = compareButton = photoButton = null;
    }
}
