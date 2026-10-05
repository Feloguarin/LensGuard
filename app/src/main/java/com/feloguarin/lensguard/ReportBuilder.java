package com.feloguarin.lensguard;

import android.content.Context;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

/** Machine-readable inspection reports. Addresses are masked unless the user includes them. */
final class ReportBuilder {
    static final String FORMAT = "lensguard-report";
    static final int FORMAT_VERSION = 2;
    static final long KEEP_SHARED_MS = 60 * 60 * 1000;

    private ReportBuilder() {}

    static JSONObject json(Context context, Inspection inspection, boolean includeAddresses) throws JSONException {
        JSONObject report = new JSONObject();
        report.put("format", FORMAT);
        report.put("formatVersion", FORMAT_VERSION);
        report.put("app", "LensGuard " + BuildConfig.VERSION_NAME);
        report.put("generated", iso(System.currentTimeMillis()));
        report.put("device", Build.MANUFACTURER + " " + Build.MODEL);
        report.put("android", Build.VERSION.RELEASE);
        report.put("limitation", context.getString(R.string.report_limitation));
        report.put("addresses", includeAddresses ? "included" : "masked");
        JSONObject body = new JSONObject();
        body.put("id", inspection.id);
        body.put("title", inspection.title);
        body.put("created", iso(inspection.createdAt));
        body.put("updated", iso(inspection.updatedAt));
        JSONArray spots = new JSONArray();
        for (Checklist.Spot spot : Checklist.SPOTS) {
            JSONObject item = new JSONObject();
            item.put("id", spot.id);
            item.put("title", context.getString(spot.title));
            String status = inspection.status(spot.id);
            item.put("status", status == null ? "not_inspected" : status);
            spots.put(item);
        }
        body.put("checklist", spots);
        JSONArray observations = new JSONArray();
        for (Observation observation : inspection.observations) {
            JSONObject item = observation.toJson();
            item.put("time", iso(observation.time));
            if (observation.data != null) {
                JSONObject data = new JSONObject(observation.data.toString());
                if (!includeAddresses) maskAddresses(data);
                item.put("data", data);
            }
            observations.put(item);
        }
        body.put("observations", observations);
        report.put("inspection", body);
        return report;
    }

    /** Replaces every "address" value, at any depth, with its masked form. */
    static void maskAddresses(Object node) throws JSONException {
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            for (Iterator<String> keys = object.keys(); keys.hasNext(); ) {
                String key = keys.next();
                Object value = object.get(key);
                if ("address".equals(key) && value instanceof String) object.put(key, Text.maskAddress((String) value));
                else maskAddresses(value);
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) maskAddresses(array.get(i));
        }
    }

    static String iso(long time) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date(time));
    }

    /**
     * A fresh file in the private report cache. Copies older than an hour are removed; newer ones
     * stay because an app that received one may not have read it yet.
     */
    static File newReportFile(Context context, String extension) throws IOException {
        File directory = new File(context.getCacheDir(), "reports");
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("Cannot create report storage");
        long now = System.currentTimeMillis();
        File[] old = directory.listFiles();
        if (old != null) {
            for (File file : old) if (now - file.lastModified() > KEEP_SHARED_MS) //noinspection ResultOfMethodCallIgnored
                file.delete();
        }
        String stamp = new SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(new Date(now));
        return new File(directory, "LensGuard-report-" + stamp + "." + extension);
    }

    static File writeJson(Context context, Inspection inspection, boolean includeAddresses) throws IOException, JSONException {
        File file = newReportFile(context, "json");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(json(context, inspection, includeAddresses).toString(2).getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }
}
