package com.feloguarin.lensguard;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends ComponentActivity {
    static final int START = 0, CAMERA = 1, NEARBY = 2, SENSORS = 3, REPORT = 4;
    private static final int[] TAB_NAMES = {R.string.tab_start, R.string.tab_camera, R.string.tab_nearby,
            R.string.tab_sensors, R.string.tab_report};
    private static final String DRAFT = "note_draft";

    Ui ui;
    InspectionStore store;
    Thumbnails thumbnails;
    SensorMonitor sensors;
    WirelessProbe wireless;
    SignalFollower follower;
    AudioProbe audio;
    LensCamera lens;
    SensorMonitor.Snapshot sensorSnapshot;
    WirelessProbe.Survey survey;
    String soundReading;
    /** The checklist spot that new photos, readings and notes are linked to, or null. */
    String spot;
    boolean listening;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private Screen[] screens;
    private LinearLayout pages, nav;
    private ScrollView scroll;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private Runnable permissionAction;
    private int permissionTab;
    private boolean permissionResolved;
    private int tab;
    private boolean resumed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new Ui(this);
        store = new InspectionStore(this);
        thumbnails = new Thumbnails();
        screens = new Screen[] {new StartScreen(this), new CameraScreen(this), new NearbyScreen(this),
                new SensorsScreen(this), new ReportScreen(this)};
        if (store.migrateLegacy(getPreferences(MODE_PRIVATE))) toast(getString(R.string.inspection_imported_toast));
        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            permissionResolved = true;
            runPermissionActionIfReady();
        });
        sensors = new SensorMonitor(this, snapshot -> {
            sensorSnapshot = snapshot;
            current().onSensors(snapshot);
        });
        wireless = new WirelessProbe(this, result -> {
            survey = result;
            current().onWireless(result);
        });
        follower = new SignalFollower(this, followState -> current().onFollower(followState));
        audio = new AudioProbe(this, message -> {
            soundReading = message;
            listening = audio.isRunning();
            current().onAudio(message);
        });
        lens = new LensCamera(this, (CameraScreen) screens[CAMERA], () -> sensorSnapshot != null
                && Double.isFinite(sensorSnapshot.rotationRate) && sensorSnapshot.rotationRate > LensCamera.MOVING_RAD_PER_S);
        if (state != null) {
            tab = Math.max(0, Math.min(REPORT, state.getInt("tab")));
            spot = state.getString("spot");
            lens.setFront(state.getBoolean("front"));
            for (Screen screen : screens) screen.restore(state);
        } else {
            ((ReportScreen) screens[REPORT]).draft = getPreferences(MODE_PRIVATE).getString(DRAFT, "");
        }
        buildChrome();
        showTab(tab);
    }

    private void buildChrome() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(ui.dp(18), 0, ui.dp(18), 0);
        if (Build.VERSION.SDK_INT >= 30) root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(ui.dp(18) + bars.left, bars.top, ui.dp(18) + bars.right, bars.bottom);
            return insets;
        });
        else root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(ui.dp(18) + insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    ui.dp(18) + insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, ui.dp(14), 0, ui.dp(12));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_lensguard);
        logo.setImportantForAccessibility(ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(logo, new LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(ui.dp(10), 0, 0, 0);
        titles.addView(ui.text(getString(R.string.app_name), 23, Ui.TEXT));
        titles.addView(ui.text(getString(R.string.header_tagline), 10, Ui.MUTED));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header);
        TextView caution = ui.text(getString(R.string.caution), 12, Ui.AMBER);
        caution.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        caution.setBackground(ui.shape(Ui.AMBER_SURFACE, 12));
        root.addView(caution);
        pages = new LinearLayout(this);
        pages.setOrientation(LinearLayout.VERTICAL);
        root.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        nav = new LinearLayout(this);
        nav.setPadding(0, ui.dp(8), 0, ui.dp(8));
        root.addView(nav);
        setContentView(root);
        root.requestApplyInsets();
    }

    Screen current() {
        return screens[tab];
    }

    Inspection inspection() {
        return store.current();
    }

    void saveInspection() {
        if (!store.save(store.current())) toast(getString(R.string.storage_failed));
    }

    /** Adds an observation to the current inspection, linked to the active checklist spot. */
    Observation record(String source, String summary, String note, String photo, JSONObject data) {
        return recordAt(spot, source, summary, note, photo, data);
    }

    Observation recordAt(String spotId, String source, String summary, String note, String photo, JSONObject data) {
        Observation observation = new Observation(source, System.currentTimeMillis());
        observation.spot = Checklist.find(spotId) == null ? null : spotId;
        observation.summary = summary;
        observation.note = note;
        observation.photo = photo;
        observation.data = data;
        inspection().observations.add(observation);
        saveInspection();
        return observation;
    }

    void showTab(int index) {
        screens[tab].leave();
        tab = index;
        stopTools();
        pages.removeAllViews();
        nav.removeAllViews();
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int target = i;
            Button button = ui.button(getString(TAB_NAMES[i]), () -> openTab(target));
            boolean selected = i == index;
            button.setTextColor(selected ? Ui.BG : Ui.MUTED);
            button.setBackground(ui.surface(selected ? Ui.MINT : Ui.CARD, 12));
            button.setSelected(selected);
            button.setPadding(ui.dp(2), ui.dp(4), ui.dp(2), ui.dp(4));
            button.setMaxLines(1);
            button.setAutoSizeTextTypeUniformWithConfiguration(9, 13, 1, TypedValue.COMPLEX_UNIT_SP);
            ViewCompat.setStateDescription(button, selected ? getString(R.string.tab_selected) : null);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ui.dp(48), 1);
            params.setMargins(ui.dp(2), 0, ui.dp(2), 0);
            nav.addView(button, params);
        }
        scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, ui.dp(16), 0, ui.dp(12));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        pages.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        screens[index].build(body);
        if (sensorSnapshot != null) screens[index].onSensors(sensorSnapshot);
    }

    /** Tab-bar navigation always lands on a tab's first page. */
    private void openTab(int index) {
        if (index == START) ((StartScreen) screens[START]).openSpot = null;
        if (index == SENSORS) ((SensorsScreen) screens[SENSORS]).page = SensorsScreen.LIST;
        if (index == NEARBY) ((NearbyScreen) screens[NEARBY]).page = NearbyScreen.SURVEY;
        showTab(index);
    }

    /** Rebuilds the current page in place, keeping the scroll position. */
    void refresh() {
        int y = scroll == null ? 0 : scroll.getScrollY();
        showTab(tab);
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    void openSpot(String id) {
        ((StartScreen) screens[START]).openSpot = id;
        showTab(START);
    }

    /** Opens a tool for a checklist spot so what the user saves is linked to it. */
    void inspectSpot(String id, int target) {
        spot = id;
        if (target == SENSORS) ((SensorsScreen) screens[SENSORS]).page = SensorsScreen.MAGNETIC;
        if (target == NEARBY) ((NearbyScreen) screens[NEARBY]).page = NearbyScreen.SURVEY;
        showTab(target);
    }

    void openSensorPage(int page) {
        ((SensorsScreen) screens[SENSORS]).page = page;
        showTab(SENSORS);
    }

    void openNotes() {
        showTab(REPORT);
        ((ReportScreen) screens[REPORT]).focusNote();
    }

    private void stopTools() {
        lens.stop();
        wireless.stop();
        follower.stop();
        stopAudio();
    }

    void stopAudio() {
        listening = false;
        if (audio != null) audio.stop();
    }

    void keepScreenOn(boolean on) {
        if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    void permissions(String[] needed, Runnable action) {
        List<String> missing = new ArrayList<>();
        for (String permission : needed) if (!granted(permission)) missing.add(permission);
        if (missing.isEmpty()) action.run();
        else {
            permissionAction = action;
            permissionTab = tab;
            permissionResolved = false;
            permissionLauncher.launch(missing.toArray(new String[0]));
        }
    }

    private void runPermissionActionIfReady() {
        if (!resumed || !permissionResolved || permissionAction == null) return;
        Runnable action = permissionAction;
        permissionAction = null;
        permissionResolved = false;
        if (tab == permissionTab) action.run();
    }

    String[] radioPermissions() {
        List<String> list = new ArrayList<>();
        list.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        list.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 31) {
            list.add(Manifest.permission.BLUETOOTH_SCAN);
            list.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        return list.toArray(new String[0]);
    }

    boolean granted(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    String sourceName(String source) {
        switch (source) {
            case Observation.CAMERA: return getString(R.string.source_camera);
            case Observation.NEARBY: return getString(R.string.source_nearby);
            case Observation.MAGNETIC: return getString(R.string.source_magnetic);
            case Observation.SOUND: return getString(R.string.source_sound);
            default: return getString(R.string.source_note);
        }
    }

    boolean includeAddresses() {
        return getSharedPreferences(InspectionStore.PREFERENCES, MODE_PRIVATE).getBoolean("include_addresses", false);
    }

    void setIncludeAddresses(boolean include) {
        getSharedPreferences(InspectionStore.PREFERENCES, MODE_PRIVATE).edit().putBoolean("include_addresses", include).apply();
    }

    /** Runs slow work, such as report rendering, away from the main thread. */
    void inBackground(Runnable work) {
        background.execute(work);
    }

    void onMain(Runnable work) {
        main.post(() -> { if (!isDestroyed()) work.run(); });
    }

    void share(File file, String mime) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
        Intent send = new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newRawUri(getString(R.string.share_clip_label), uri));
        startActivity(Intent.createChooser(send, getString(R.string.share_title)));
    }

    void shareAll(List<File> files, String mime) {
        if (files.size() == 1) {
            share(files.get(0), mime);
            return;
        }
        ArrayList<Uri> uris = new ArrayList<>();
        ClipData clip = null;
        for (File file : files) {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
            uris.add(uri);
            if (clip == null) clip = ClipData.newRawUri(getString(R.string.share_clip_label), uri);
            else clip.addItem(new ClipData.Item(uri));
        }
        Intent send = new Intent(Intent.ACTION_SEND_MULTIPLE).setType(mime)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(clip);
        startActivity(Intent.createChooser(send, getString(R.string.share_title)));
    }

    void viewPhoto(File photo) {
        ImageView image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setContentDescription(getString(R.string.observation_photo_description));
        AlertDialog dialog = new AlertDialog.Builder(this).setView(image)
                .setPositiveButton(R.string.action_close, null).create();
        dialog.show();
        inBackground(() -> {
            Bitmap bitmap = Photos.decode(photo, 1600);
            onMain(() -> {
                if (bitmap != null && dialog.isShowing()) image.setImageBitmap(bitmap);
            });
        });
    }

    void confirm(int title, int message, int action, Runnable onConfirm) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(action, (d, w) -> onConfirm.run()).show();
    }

    void toast(CharSequence message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /** Removes every saved inspection, photo and report copy on this device. */
    void deleteAllEvidence() {
        boolean success = store.deleteAll();
        getPreferences(MODE_PRIVATE).edit().remove("note").remove(DRAFT).apply();
        ((ReportScreen) screens[REPORT]).clearDraft();
        spot = null;
        thumbnails.clear();
        toast(getString(success ? R.string.evidence_deleted : R.string.evidence_delete_failed));
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        sensors.start();
        runPermissionActionIfReady();
    }

    @Override protected void onPause() {
        resumed = false;
        current().pause();
        SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
        editor.putString(DRAFT, ((ReportScreen) screens[REPORT]).draft).apply();
        stopTools();
        sensors.stop();
        super.onPause();
    }

    @Override protected void onDestroy() {
        lens.release();
        wireless.stop();
        follower.stop();
        audio.stop();
        sensors.stop();
        background.shutdownNow();
        thumbnails.shutdown();
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("tab", tab);
        state.putString("spot", spot);
        state.putBoolean("front", lens.isFront());
        for (Screen screen : screens) screen.save(state);
        super.onSaveInstanceState(state);
    }
}
