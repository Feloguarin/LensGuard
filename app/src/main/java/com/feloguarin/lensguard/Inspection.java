package com.feloguarin.lensguard;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A room inspection: checklist progress and the observations collected for it. */
public final class Inspection {
    public static final String INSPECTED = "inspected";
    public static final String CLOSER_LOOK = "closer_look";
    static final int FORMAT = 2;

    public final String id;
    public final long createdAt;
    public String title;
    public long updatedAt;
    public final Map<String, String> spots = new LinkedHashMap<>();
    public final List<Observation> observations = new ArrayList<>();

    public Inspection(String title, long now) {
        this(UUID.randomUUID().toString(), title, now);
    }

    private Inspection(String id, String title, long createdAt) {
        this.id = id;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /** {@link #INSPECTED}, {@link #CLOSER_LOOK}, or null when not yet inspected. */
    public String status(String spot) {
        return spots.get(spot);
    }

    public void setStatus(String spot, String status) {
        if (status == null) spots.remove(spot);
        else spots.put(spot, status);
    }

    public int count(String status) {
        int count = 0;
        for (String value : spots.values()) if (value.equals(status)) count++;
        return count;
    }

    public List<Observation> forSpot(String spot) {
        List<Observation> result = new ArrayList<>();
        for (Observation observation : observations) {
            if (spot != null && spot.equals(observation.spot)) result.add(observation);
        }
        return result;
    }

    public int photoCount() {
        int count = 0;
        for (Observation observation : observations) if (observation.photo != null) count++;
        return count;
    }

    public Observation find(String observationId) {
        for (Observation observation : observations) if (observation.id.equals(observationId)) return observation;
        return null;
    }

    JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("format", FORMAT);
        json.put("id", id);
        json.put("title", title);
        json.put("createdAt", createdAt);
        json.put("updatedAt", updatedAt);
        JSONObject statuses = new JSONObject();
        for (Map.Entry<String, String> entry : spots.entrySet()) statuses.put(entry.getKey(), entry.getValue());
        json.put("spots", statuses);
        JSONArray list = new JSONArray();
        for (Observation observation : observations) list.put(observation.toJson());
        json.put("observations", list);
        return json;
    }

    static Inspection fromJson(JSONObject json) throws JSONException {
        Inspection inspection = new Inspection(json.getString("id"), json.optString("title", ""), json.getLong("createdAt"));
        inspection.updatedAt = json.optLong("updatedAt", inspection.createdAt);
        JSONObject statuses = json.optJSONObject("spots");
        if (statuses != null) {
            for (Iterator<String> keys = statuses.keys(); keys.hasNext(); ) {
                String key = keys.next();
                String status = statuses.optString(key, null);
                if (INSPECTED.equals(status) || CLOSER_LOOK.equals(status)) inspection.spots.put(key, status);
            }
        }
        JSONArray list = json.optJSONArray("observations");
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                // Skip a damaged entry rather than losing the whole inspection.
                if (item != null) {
                    try { inspection.observations.add(Observation.fromJson(item)); }
                    catch (JSONException ignored) { }
                }
            }
        }
        return inspection;
    }
}
