package com.feloguarin.lensguard;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/** One saved observation: what a tool showed, an optional photo and the user's own note. */
public final class Observation {
    public static final String CAMERA = "camera";
    public static final String NEARBY = "nearby";
    public static final String MAGNETIC = "magnetic";
    public static final String SOUND = "sound";
    public static final String NOTE = "note";

    public final String id;
    public final long time;
    public final String source;
    public String spot;
    public String summary;
    public String note;
    public String photo;
    public JSONObject data;

    public Observation(String source, long time) {
        this("o-" + UUID.randomUUID(), source, time);
    }

    private Observation(String id, String source, long time) {
        this.id = id;
        this.source = source;
        this.time = time;
    }

    JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("time", time);
        json.put("source", source);
        if (spot != null) json.put("spot", spot);
        if (summary != null) json.put("summary", summary);
        if (note != null) json.put("note", note);
        if (photo != null) json.put("photo", photo);
        if (data != null) json.put("data", data);
        return json;
    }

    static Observation fromJson(JSONObject json) throws JSONException {
        Observation observation = new Observation(json.getString("id"), json.getString("source"), json.getLong("time"));
        observation.spot = json.optString("spot", null);
        observation.summary = json.optString("summary", null);
        observation.note = json.optString("note", null);
        observation.photo = json.optString("photo", null);
        observation.data = json.optJSONObject("data");
        return observation;
    }
}
