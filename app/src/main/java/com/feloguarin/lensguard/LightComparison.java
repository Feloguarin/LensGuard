package com.feloguarin.lensguard;

import java.util.ArrayList;
import java.util.List;

/**
 * Compares highlights seen with the phone light on and off from the same position. A highlight
 * that disappears without the light was a reflection of it, as a lens can be, and so can glass or
 * metal. One that remains is a light source or a reflection of room light.
 */
public final class LightComparison {
    public static final int FRAMES_PER_PHASE = 3;
    static final int MINIMUM_FRAMES = 2;

    public enum Kind { REFLECTS_PHONE_LIGHT, VISIBLE_WITHOUT_LIGHT }

    public static final class Finding {
        public final float x;
        public final float y;
        public final Kind kind;

        Finding(float x, float y, Kind kind) {
            this.x = x;
            this.y = y;
            this.kind = kind;
        }
    }

    public static final class Result {
        public final List<Finding> findings;
        public final int reflections;
        public final int lightSources;
        public final boolean moved;

        Result(List<Finding> findings, boolean moved) {
            this.findings = findings;
            this.moved = moved;
            int reflected = 0, sources = 0;
            for (Finding finding : findings) {
                if (finding.kind == Kind.REFLECTS_PHONE_LIGHT) reflected++;
                else sources++;
            }
            reflections = reflected;
            lightSources = sources;
        }
    }

    private final List<List<float[]>> lightOn = new ArrayList<>();
    private final List<List<float[]>> lightOff = new ArrayList<>();
    private final float aspect;
    private final float radius;

    public LightComparison(float aspect) {
        this(aspect, 0.05f);
    }

    public LightComparison(float aspect, float radius) {
        this.aspect = aspect > 0 ? aspect : 1;
        this.radius = radius;
    }

    public void addLightOn(List<float[]> points) { lightOn.add(new ArrayList<>(points)); }
    public void addLightOff(List<float[]> points) { lightOff.add(new ArrayList<>(points)); }
    public int lightOnFrames() { return lightOn.size(); }
    public int lightOffFrames() { return lightOff.size(); }

    public Result result(boolean moved) {
        List<float[]> on = stable(lightOn, aspect, radius, MINIMUM_FRAMES);
        List<float[]> off = stable(lightOff, aspect, radius, MINIMUM_FRAMES);
        List<Finding> findings = new ArrayList<>();
        boolean[] offMatched = new boolean[off.size()];
        // Auto-exposure can shift a point slightly between phases, so allow extra distance.
        float tolerance = radius * 1.5f;
        for (float[] point : on) {
            int match = nearest(point, off, offMatched, tolerance);
            if (match >= 0) offMatched[match] = true;
            findings.add(new Finding(point[0], point[1],
                    match >= 0 ? Kind.VISIBLE_WITHOUT_LIGHT : Kind.REFLECTS_PHONE_LIGHT));
        }
        for (int i = 0; i < off.size(); i++) {
            if (!offMatched[i]) findings.add(new Finding(off.get(i)[0], off.get(i)[1], Kind.VISIBLE_WITHOUT_LIGHT));
        }
        return new Result(findings, moved);
    }

    /** Positions seen in at least {@code minimumFrames} frames, averaged across those frames. */
    static List<float[]> stable(List<List<float[]>> frames, float aspect, float radius, int minimumFrames) {
        List<float[]> clusters = new ArrayList<>(); // {x, y, count}
        for (List<float[]> frame : frames) {
            boolean[] used = new boolean[clusters.size()];
            for (float[] point : frame) {
                int best = -1;
                double bestDistance = radius;
                for (int c = 0; c < used.length; c++) {
                    if (used[c]) continue;
                    double d = HighlightTracker.distance(clusters.get(c)[0], clusters.get(c)[1], point, aspect);
                    if (d <= bestDistance) { best = c; bestDistance = d; }
                }
                if (best < 0) {
                    clusters.add(new float[] {point[0], point[1], 1});
                } else {
                    float[] cluster = clusters.get(best);
                    cluster[2]++;
                    cluster[0] += (point[0] - cluster[0]) / cluster[2];
                    cluster[1] += (point[1] - cluster[1]) / cluster[2];
                    used[best] = true;
                }
            }
        }
        List<float[]> result = new ArrayList<>();
        for (float[] cluster : clusters) {
            if (cluster[2] >= minimumFrames) result.add(new float[] {cluster[0], cluster[1]});
        }
        return result;
    }

    private int nearest(float[] point, List<float[]> candidates, boolean[] taken, float maximum) {
        int best = -1;
        double bestDistance = maximum;
        for (int i = 0; i < candidates.size(); i++) {
            if (taken[i]) continue;
            double d = HighlightTracker.distance(candidates.get(i)[0], candidates.get(i)[1], point, aspect);
            if (d <= bestDistance) { best = i; bestDistance = d; }
        }
        return best;
    }
}
