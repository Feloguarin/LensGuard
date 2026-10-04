package com.feloguarin.lensguard;

import android.Manifest;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.google.common.util.concurrent.ListenableFuture;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends ComponentActivity {
    private static final int BG = Color.rgb(13, 20, 23), CARD = Color.rgb(24, 35, 39);
    private static final int MINT = Color.rgb(183, 247, 121), TEXT = Color.rgb(239, 245, 239);
    private static final int MUTED = Color.rgb(158, 177, 181), AMBER = Color.rgb(255, 198, 116);
    private LinearLayout root, pages, nav;
    private TextView magneticView, motionView, opticalView, radioView, sensorView, environmentView, audioView, calibrationView;
    private EditText noteInput;
    private PreviewView previewView;
    private SensorMonitor sensors;
    private WirelessProbe wireless;
    private AudioProbe audio;
    private SensorMonitor.Snapshot sensorSnapshot;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private ImageCapture capture;
    private ExecutorService imageExecutor;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private Runnable permissionAction;
    private int permissionTab;
    private boolean permissionResolved;
    private int tab = 0, tool = 0, cameraGeneration = 0, analyzedFrames = 0;
    private boolean resumed, front, torch, listening;
    private String optical = "Start the camera to inspect reflections.", radio = "No radio scan yet.", sound = "Microphone off.";
    private String note = "";
    private long lastAnalysis, lastFrameAt;
    private File lastPhoto;
    private Button torchButton, audioButton, cameraButton, photoButton, scanButton, scanStopButton;
    private TextView cameraPlaceholder, cameraStatus, scanStatus;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) { tab = state.getInt("tab"); tool = state.getInt("tool"); front = state.getBoolean("front"); note = state.getString("note", ""); }
        else note = getPreferences(MODE_PRIVATE).getString("note", "");
        imageExecutor = Executors.newSingleThreadExecutor();
        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            permissionResolved = true;
            runPermissionActionIfReady();
        });
        sensors = new SensorMonitor(this, snapshot -> {
            sensorSnapshot = snapshot;
            if (camera != null && SystemClock.elapsedRealtime() - lastFrameAt > 3500) set(cameraStatus, "WAITING · No new camera frames. Try restarting camera.");
            updateMagnetic(snapshot); set(motionView, snapshot.motion);
            set(sensorView, snapshot.inventory); set(environmentView, snapshot.environment);
            set(calibrationView, snapshot.calibration);
        });
        wireless = new WirelessProbe(this, result -> { radio = result; set(radioView, result); updateSurvey(); });
        audio = new AudioProbe(this, result -> {
            sound = result; set(audioView, result); listening = audio.isRunning();
            if (audioButton != null) audioButton.setText(listening ? "Stop microphone" : "Enable microphone");
        });
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        root.setPadding(dp(18), 0, dp(18), 0);
        if (Build.VERSION.SDK_INT >= 30) root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(dp(18) + bars.left, bars.top, dp(18) + bars.right, bars.bottom);
            return insets;
        });
        else root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(dp(18) + insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    dp(18) + insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(0, dp(14), 0, dp(14));
        TextView logo = text("◉", 28, MINT); header.addView(logo);
        LinearLayout titles = new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL); titles.setPadding(dp(10), 0, 0, 0);
        titles.addView(text("LensGuard", 23, TEXT)); titles.addView(text("ROOM INSPECTION · ON DEVICE", 10, MUTED));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(header);
        TextView caution = text("Clues, not certainty. A clean scan cannot rule out cameras.", 12, AMBER);
        caution.setPadding(dp(12), dp(10), dp(12), dp(10)); caution.setBackground(shape(Color.rgb(43, 36, 27), 12)); root.addView(caution);
        pages = new LinearLayout(this); pages.setOrientation(LinearLayout.VERTICAL);
        root.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        nav = new LinearLayout(this); nav.setPadding(0, dp(8), 0, dp(8)); root.addView(nav);
        setContentView(root); root.requestApplyInsets(); showTab(tab);
    }

    private void showTab(int index) {
        rememberNote(); tab = index; stopCamera(); wireless.stop(); stopAudio();
        pages.removeAllViews(); magneticView = motionView = opticalView = radioView = sensorView = environmentView = audioView = calibrationView = null;
        noteInput = null; previewView = null; torchButton = null; audioButton = null;
        cameraButton = photoButton = scanButton = scanStopButton = null; cameraPlaceholder = cameraStatus = scanStatus = null;
        nav.removeAllViews();
        String[] names = {"Start", "Camera", "Nearby", "Tools"};
        for (int i = 0; i < names.length; i++) {
            final int n = i;
            Button b = button(names[i], () -> { if (n == 3) tool = 0; showTab(n); }); b.setTextColor(i == index ? BG : MUTED); b.setBackground(shape(i == index ? MINT : CARD, 12));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1); p.setMargins(dp(2), 0, dp(2), 0); nav.addView(b, p);
        }
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false); scroll.setClipToPadding(false); scroll.setPadding(0, dp(16), 0, dp(12));
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); scroll.addView(body);
        pages.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        if (index == 0) buildStart(body);
        else if (index == 1) buildSweep(body);
        else if (index == 2) buildSignals(body);
        else buildTools(body);
        if (sensorSnapshot != null) {
            updateMagnetic(sensorSnapshot); set(motionView, sensorSnapshot.motion);
            set(sensorView, sensorSnapshot.inventory); set(environmentView, sensorSnapshot.environment); set(calibrationView, sensorSnapshot.calibration);
        }
    }

    private void buildStart(LinearLayout body) {
        body.addView(text("Inspect your space.", 30, TEXT));
        label(body, "START HERE / ABOUT 5 MINUTES");
        body.addView(text("Begin with what you can see. Each tool helps you collect clues to check yourself.", 15, MUTED));
        LinearLayout visual = card(body, "1. Look for a lens", "Dim the room. Move slowly around objects facing beds, bathrooms or changing areas.");
        visual.addView(primary("Open camera", () -> showTab(1)));
        LinearLayout nearby = card(body, "2. Check nearby signals", "Look for your known Wi-Fi network and advertised devices. Unfamiliar names alone are not evidence of a camera.");
        nearby.addView(button("Open nearby scan", () -> showTab(2)));
        LinearLayout notes = card(body, "3. Save what you noticed", "Keep a photo or a note about a specific object. Compare it from several angles.");
        notes.addView(button("Open notes", () -> openTool(3)));
        body.addView(text("Nothing here can confirm a hidden camera or prove a room is clear. Start with a visible webcam to learn how reflections behave.", 13, MUTED));
    }

    private void buildSweep(LinearLayout body) {
        body.addView(text("Look for a lens.", 29, TEXT));
        body.addView(text("Dim the room and turn the light on. Tap the lens area to focus, then change angles slowly.", 14, MUTED));
        FrameLayout frame = new FrameLayout(this); frame.setBackground(shape(CARD, 20)); frame.setClipToOutline(true);
        previewView = new PreviewView(this); previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        previewView.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                view.performClick();
                if (camera != null) {
                    MeteringPoint point = previewView.getMeteringPointFactory().createPoint(event.getX(), event.getY());
                    FocusMeteringAction focus = new FocusMeteringAction.Builder(point).setAutoCancelDuration(5, TimeUnit.SECONDS).build();
                    camera.getCameraControl().startFocusAndMetering(focus);
                    toast("Focusing here. Hold steady and change angles slowly.");
                }
            }
            return true;
        });
        frame.addView(previewView, new FrameLayout.LayoutParams(-1, -1));
        cameraPlaceholder = text("Camera is off\nTap Start camera below", 18, MUTED); cameraPlaceholder.setGravity(Gravity.CENTER);
        cameraPlaceholder.setBackgroundColor(CARD); frame.addView(cameraPlaceholder, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, dp(225)); fp.setMargins(0, dp(12), 0, dp(8)); body.addView(frame, fp);
        cameraStatus = text("OFF · No camera frames being analyzed", 12, MUTED); body.addView(cameraStatus);
        opticalView = text("Start the camera to inspect reflections.", 13, TEXT); body.addView(opticalView);
        cameraButton = primary("Start camera", () -> {
            if (camera != null) stopCamera();
            else permissions(new String[]{Manifest.permission.CAMERA}, this::startCamera);
        });
        row(body, cameraButton, button("Switch camera", () -> { front = !front; if (camera != null) startCamera(); else set(cameraStatus, "OFF · " + (front ? "Front" : "Rear") + " camera selected"); }));
        torchButton = button("Turn light on", this::toggleTorch); torchButton.setEnabled(false); torchButton.setAlpha(.45f);
        photoButton = button("Save photo", this::savePhoto); photoButton.setEnabled(false); photoButton.setAlpha(.45f);
        row(body, torchButton, photoButton);
        body.addView(text("A bright point may be glass, a screw or an LED. LensGuard counts small highlights; it does not recognize cameras.", 12, MUTED));
        body.addView(button("Add an observation", () -> openTool(3)));
        LinearLayout advanced = new LinearLayout(this); advanced.setOrientation(LinearLayout.VERTICAL); advanced.setVisibility(View.GONE);
        body.addView(button("Zoom & exposure options", () -> advanced.setVisibility(advanced.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE)));
        body.addView(advanced);
        TextView zoomLabel = text("Zoom · 1×", 12, MUTED); advanced.addView(zoomLabel);
        SeekBar zoom = new SeekBar(this); zoom.setMax(100); advanced.addView(zoom);
        zoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean user) {
                if (camera != null && camera.getCameraInfo().getZoomState().getValue() != null) {
                    float maximum = Math.min(4, camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio());
                    float ratio = 1 + (maximum - 1) * p / 100f;
                    camera.getCameraControl().setZoomRatio(ratio); zoomLabel.setText(String.format(Locale.US, "Zoom · %.1f×", ratio));
                }
            }
            public void onStartTrackingTouch(SeekBar b) {} public void onStopTrackingTouch(SeekBar b) {}
        });
        TextView exposureLabel = text("Exposure · neutral", 12, MUTED); advanced.addView(exposureLabel);
        SeekBar exposure = new SeekBar(this); exposure.setMax(100); exposure.setProgress(50); advanced.addView(exposure);
        exposure.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean user) {
                if (camera == null) return;
                androidx.camera.core.ExposureState state = camera.getCameraInfo().getExposureState();
                if (!state.isExposureCompensationSupported()) { exposureLabel.setText("Exposure adjustment unavailable"); return; }
                int low = state.getExposureCompensationRange().getLower(), high = state.getExposureCompensationRange().getUpper();
                int value = p < 50 ? Math.round(low * (50 - p) / 50f) : Math.round(high * (p - 50) / 50f);
                camera.getCameraControl().setExposureCompensationIndex(value); exposureLabel.setText("Exposure · " + value);
            }
            public void onStartTrackingTouch(SeekBar b) {} public void onStopTrackingTouch(SeekBar b) {}
        });
    }

    private void buildSignals(LinearLayout body) {
        body.addView(text("Check nearby signals.", 28, TEXT));
        body.addView(text("Turn on Wi-Fi, Bluetooth and Android Location. Connect to the room's Wi-Fi for local services. Allow precise Location when asked.", 14, MUTED));
        scanStatus = text(wireless.summary(), 15, MINT); body.addView(scanStatus);
        scanButton = primary("Start 15-second scan", () -> permissions(radioPermissions(), wireless::start));
        scanStopButton = button("Stop scan", wireless::stop);
        row(body, scanButton, scanStopButton);
        body.addView(text("Compare with devices you know. These are advertisements, not a list of cameras. Offline cameras may emit nothing.", 13, MUTED));
        radioView = text(radio, 13, TEXT); radioView.setTextIsSelectable(true);
        LinearLayout results = card(body, "WHAT THE SCAN OBSERVED", null); results.addView(radioView);
        body.addView(button("Add an observation", () -> openTool(3)));
        updateSurvey();
    }

    private void updateSurvey() {
        if (wireless == null) return;
        set(scanStatus, wireless.summary());
        enabled(scanStopButton, wireless.isRunning());
        if (scanButton != null) { scanButton.setEnabled(!wireless.isRunning()); scanButton.setAlpha(wireless.isRunning() ? .45f : 1f); }
    }

    private void openTool(int choice) { tool = choice; showTab(3); }

    private void buildTools(LinearLayout body) {
        if (tool != 0) body.addView(button("All tools", () -> openTool(0)));
        if (tool == 1) { buildMagnetic(body); return; }
        if (tool == 2) { buildSensors(body); return; }
        if (tool == 3) { buildNotes(body); return; }
        if (tool == 4) { buildAudio(body); return; }
        body.addView(text("Optional tools.", 29, TEXT));
        LinearLayout notes = card(body, "Notes & saved photos", "Write an observation, share a report or delete saved evidence.");
        notes.addView(button("Open notes", () -> openTool(3)));
        LinearLayout magnetic = card(body, "Magnetic comparison", "Compare the field near an object. Metal and magnets also change the reading; this cannot identify a camera.");
        magnetic.addView(button("Open magnetic check", () -> openTool(1)));
        LinearLayout inventory = card(body, "Sensor diagnostics", "Check which hardware streams are available and whether readings change.");
        inventory.addView(button("Open sensor readings", () -> openTool(2)));
        LinearLayout acoustic = card(body, "Experimental sound check", "Observe high-frequency tones. There is no universal camera sound signature.");
        acoustic.addView(button("Open sound check", () -> openTool(4)));
    }

    private void buildMagnetic(LinearLayout body) {
        body.addView(text("Compare magnetic field.", 25, TEXT));
        card(body, "1. Set a reference", "Move away from metal, chargers and magnets. Tap Set baseline and hold still for at least 3 seconds. If accuracy is low, move the phone in a figure-eight, then retry.");
        calibrationView = text("No baseline yet.", 14, MINT); body.addView(calibrationView);
        body.addView(primary("Set baseline", sensors::calibrate));
        LinearLayout field = card(body, "2. Compare near an object", "Move close to the object, then away. Repeat. A change tells you the magnetic field changed, not what caused it.");
        magneticView = text("Waiting for magnetometer…", 22, MINT); field.addView(magneticView);
        motionView = text("Waiting for motion readings…", 13, MUTED); field.addView(motionView);
        field.addView(text("Magnetic field is not radio signal strength. Ordinary metal and electronics can produce large changes.", 12, MUTED));
        body.addView(button("Add an observation", () -> openTool(3)));
    }

    private void updateMagnetic(SensorMonitor.Snapshot snapshot) {
        String reading = !Double.isFinite(snapshot.magneticMicroTesla) ? snapshot.magnetic : String.format(Locale.US, "%.1f µT", snapshot.magneticMicroTesla);
        if (snapshot.calibrated) reading += String.format(Locale.US, "\nChange from baseline: %.1f µT", snapshot.magneticDeltaMicroTesla);
        set(magneticView, reading);
    }

    private void buildAudio(LinearLayout body) {
        body.addView(text("Experimental sound check.", 24, TEXT));
        card(body, "What this measures", "High-frequency tones from 15–22 kHz. Chargers, lights, insects and other devices can create tones. These readings cannot identify a camera.");
        audioView = text(sound, 14, MINT); body.addView(audioView);
        audioButton = button("Enable microphone", () -> {
            if (listening) stopAudio();
            else permissions(new String[]{Manifest.permission.RECORD_AUDIO}, () -> {
                if (!granted(Manifest.permission.RECORD_AUDIO)) { sound = "Microphone permission denied. Tap Enable microphone to retry."; set(audioView, sound); return; }
                listening = true; audio.start(); audioButton.setText("Stop microphone");
            });
        });
        body.addView(audioButton); body.addView(text("Audio is analyzed in memory. No recording is saved.", 12, MUTED));
    }

    private void buildSensors(LinearLayout body) {
        body.addView(text("Know your hardware.", 28, TEXT)); label(body, Build.MANUFACTURER + " " + Build.MODEL + " / ANDROID " + Build.VERSION.RELEASE);
        LinearLayout environment = card(body, "ROOM & MOVEMENT", null);
        environmentView = text("Waiting for sensor readings…", 14, MINT); environment.addView(environmentView);
        motionView = text("Waiting for motion readings…", 14, MUTED); environment.addView(motionView);
        card(body, "Public sensor access", "Every sensor exposed by Android is listed below, including unavailable or restricted streams. Light, proximity and motion help guide your sweep. Barometer and activity data provide context only. Fingerprint hardware and camera-internal autofocus/spectral sensors have no general raw sensor stream. NFC and GPS do not identify hidden cameras.");
        if (Build.VERSION.SDK_INT >= 29) body.addView(button("Enable activity sensors", () -> permissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, () -> { sensors.stop(); sensors.start(); })));
        LinearLayout list = card(body, "LIVE SENSOR INVENTORY", null);
        sensorView = text("Loading sensor inventory…", 12, TEXT); sensorView.setTextIsSelectable(true); list.addView(sensorView);
    }

    private void buildNotes(LinearLayout body) {
        body.addView(text("Keep your observations.", 27, TEXT)); label(body, "PRIVATE UNTIL YOU SHARE");
        LinearLayout notes = card(body, "INSPECTION NOTES", null);
        noteInput = new EditText(this); noteInput.setText(note); noteInput.setTextColor(TEXT); noteInput.setHintTextColor(MUTED);
        noteInput.setHint("Object, room and what you observed…"); noteInput.setMinLines(3); noteInput.setGravity(Gravity.TOP); noteInput.setTextSize(15);
        notes.addView(noteInput, new LinearLayout.LayoutParams(-1, -2));
        row(body, button("Share report", this::shareReport), button("Share latest photo", this::sharePhoto));
        body.addView(button("Delete saved evidence", () -> new android.app.AlertDialog.Builder(this)
                .setTitle("Delete local evidence?").setMessage("This deletes saved photos, notes and cached reports on this device.")
                .setNegativeButton("Cancel", null).setPositiveButton("Delete", (d, w) -> deleteEvidence()).show()));
        card(body, "Privacy", "Camera frames and microphone samples stay in memory unless you tap Save photo. Saved photos and notes remain in app-private storage. Reports include observed sensor values and nearby device identifiers. Sharing uses the app you choose. No accounts, analytics, ads or cloud detection.");
        card(body, "LensGuard 1.1.0 · evaluation build", "Designed for Pixel 9; adapts to other Android devices. Development-signed release. Hardware behavior still needs testing on a real Pixel 9. This app cannot guarantee a room is free of hidden cameras.");
    }

    private void permissions(String[] needed, Runnable action) {
        List<String> missing = new ArrayList<>();
        for (String permission : needed) if (!granted(permission)) missing.add(permission);
        if (missing.isEmpty()) action.run();
        else { permissionAction = action; permissionTab = tab; permissionResolved = false; permissionLauncher.launch(missing.toArray(new String[0])); }
    }
    private void runPermissionActionIfReady() {
        if (!resumed || !permissionResolved || permissionAction == null) return;
        Runnable action = permissionAction; permissionAction = null; permissionResolved = false;
        if (tab == permissionTab) action.run();
    }
    private String[] radioPermissions() {
        List<String> list = new ArrayList<>(); list.add(Manifest.permission.ACCESS_COARSE_LOCATION); list.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 31) { list.add(Manifest.permission.BLUETOOTH_SCAN); list.add(Manifest.permission.BLUETOOTH_CONNECT); }
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        return list.toArray(new String[0]);
    }
    private boolean granted(String permission) { return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED; }

    private void startCamera() {
        if (!resumed || tab != 1 || previewView == null) return;
        if (!granted(Manifest.permission.CAMERA)) { optical = "Allow camera access to use visual inspection."; set(opticalView, optical); return; }
        stopCamera(); final int generation = cameraGeneration;
        set(cameraPlaceholder, "Starting camera…");
        optical = "Starting " + (front ? "front" : "rear") + " camera…"; set(opticalView, optical);
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            if (!resumed || tab != 1 || generation != cameraGeneration || previewView == null) return;
            try {
                cameraProvider = future.get();
                CameraSelector selector = front ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
                if (!cameraProvider.hasCamera(selector)) { stopCamera(); optical = "Selected camera unavailable."; set(opticalView, optical); return; }
                Preview preview = new Preview.Builder().build(); preview.setSurfaceProvider(previewView.getSurfaceProvider());
                capture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
                ImageAnalysis analysis = new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                analysis.setAnalyzer(imageExecutor, image -> analyzeFrame(image, generation));
                camera = cameraProvider.bindToLifecycle(this, selector, preview, capture, analysis);
                cameraPlaceholder.setVisibility(View.GONE); analyzedFrames = 0; lastFrameAt = SystemClock.elapsedRealtime();
                cameraButton.setText("Stop camera"); enabled(photoButton, true); enabled(torchButton, camera.getCameraInfo().hasFlashUnit());
                set(cameraStatus, "STARTING · Waiting for the first camera frame");
                optical = "Live view · move slowly and inspect repeatable reflections."; set(opticalView, optical);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } catch (Exception e) { stopCamera(); optical = "Camera unavailable. Close other camera apps and retry."; set(opticalView, optical); }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeFrame(ImageProxy image, int generation) {
        try {
            long now = SystemClock.elapsedRealtime(); if (now - lastAnalysis < 450) return; lastAnalysis = now;
            ImageProxy.PlaneProxy plane = image.getPlanes()[0]; ByteBuffer pixels = plane.getBuffer();
            int step = Math.max(1, image.getWidth() / 160), width = image.getWidth() / step, height = image.getHeight() / step;
            byte[] gray = new byte[width * height]; int sum = 0;
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                byte value = pixels.get(y * step * plane.getRowStride() + x * step * plane.getPixelStride());
                gray[y * width + x] = value; sum += value & 255;
            }
            int glints = GlintDetector.count(gray, width, height);
            String result = glints > 0 ? glints + " small highlight(s) in this frame · inspect from several angles."
                    : "No small bright points observed · continue visual inspection.";
            if (sum / gray.length < 12) result = "Very dark view · turn on the light and sweep slowly.";
            final String message = result;
            runOnUiThread(() -> { if (resumed && tab == 1 && generation == cameraGeneration) { lastFrameAt = SystemClock.elapsedRealtime(); optical = message; set(opticalView, optical); set(cameraStatus, "LIVE · " + (++analyzedFrames) + " frames checked · " + (front ? "Front" : "Rear") + " camera"); } });
        } catch (RuntimeException ignored) { /* A dropped frame must not interrupt preview. */ }
        finally { image.close(); }
    }
    private void toggleTorch() {
        if (camera == null) { toast("Start the camera first."); return; }
        if (!camera.getCameraInfo().hasFlashUnit()) { toast("This camera has no flashlight. Try the rear camera."); return; }
        torch = !torch; camera.getCameraControl().enableTorch(torch); torchButton.setText(torch ? "Turn light off" : "Turn light on");
    }
    private void stopCamera() {
        cameraGeneration++; torch = false;
        if (camera != null) camera.getCameraControl().enableTorch(false);
        if (cameraProvider != null) cameraProvider.unbindAll(); camera = null; capture = null;
        if (torchButton != null) torchButton.setText("Turn light on");
        enabled(torchButton, false); enabled(photoButton, false);
        if (cameraButton != null) cameraButton.setText("Start camera");
        if (cameraPlaceholder != null) { cameraPlaceholder.setVisibility(View.VISIBLE); set(cameraPlaceholder, "Camera is off\nTap Start camera below"); }
        set(cameraStatus, "OFF · No camera frames being analyzed");
        if (opticalView != null) set(opticalView, "Camera stopped. Tap Start camera to continue.");
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void savePhoto() {
        if (capture == null) { toast("Start the camera first."); return; }
        File directory = new File(getFilesDir(), "evidence");
        if (!directory.exists() && !directory.mkdirs()) { toast("Unable to create private evidence storage."); return; }
        File target = new File(directory, "inspection-" + System.currentTimeMillis() + ".jpg");
        capture.takePicture(new ImageCapture.OutputFileOptions.Builder(target).build(), ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override public void onImageSaved(ImageCapture.OutputFileResults result) { lastPhoto = target; toast("Photo saved privately. Share it from Tools → Notes."); }
            @Override public void onError(ImageCaptureException error) { toast("Photo could not be saved. Retry."); }
        });
    }
    private void sharePhoto() {
        if (lastPhoto == null || !lastPhoto.exists()) {
            File[] photos = new File(getFilesDir(), "evidence").listFiles();
            if (photos != null) for (File f : photos) if (f.getName().endsWith(".jpg") && (lastPhoto == null || f.lastModified() > lastPhoto.lastModified())) lastPhoto = f;
        }
        if (lastPhoto == null || !lastPhoto.exists()) { toast("No saved photo yet. Tap Save photo during a sweep."); return; }
        shareFile(lastPhoto, "image/jpeg");
    }
    private void shareReport() {
        rememberNote();
        try {
            JSONObject report = new JSONObject();
            report.put("app", "LensGuard 1.1.0"); report.put("time", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date()));
            report.put("device", Build.MANUFACTURER + " " + Build.MODEL); report.put("android", Build.VERSION.RELEASE);
            report.put("limitation", "Inspection observations only. No measurement confirms a camera or proves its absence.");
            report.put("notes", note); report.put("visualObservation", optical); report.put("radioObservations", radio); report.put("audioObservation", sound);
            if (sensorSnapshot != null) { report.put("magnetic", sensorSnapshot.magnetic); report.put("environment", sensorSnapshot.environment); report.put("motion", sensorSnapshot.motion); report.put("sensorInventory", sensorSnapshot.inventory); }
            File directory = new File(getCacheDir(), "reports");
            if (!directory.exists() && !directory.mkdirs()) throw new java.io.IOException("Cannot create report storage");
            File file = new File(directory, "lensguard-report.json");
            try (FileOutputStream out = new FileOutputStream(file)) { out.write(report.toString(2).getBytes(StandardCharsets.UTF_8)); }
            shareFile(file, "application/json");
        } catch (Exception e) { toast("Could not create report. Please retry."); }
    }
    private void shareFile(File file, String mime) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
        Intent send = new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newRawUri("LensGuard evidence", uri));
        startActivity(Intent.createChooser(send, "Share observation"));
    }
    private void deleteEvidence() {
        boolean success = true;
        for (File dir : new File[]{new File(getFilesDir(), "evidence"), new File(getCacheDir(), "reports")}) {
            File[] files = dir.listFiles(); if (files != null) for (File file : files) success &= file.delete();
        }
        lastPhoto = null; note = ""; if (noteInput != null) noteInput.setText(""); getPreferences(MODE_PRIVATE).edit().remove("note").apply();
        toast(success ? "Saved evidence deleted." : "Some files could not be deleted. Retry.");
    }
    private void rememberNote() { if (noteInput != null) note = noteInput.getText().toString(); }
    private void stopAudio() { listening = false; if (audio != null) audio.stop(); if (audioButton != null) audioButton.setText("Enable microphone"); }
    @Override protected void onResume() { super.onResume(); resumed = true; sensors.start(); runPermissionActionIfReady(); }
    @Override protected void onPause() { resumed = false; rememberNote(); getPreferences(MODE_PRIVATE).edit().putString("note", note).apply(); stopCamera(); wireless.stop(); stopAudio(); sensors.stop(); super.onPause(); }
    @Override protected void onDestroy() { stopCamera(); wireless.stop(); audio.stop(); sensors.stop(); imageExecutor.shutdownNow(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) { rememberNote(); state.putInt("tab", tab); state.putInt("tool", tool); state.putBoolean("front", front); state.putString("note", note); super.onSaveInstanceState(state); }

    private void enabled(Button button, boolean value) { if (button != null) { button.setEnabled(value); button.setAlpha(value ? 1f : .45f); } }
    private Button primary(String title, Runnable action) { Button button = button(title, action); button.setTextColor(BG); button.setBackground(shape(MINT, 12)); return button; }
    private void set(TextView view, String message) { if (view != null) view.setText(message); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int color, int radius) { GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable; }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setLineSpacing(dp(3), 1);
        if (size >= 22) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }
    private Button button(String title, Runnable action) {
        Button view = new Button(this); view.setText(title); view.setAllCaps(false); view.setTextSize(13); view.setTextColor(TEXT);
        view.setMinWidth(0); view.setMinimumWidth(0); view.setMinHeight(dp(48)); view.setPadding(dp(8), dp(4), dp(8), dp(4));
        view.setBackground(shape(CARD, 12)); view.setOnClickListener(v -> action.run()); return view;
    }
    private void label(LinearLayout body, String title) { TextView v = text(title.toUpperCase(Locale.US), 10, MUTED); v.setLetterSpacing(.12f); v.setPadding(0, dp(8), 0, dp(8)); body.addView(v); }
    private void row(LinearLayout body, View left, View right) {
        LinearLayout row = new LinearLayout(this); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1); p.setMargins(0, dp(6), dp(4), dp(6)); row.addView(left, p);
        LinearLayout.LayoutParams q = new LinearLayout.LayoutParams(0, dp(48), 1); q.setMargins(dp(4), dp(6), 0, dp(6)); row.addView(right, q); body.addView(row);
    }
    private LinearLayout card(LinearLayout body, String title, String copy) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16), dp(16), dp(16), dp(16)); card.setBackground(shape(CARD, 16));
        TextView heading = text(title, 13, TEXT); heading.setTypeface(Typeface.DEFAULT_BOLD); heading.setPadding(0, 0, 0, dp(8)); card.addView(heading);
        if (copy != null) card.addView(text(copy, 13, MUTED)); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, dp(10), 0, dp(4)); body.addView(card, p); return card;
    }
}
