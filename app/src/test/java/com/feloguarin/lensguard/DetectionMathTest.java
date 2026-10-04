package com.feloguarin.lensguard;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

public class DetectionMathTest {
    @Test public void magnitudeUsesAllAxesAndRejectsShortVectors() {
        assertEquals(13, DetectionMath.magnitude(new float[] {3, 4, 12}), 1e-9);
        assertTrue(Double.isNaN(DetectionMath.magnitude(new float[] {1, 2})));
        assertEquals(5, DetectionMath.vectorDistance(new float[] {1, 2, 3},
                new float[] {4, 6, 3}), 1e-9);
    }

    @Test public void stationaryRequiresStableAccelerationAndSmallRotation() {
        assertTrue(DetectionMath.stationary(9.81, 0.05, 0.01, true));
        assertFalse(DetectionMath.stationary(11, 0.05, 0.01, true));
        assertFalse(DetectionMath.stationary(9.81, 0.5, 0.01, true));
        assertFalse(DetectionMath.stationary(9.81, 0.05, 0.2, true));
        assertFalse(DetectionMath.stationary(9.81, 0.05, Double.NaN, true));
        assertTrue(DetectionMath.stationary(9.81, 0.05, Double.NaN, false));
        assertFalse(DetectionMath.stationary(Double.NaN, 0.05, 0.01, true));
    }

    @Test public void baselineRequiresBothConsecutiveTimeAndEnoughSamples() {
        DetectionMath.CalibrationWindow window = new DetectionMath.CalibrationWindow(3_000_000_000L, 30, 2);
        for (int i = 0; i < 30; i++) assertFalse(window.add(45 + (i % 2) * 0.1,
                i * 100_000_000L, true));
        assertTrue(window.add(45, 3_000_000_000L, true));
        assertEquals(45.0484, window.mean(), 0.001);
        assertEquals(1, window.progress(), 0);
        assertTrue(window.standardDeviation() < 0.1);
    }

    @Test public void motionInvalidValuesAndMissingEventsRestartCalibration() {
        DetectionMath.CalibrationWindow window = new DetectionMath.CalibrationWindow(3_000_000_000L, 30, 2);
        for (int i = 0; i < 20; i++) window.add(40, i * 100_000_000L, true);
        assertFalse(window.add(40, 2_000_000_000L, false));
        assertEquals(0, window.sampleCount());
        window.add(40, 2_100_000_000L, true);
        assertFalse(window.add(Double.NaN, 2_200_000_000L, true));
        assertEquals(0, window.sampleCount());
        window.add(40, 2_300_000_000L, true);
        window.add(40, 3_300_000_000L, true);
        assertEquals(1, window.sampleCount());
        assertEquals(0, window.elapsedNanos());
    }

    @Test public void changingMagneticEnvironmentCannotBecomeAStableBaseline() {
        DetectionMath.CalibrationWindow window = new DetectionMath.CalibrationWindow(3_000_000_000L, 30, 2);
        for (int i = 0; i < 31; i++) assertFalse(window.add(i % 2 == 0 ? 20 : 80,
                i * 100_000_000L, true));
        assertEquals(0, window.sampleCount());
    }

    @Test public void tooFewSamplesCannotFinishEvenIfTimeHasElapsed() {
        DetectionMath.CalibrationWindow window = new DetectionMath.CalibrationWindow(3_000_000_000L, 30, 2);
        for (int i = 0; i < 9; i++) assertFalse(window.add(40, i * 400_000_000L, true));
        assertEquals(9, window.sampleCount());
        assertTrue(window.progress() < 1);
    }

    @Test public void fftMeasuresAnInBandToneWithoutInventingCameraEvidence() {
        short[] pcm = sine(18_000, 0.25);
        DetectionMath.ToneResult result = DetectionMath.highFrequencyTone(pcm, 48_000);
        assertEquals(18_000, result.frequencyHz, 48_000.0 / pcm.length);
        assertEquals(-12.04, result.toneDbfs, 0.15);
        assertTrue(result.prominent);
        assertFalse(result.clipped);
    }

    @Test public void silenceLowFrequencySpeechBandAndBroadNoiseAreNotProminentTones() {
        assertFalse(DetectionMath.highFrequencyTone(new short[4096], 48_000).prominent);
        assertFalse(DetectionMath.highFrequencyTone(sine(1000, 0.5), 48_000).prominent);
        Random random = new Random(17);
        short[] noise = new short[4096];
        for (int i = 0; i < noise.length; i++) noise[i] = (short) (random.nextInt(16384) - 8192);
        assertFalse(DetectionMath.highFrequencyTone(noise, 48_000).prominent);
    }

    @Test public void clippedFramesAreFlaggedInsteadOfUsedAsToneClues() {
        short[] pcm = sine(18_000, 2);
        DetectionMath.ToneResult result = DetectionMath.highFrequencyTone(pcm, 48_000);
        assertTrue(result.clipped);
        assertFalse(result.prominent);
    }

    private static short[] sine(double frequencyHz, double amplitude) {
        short[] pcm = new short[4096];
        for (int i = 0; i < pcm.length; i++) {
            double value = 32767 * amplitude * Math.sin(2 * Math.PI * frequencyHz * i / 48_000);
            pcm[i] = (short) Math.max(-32768, Math.min(32767, value));
        }
        return pcm;
    }
}
