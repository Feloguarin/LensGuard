package com.feloguarin.lensguard;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Smooths received signal strength and reports whether it is rising or falling. Walls, bodies and
 * phone orientation change RSSI, so the trend guides a search; it is not a distance.
 */
public final class SignalTrend {
    public static final int STRONGER = 1;
    public static final int STEADY = 0;
    public static final int WEAKER = -1;
    static final long TREND_WINDOW_MS = 3_000;
    static final double TREND_DB = 3;
    private static final long KEEP_MS = 60_000;

    private final ArrayDeque<double[]> history = new ArrayDeque<>(); // {timeMs, smoothed, raw}
    private double smoothed = Double.NaN;

    public void add(long timeMs, int rssi) {
        // Single advertisements jump by several dB; a gentle filter keeps the trend readable.
        smoothed = Double.isNaN(smoothed) ? rssi : 0.7 * smoothed + 0.3 * rssi;
        history.addLast(new double[] {timeMs, smoothed, rssi});
        while (!history.isEmpty() && timeMs - history.peekFirst()[0] > KEEP_MS) history.removeFirst();
    }

    public double smoothed() { return smoothed; }

    public int latest() {
        return history.isEmpty() ? Integer.MIN_VALUE : (int) history.peekLast()[2];
    }

    public long lastSampleMs() {
        return history.isEmpty() ? Long.MIN_VALUE : (long) history.peekLast()[0];
    }

    /** Compares the newest smoothed value with the one about three seconds earlier. */
    public int trend() {
        if (history.size() < 2) return STEADY;
        double[] newest = history.peekLast();
        double[] reference = null;
        Iterator<double[]> backwards = history.descendingIterator();
        while (backwards.hasNext()) {
            double[] sample = backwards.next();
            if (newest[0] - sample[0] >= TREND_WINDOW_MS) { reference = sample; break; }
        }
        if (reference == null) return STEADY;
        double change = newest[1] - reference[1];
        return change >= TREND_DB ? STRONGER : change <= -TREND_DB ? WEAKER : STEADY;
    }

    /** Smoothed samples from the last {@code windowMs}, oldest first. */
    public float[] recent(long nowMs, long windowMs) {
        int count = 0;
        for (double[] sample : history) if (nowMs - sample[0] <= windowMs) count++;
        float[] values = new float[count];
        int i = 0;
        for (double[] sample : history) if (nowMs - sample[0] <= windowMs) values[i++] = (float) sample[1];
        return values;
    }

    public void clear() {
        history.clear();
        smoothed = Double.NaN;
    }
}
