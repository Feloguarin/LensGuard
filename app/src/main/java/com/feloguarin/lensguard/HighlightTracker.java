package com.feloguarin.lensguard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Follows small highlights across analysed frames. A steady highlight stays in place while the
 * phone is held still; that makes it worth inspecting, not a camera identification.
 */
public final class HighlightTracker {
    public static final class Track {
        public final int id;
        public float x;
        public float y;
        public int hits = 1;
        public int misses;
        public boolean steady;

        Track(int id, float x, float y) {
            this.id = id;
            this.x = x;
            this.y = y;
        }
    }

    private final List<Track> tracks = new ArrayList<>();
    private final float radius;
    private final int steadyHits;
    private final int maxMisses;
    private int nextId = 1;

    public HighlightTracker() {
        this(0.045f, 3, 2);
    }

    /**
     * @param radius match distance as a fraction of the frame height
     * @param steadyHits detections required before a track is steady
     * @param maxMisses consecutive misses before a track is dropped
     */
    public HighlightTracker(float radius, int steadyHits, int maxMisses) {
        this.radius = radius;
        this.steadyHits = steadyHits;
        this.maxMisses = maxMisses;
    }

    /**
     * @param points normalised {x, y} positions from one frame
     * @param aspect frame width/height, so horizontal and vertical distances compare fairly
     * @return tracks visible in this frame
     */
    public List<Track> update(List<float[]> points, float aspect) {
        List<float[]> candidates = new ArrayList<>();
        for (int t = 0; t < tracks.size(); t++) {
            for (int p = 0; p < points.size(); p++) {
                double d = distance(tracks.get(t).x, tracks.get(t).y, points.get(p), aspect);
                if (d <= radius) candidates.add(new float[] {(float) d, t, p});
            }
        }
        Collections.sort(candidates, (a, b) -> Float.compare(a[0], b[0]));
        boolean[] trackMatched = new boolean[tracks.size()];
        boolean[] pointMatched = new boolean[points.size()];
        for (float[] candidate : candidates) {
            int t = (int) candidate[1], p = (int) candidate[2];
            if (trackMatched[t] || pointMatched[p]) continue;
            trackMatched[t] = true;
            pointMatched[p] = true;
            Track track = tracks.get(t);
            float[] point = points.get(p);
            // Light smoothing keeps markers calm without lagging behind a slow sweep.
            track.x = 0.6f * track.x + 0.4f * point[0];
            track.y = 0.6f * track.y + 0.4f * point[1];
            track.hits++;
            track.misses = 0;
            if (track.hits >= steadyHits) track.steady = true;
        }
        List<Track> survivors = new ArrayList<>();
        for (int t = 0; t < tracks.size(); t++) {
            Track track = tracks.get(t);
            if (!trackMatched[t]) track.misses++;
            if (track.misses <= maxMisses) survivors.add(track);
        }
        tracks.clear();
        tracks.addAll(survivors);
        for (int p = 0; p < points.size(); p++) {
            if (!pointMatched[p]) {
                Track track = new Track(nextId++, points.get(p)[0], points.get(p)[1]);
                if (track.hits >= steadyHits) track.steady = true;
                tracks.add(track);
            }
        }
        return visible();
    }

    /** Tracks seen in the latest frame, plus steady tracks that missed a single frame. */
    public List<Track> visible() {
        List<Track> result = new ArrayList<>();
        for (Track track : tracks) {
            if (track.misses == 0 || (track.steady && track.misses == 1)) result.add(track);
        }
        return result;
    }

    public int steadyCount() {
        int count = 0;
        for (Track track : visible()) if (track.steady) count++;
        return count;
    }

    public void reset() {
        tracks.clear();
    }

    static double distance(float x, float y, float[] point, float aspect) {
        double dx = (x - point[0]) * aspect, dy = y - point[1];
        return Math.sqrt(dx * dx + dy * dy);
    }
}
