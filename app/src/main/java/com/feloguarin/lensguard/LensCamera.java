package com.feloguarin.lensguard;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Size;
import androidx.activity.ComponentActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExposureState;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.core.ZoomState;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Runs the camera for the lens finder: preview, small-highlight analysis, steady-highlight tracking,
 * the light on/off comparison and private photo capture. Main-thread only, except frame analysis.
 */
final class LensCamera {
    interface Listener {
        /** Running, starting, torch or comparison state changed. */
        void onCameraChanged();
        void onFrame(List<HighlightTracker.Track> tracks, float aspect, int frames, boolean dark);
        void onComparisonProgress(int step, int steps);
        void onComparisonDone(LightComparison.Result result, float aspect);
        void onMessage(int message);
    }

    interface CaptureCallback { void onResult(boolean saved); }

    static final long ANALYSIS_INTERVAL_MS = 250;
    static final long COMPARISON_INTERVAL_MS = 150;
    static final long STALL_MS = 3_500;
    /** Deliberate phone movement; hand tremor stays well below this rotation rate. */
    static final double MOVING_RAD_PER_S = 0.35;

    private final ComponentActivity activity;
    private final Listener listener;
    private final BooleanSupplier moving;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HighlightTracker tracker = new HighlightTracker();
    private ProcessCameraProvider provider;
    private Camera camera;
    private ImageCapture capture;
    private ComparisonRun comparison;
    private int generation;
    private int frames;
    private long lastFrameMs;
    private float aspect = 0.75f;
    private boolean front;
    private boolean torch;
    private boolean starting;
    private volatile long lastAnalysisMs;
    private volatile boolean fastAnalysis;
    private byte[] luminance; // Analysis thread only.

    LensCamera(ComponentActivity activity, Listener listener, BooleanSupplier moving) {
        this.activity = activity;
        this.listener = listener;
        this.moving = moving;
    }

    boolean isRunning() { return camera != null; }
    boolean isStarting() { return starting; }
    boolean isFront() { return front; }
    boolean torch() { return torch; }
    boolean isComparing() { return comparison != null; }
    int frames() { return frames; }
    boolean hasFlash() { return camera != null && camera.getCameraInfo().hasFlashUnit(); }

    /** True when the camera is bound but frames stopped arriving. */
    boolean stalled() {
        return camera != null && SystemClock.elapsedRealtime() - lastFrameMs > STALL_MS;
    }

    void setFront(boolean value) { front = value; }

    void start(PreviewView view) {
        stop();
        starting = true;
        final int current = generation;
        listener.onCameraChanged();
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(activity);
        future.addListener(() -> {
            if (current != generation) return;
            try {
                provider = future.get();
                CameraSelector selector = front ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
                if (!provider.hasCamera(selector)) {
                    starting = false;
                    listener.onMessage(R.string.camera_selected_unavailable);
                    listener.onCameraChanged();
                    return;
                }
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(view.getSurfaceProvider());
                capture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
                // More pixels than 1.x so a distant pinpoint still saturates at least one pixel.
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setResolutionSelector(new ResolutionSelector.Builder()
                                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                                .setResolutionStrategy(new ResolutionStrategy(new Size(1280, 960),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                                .build())
                        .build();
                final boolean mirrored = front;
                analysis.setAnalyzer(executor, image -> analyze(image, current, mirrored));
                // A shared viewport crops analysis to exactly what the preview shows, so markers line up.
                ViewPort port = view.getViewPort();
                if (port != null) {
                    UseCaseGroup group = new UseCaseGroup.Builder().setViewPort(port).addUseCase(preview)
                            .addUseCase(capture).addUseCase(analysis).build();
                    camera = provider.bindToLifecycle(activity, selector, group);
                } else {
                    camera = provider.bindToLifecycle(activity, selector, preview, capture, analysis);
                }
                starting = false;
                frames = 0;
                lastFrameMs = SystemClock.elapsedRealtime();
                tracker.reset();
                listener.onCameraChanged();
            } catch (Exception unavailable) {
                stop();
                listener.onMessage(R.string.camera_unavailable);
            }
        }, ContextCompat.getMainExecutor(activity));
    }

    void stop() {
        generation++;
        endComparison();
        if (camera != null && torch) camera.getCameraControl().enableTorch(false);
        torch = false;
        starting = false;
        if (provider != null) provider.unbindAll();
        camera = null;
        capture = null;
        frames = 0;
        tracker.reset();
        listener.onCameraChanged();
    }

    void release() {
        stop();
        executor.shutdownNow();
    }

    void setTorch(boolean on) {
        if (camera == null || comparison != null) return;
        if (!hasFlash()) {
            listener.onMessage(R.string.camera_no_flash);
            return;
        }
        applyTorch(on);
        listener.onCameraChanged();
    }

    void focus(MeteringPoint point) {
        if (camera == null) return;
        camera.getCameraControl().startFocusAndMetering(new FocusMeteringAction.Builder(point)
                .setAutoCancelDuration(5, TimeUnit.SECONDS).build());
    }

    /** Sets zoom from 0–1 across 1× to at most 4×. Returns the ratio, or NaN without a camera. */
    float zoom(float fraction) {
        if (camera == null) return Float.NaN;
        ZoomState state = camera.getCameraInfo().getZoomState().getValue();
        if (state == null) return Float.NaN;
        float maximum = Math.min(4, state.getMaxZoomRatio());
        float ratio = 1 + (maximum - 1) * fraction;
        camera.getCameraControl().setZoomRatio(ratio);
        return ratio;
    }

    /** Sets exposure from 0–1, where 0.5 is neutral. Returns the index, or null when unsupported. */
    Integer exposure(float fraction) {
        if (camera == null) return null;
        ExposureState state = camera.getCameraInfo().getExposureState();
        if (!state.isExposureCompensationSupported()) return null;
        int low = state.getExposureCompensationRange().getLower(), high = state.getExposureCompensationRange().getUpper();
        int value = fraction < 0.5f ? Math.round(low * (0.5f - fraction) * 2) : Math.round(high * (fraction - 0.5f) * 2);
        camera.getCameraControl().setExposureCompensationIndex(value);
        return value;
    }

    void capture(File target, CaptureCallback callback) {
        if (capture == null || target == null) {
            callback.onResult(false);
            return;
        }
        capture.takePicture(new ImageCapture.OutputFileOptions.Builder(target).build(),
                ContextCompat.getMainExecutor(activity), new ImageCapture.OnImageSavedCallback() {
                    @Override public void onImageSaved(ImageCapture.OutputFileResults result) { callback.onResult(true); }
                    @Override public void onError(ImageCaptureException error) { callback.onResult(false); }
                });
    }

    /**
     * Collects highlights with the light on, then off, from the same position. Returns false when the
     * comparison cannot start.
     */
    boolean compareLight() {
        if (camera == null) {
            listener.onMessage(R.string.camera_start_first);
            return false;
        }
        if (!hasFlash()) {
            listener.onMessage(R.string.compare_needs_flash);
            return false;
        }
        if (comparison != null) return false;
        comparison = new ComparisonRun(main, this::applyTorch, moving, new ComparisonRun.Listener() {
            @Override public void onProgress(int step, int steps) { listener.onComparisonProgress(step, steps); }

            @Override public void onDone(LightComparison.Result result) {
                comparison = null;
                fastAnalysis = false;
                listener.onComparisonDone(result, aspect);
                listener.onCameraChanged();
            }

            @Override public void onTimeout() {
                comparison = null;
                fastAnalysis = false;
                listener.onMessage(R.string.compare_timeout);
                listener.onCameraChanged();
            }
        }, torch, aspect);
        fastAnalysis = true;
        comparison.start();
        listener.onCameraChanged();
        return true;
    }

    private void endComparison() {
        ComparisonRun ending = comparison;
        comparison = null;
        fastAnalysis = false;
        if (ending != null) ending.cancel();
    }

    private void applyTorch(boolean on) {
        torch = on;
        if (camera != null) camera.getCameraControl().enableTorch(on);
    }

    private void analyze(ImageProxy image, int current, boolean mirrored) {
        try {
            long now = SystemClock.elapsedRealtime();
            if (now - lastAnalysisMs < (fastAnalysis ? COMPARISON_INTERVAL_MS : ANALYSIS_INTERVAL_MS)) return;
            lastAnalysisMs = now;
            ImageProxy.PlaneProxy plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            buffer.rewind();
            int size = buffer.remaining();
            if (luminance == null || luminance.length < size) luminance = new byte[size];
            buffer.get(luminance, 0, size);
            Rect crop = image.getCropRect();
            int rotation = image.getImageInfo().getRotationDegrees();
            int step = Math.max(1, Math.max(crop.width(), crop.height()) / 160);
            GlintDetector.Grid grid = GlintDetector.reduce(luminance, plane.getRowStride(), plane.getPixelStride(),
                    crop.left, crop.top, crop.width(), crop.height(), step);
            if (grid == null) return;
            List<float[]> points = new ArrayList<>();
            for (GlintDetector.Blob blob : GlintDetector.find(grid)) {
                points.add(FrameGeometry.upright((blob.x + 0.5f) / grid.width, (blob.y + 0.5f) / grid.height,
                        rotation, mirrored));
            }
            float frameAspect = FrameGeometry.uprightAspect(crop.width(), crop.height(), rotation);
            boolean dark = grid.averageLuminance < 12;
            main.post(() -> deliver(current, points, frameAspect, dark));
        } catch (RuntimeException ignored) {
            // A dropped frame must not interrupt preview.
        } finally {
            image.close();
        }
    }

    private void deliver(int current, List<float[]> points, float frameAspect, boolean dark) {
        if (current != generation || camera == null) return;
        frames++;
        lastFrameMs = SystemClock.elapsedRealtime();
        aspect = frameAspect;
        List<HighlightTracker.Track> tracks = tracker.update(points, frameAspect);
        if (comparison != null) comparison.offer(points);
        listener.onFrame(tracks, frameAspect, frames, dark);
    }
}
