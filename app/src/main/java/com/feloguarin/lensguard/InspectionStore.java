package com.feloguarin.lensguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

/**
 * App-private inspections. Each inspection is one JSON file; photos stay in the evidence folder
 * used since 1.0. Nothing here leaves the device unless the user shares it.
 */
final class InspectionStore {
    static final String PREFERENCES = "lensguard";
    private static final String CURRENT = "current_inspection";
    private static final String MIGRATED = "migrated_1x";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    private final Context context;
    private final File directory;
    private final File evidence;
    private final SharedPreferences preferences;
    private Inspection current;

    InspectionStore(Context context) {
        this.context = context.getApplicationContext();
        directory = new File(this.context.getFilesDir(), "inspections");
        evidence = new File(this.context.getFilesDir(), "evidence");
        preferences = this.context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    /** The inspection that new observations join. Created on first use. */
    Inspection current() {
        if (current != null) return current;
        String id = preferences.getString(CURRENT, null);
        if (id != null) current = load(id);
        if (current == null) {
            List<Inspection> all = all();
            if (!all.isEmpty()) {
                current = all.get(0);
                preferences.edit().putString(CURRENT, current.id).apply();
            }
        }
        if (current == null) current = create(defaultTitle());
        return current;
    }

    /** All inspections, most recently changed first. */
    List<Inspection> all() {
        List<Inspection> result = new ArrayList<>();
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (!name.endsWith(".json")) continue;
                Inspection inspection = load(name.substring(0, name.length() - 5));
                if (inspection != null) result.add(inspection);
            }
        }
        result.sort((a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        return result;
    }

    Inspection create(String title) {
        Inspection inspection = new Inspection(title, System.currentTimeMillis());
        save(inspection);
        select(inspection);
        return inspection;
    }

    void select(Inspection inspection) {
        current = inspection;
        preferences.edit().putString(CURRENT, inspection.id).apply();
    }

    /** Saves the inspection and marks it changed. Returns false when storage failed. */
    boolean save(Inspection inspection) {
        inspection.updatedAt = Math.max(System.currentTimeMillis(), inspection.createdAt);
        if (!directory.exists() && !directory.mkdirs()) return false;
        AtomicFile file = new AtomicFile(new File(directory, inspection.id + ".json"));
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write(inspection.toJson().toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out);
            return true;
        } catch (IOException | JSONException failure) {
            if (out != null) file.failWrite(out);
            return false;
        }
    }

    /** Deletes the inspection and the photos it references. */
    boolean delete(Inspection inspection) {
        boolean success = true;
        for (Observation observation : inspection.observations) {
            File photo = photoFile(observation.photo);
            if (photo != null && photo.exists()) success &= photo.delete();
        }
        new AtomicFile(new File(directory, inspection.id + ".json")).delete();
        if (current != null && current.id.equals(inspection.id)) {
            current = null;
            preferences.edit().remove(CURRENT).apply();
        }
        return success;
    }

    /** Deletes every inspection, photo, shared-report copy and the 1.x note. */
    boolean deleteAll() {
        boolean success = true;
        for (File folder : new File[] {directory, evidence, new File(context.getCacheDir(), "reports")}) {
            File[] files = folder.listFiles();
            if (files != null) for (File file : files) success &= file.delete();
        }
        current = null;
        preferences.edit().remove(CURRENT).apply();
        return success;
    }

    File evidenceDirectory() {
        return evidence;
    }

    /** A photo inside the private evidence folder, or null for a missing or unsafe name. */
    File photoFile(String name) {
        if (name == null || name.contains("/") || name.contains("\\") || name.startsWith(".")) return null;
        return new File(evidence, name);
    }

    File newPhotoFile() {
        if (!evidence.exists() && !evidence.mkdirs()) return null;
        return new File(evidence, "inspection-" + System.currentTimeMillis() + ".jpg");
    }

    /**
     * Imports a LensGuard 1.x note and private photos into an inspection once. Returns true when
     * something was imported.
     */
    boolean migrateLegacy(SharedPreferences legacy) {
        if (preferences.getBoolean(MIGRATED, false)) return false;
        String note = legacy == null ? "" : legacy.getString("note", "");
        File[] photos = evidence.listFiles((folder, name) -> name.endsWith(".jpg"));
        boolean hasNote = note != null && !note.trim().isEmpty();
        boolean imported = false;
        if (hasNote || (photos != null && photos.length > 0)) {
            Inspection inspection = new Inspection(context.getString(R.string.inspection_imported_title), System.currentTimeMillis());
            if (photos != null) {
                Arrays.sort(photos, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
                for (File photo : photos) {
                    Observation observation = new Observation(Observation.CAMERA, photo.lastModified());
                    observation.summary = context.getString(R.string.inspection_imported_photo);
                    observation.photo = photo.getName();
                    inspection.observations.add(observation);
                }
            }
            if (hasNote) {
                Observation observation = new Observation(Observation.NOTE, System.currentTimeMillis());
                observation.note = note.trim();
                inspection.observations.add(observation);
            }
            imported = save(inspection);
            if (imported) select(inspection);
        }
        // Keep the old note if the import could not be written, so a later launch can retry.
        if (imported || !(hasNote || (photos != null && photos.length > 0))) {
            if (legacy != null) legacy.edit().remove("note").apply();
            preferences.edit().putBoolean(MIGRATED, true).apply();
        }
        return imported;
    }

    String defaultTitle() {
        return context.getString(R.string.inspection_default_title,
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date()));
    }

    private Inspection load(String id) {
        if (id == null || !SAFE_ID.matcher(id).matches()) return null;
        AtomicFile file = new AtomicFile(new File(directory, id + ".json"));
        try {
            return Inspection.fromJson(new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8)));
        } catch (IOException | JSONException damaged) {
            return null;
        }
    }
}
