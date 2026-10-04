package com.feloguarin.lensguard;

import java.util.Arrays;

/** Pure numerical helpers. These measure physical clues; none identifies a camera. */
public final class DetectionMath {
    private DetectionMath() {}

    public static double magnitude(float[] values) {
        if (values == null || values.length < 3) return Double.NaN;
        return Math.sqrt((double) values[0] * values[0]
                + (double) values[1] * values[1] + (double) values[2] * values[2]);
    }

    public static double vectorDistance(float[] first, float[] second) {
        if (first == null || second == null || first.length < 3 || second.length < 3) {
            return Double.NaN;
        }
        double x = first[0] - second[0], y = first[1] - second[1], z = first[2] - second[2];
        return Math.sqrt(x * x + y * y + z * z);
    }

    public static boolean stationary(double accelerationMagnitude, double accelerationChange,
                                     double gyroscopeMagnitude, boolean gyroscopeAvailable) {
        return Double.isFinite(accelerationMagnitude) && Double.isFinite(accelerationChange)
                && Math.abs(accelerationMagnitude - 9.80665) < 0.65
                && accelerationChange < 0.25
                && (!gyroscopeAvailable || (Double.isFinite(gyroscopeMagnitude)
                && gyroscopeMagnitude < 0.08));
    }

    /** Welford statistics for a consecutive stationary window, reset by any bad sample. */
    public static final class CalibrationWindow {
        private final long requiredNanos;
        private final int minimumSamples;
        private final double maximumStandardDeviation;
        private long firstNanos;
        private long lastNanos;
        private int count;
        private double mean;
        private double m2;

        public CalibrationWindow(long requiredNanos, int minimumSamples,
                                 double maximumStandardDeviation) {
            if (requiredNanos <= 0 || minimumSamples < 2 || maximumStandardDeviation <= 0) {
                throw new IllegalArgumentException("Invalid calibration window");
            }
            this.requiredNanos = requiredNanos;
            this.minimumSamples = minimumSamples;
            this.maximumStandardDeviation = maximumStandardDeviation;
        }

        public void reset() {
            firstNanos = 0;
            lastNanos = 0;
            count = 0;
            mean = 0;
            m2 = 0;
        }

        public boolean add(double value, long timestampNanos, boolean isStationary) {
            if (!isStationary || !Double.isFinite(value) || value < 0 || timestampNanos < 0) {
                reset();
                return false;
            }
            // A missing event stream must not count as continuous stationary sampling.
            if (count > 0 && (timestampNanos <= lastNanos
                    || timestampNanos - lastNanos > 500_000_000L)) reset();
            if (count == 0) firstNanos = timestampNanos;
            lastNanos = timestampNanos;
            count++;
            double delta = value - mean;
            mean += delta / count;
            m2 += delta * (value - mean);
            if (count >= minimumSamples && elapsedNanos() >= requiredNanos) {
                if (standardDeviation() <= maximumStandardDeviation) return true;
                reset();
            }
            return false;
        }

        public int sampleCount() { return count; }
        public double mean() { return count == 0 ? Double.NaN : mean; }
        public double standardDeviation() {
            return count < 2 ? 0 : Math.sqrt(Math.max(0, m2 / (count - 1)));
        }
        public long elapsedNanos() { return count < 2 ? 0 : lastNanos - firstNanos; }
        public double progress() {
            return Math.min(1, Math.min((double) elapsedNanos() / requiredNanos,
                    (double) count / minimumSamples));
        }
    }

    public static final class ToneResult {
        public final double frequencyHz;
        public final double toneDbfs;
        public final double peakToFloorDb;
        public final double rmsDbfs;
        public final boolean clipped;
        public final boolean prominent;

        private ToneResult(double frequencyHz, double toneDbfs, double peakToFloorDb,
                           double rmsDbfs, boolean clipped) {
            this.frequencyHz = frequencyHz;
            this.toneDbfs = toneDbfs;
            this.peakToFloorDb = peakToFloorDb;
            this.rmsDbfs = rmsDbfs;
            this.clipped = clipped;
            this.prominent = !clipped && toneDbfs > -60 && peakToFloorDb > 20;
        }
    }

    /** Hann-window FFT of a mono PCM16 frame; the input is never retained. */
    public static ToneResult highFrequencyTone(short[] pcm, int sampleRate) {
        if (pcm == null || pcm.length < 512 || Integer.bitCount(pcm.length) != 1
                || sampleRate < 44_100) {
            throw new IllegalArgumentException("Use a power-of-two PCM frame at >=44.1 kHz");
        }
        int n = pcm.length;
        double[] real = new double[n];
        double[] imaginary = new double[n];
        double mean = 0;
        double energy = 0;
        double windowSum = 0;
        int clippedSamples = 0;
        for (short sample : pcm) {
            double value = sample / 32768.0;
            mean += value;
            energy += value * value;
            if (Math.abs((int) sample) >= 32760) clippedSamples++;
        }
        mean /= n;
        for (int i = 0; i < n; i++) {
            double window = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1));
            real[i] = (pcm[i] / 32768.0 - mean) * window;
            windowSum += window;
        }
        fft(real, imaginary);
        int first = Math.max(1, (int) Math.ceil(15_000.0 * n / sampleRate));
        int last = Math.min(n / 2 - 1, (int) Math.floor(22_000.0 * n / sampleRate));
        double[] band = new double[last - first + 1];
        double maximumPower = 0;
        int peakBin = first;
        for (int bin = first; bin <= last; bin++) {
            double power = real[bin] * real[bin] + imaginary[bin] * imaginary[bin];
            band[bin - first] = power;
            if (power > maximumPower) {
                maximumPower = power;
                peakBin = bin;
            }
        }
        Arrays.sort(band);
        double medianPower = band[band.length / 2];
        double amplitude = 2 * Math.sqrt(maximumPower) / windowSum;
        return new ToneResult((double) peakBin * sampleRate / n,
                20 * Math.log10(Math.max(1e-12, amplitude)),
                10 * Math.log10(Math.max(1e-24, maximumPower)
                        / Math.max(1e-24, medianPower)),
                10 * Math.log10(Math.max(1e-24, energy / n)),
                clippedSamples > n / 100);
    }

    private static void fft(double[] real, double[] imaginary) {
        int n = real.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            while ((j & bit) != 0) { j ^= bit; bit >>= 1; }
            j ^= bit;
            if (i < j) {
                double value = real[i]; real[i] = real[j]; real[j] = value;
            }
        }
        for (int size = 2; size <= n; size <<= 1) {
            double angle = -2 * Math.PI / size;
            double stepReal = Math.cos(angle), stepImaginary = Math.sin(angle);
            for (int offset = 0; offset < n; offset += size) {
                double wr = 1, wi = 0;
                for (int k = 0; k < size / 2; k++) {
                    int even = offset + k, odd = even + size / 2;
                    double tr = wr * real[odd] - wi * imaginary[odd];
                    double ti = wr * imaginary[odd] + wi * real[odd];
                    real[odd] = real[even] - tr;
                    imaginary[odd] = imaginary[even] - ti;
                    real[even] += tr;
                    imaginary[even] += ti;
                    double nextWr = wr * stepReal - wi * stepImaginary;
                    wi = wr * stepImaginary + wi * stepReal;
                    wr = nextWr;
                }
            }
        }
    }
}
