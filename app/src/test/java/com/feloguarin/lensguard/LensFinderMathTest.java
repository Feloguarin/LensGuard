package com.feloguarin.lensguard;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/** Geometry, tracking and light comparison behind the 2.0 lens finder. */
public class LensFinderMathTest {
    @Test public void rotationFollowsCameraXClockwiseConvention() {
        // A point near the buffer's top-left corner, as a landscape sensor delivers it.
        assertPoint(0.9f, 0.1f, FrameGeometry.upright(0.1f, 0.1f, 90, false));
        assertPoint(0.9f, 0.9f, FrameGeometry.upright(0.1f, 0.1f, 180, false));
        assertPoint(0.1f, 0.9f, FrameGeometry.upright(0.1f, 0.1f, 270, false));
        assertPoint(0.1f, 0.1f, FrameGeometry.upright(0.1f, 0.1f, 0, false));
        assertPoint(0.9f, 0.2f, FrameGeometry.upright(0.2f, 0.1f, -270, false));
    }

    @Test public void frontCameraPreviewIsMirroredAfterRotation() {
        assertPoint(0.1f, 0.1f, FrameGeometry.upright(0.1f, 0.1f, 90, true));
        assertPoint(0.75f, 0.5f, FrameGeometry.upright(0.25f, 0.5f, 0, true));
    }

    @Test public void uprightAspectSwapsForQuarterTurns() {
        assertEquals(0.75f, FrameGeometry.uprightAspect(1280, 960, 90), 1e-6);
        assertEquals(4f / 3, FrameGeometry.uprightAspect(1280, 960, 180), 1e-6);
        assertEquals(1f, FrameGeometry.uprightAspect(0, 960, 90), 0);
    }

    @Test public void fillCenterCropsTheWiderDimension() {
        // Same aspect: identity.
        assertPoint(0.3f, 0.6f, FrameGeometry.fillCenter(0.3f, 0.6f, 0.75f, 0.75f));
        // A 3:4 frame in a square view loses a band at the top and bottom.
        assertPoint(0.5f, 0.5f, FrameGeometry.fillCenter(0.5f, 0.5f, 0.75f, 1f));
        assertNull(FrameGeometry.fillCenter(0.5f, 0.05f, 0.75f, 1f));
        assertPoint(0.5f, 0f, FrameGeometry.fillCenter(0.5f, 0.125f, 0.75f, 1f));
        // A wide frame in a portrait view loses its sides.
        assertNull(FrameGeometry.fillCenter(0.05f, 0.5f, 4f / 3, 0.75f));
        assertNull(FrameGeometry.fillCenter(0.5f, 0.5f, Float.NaN, 1f));
    }

    @Test public void trackerMarksAHighlightSteadyAfterRepeatedDetections() {
        HighlightTracker tracker = new HighlightTracker();
        List<HighlightTracker.Track> tracks = tracker.update(points(0.40f, 0.50f), 0.75f);
        assertEquals(1, tracks.size());
        assertFalse(tracks.get(0).steady);
        tracker.update(points(0.41f, 0.505f), 0.75f);
        tracks = tracker.update(points(0.405f, 0.50f), 0.75f);
        assertEquals(1, tracks.size());
        assertTrue(tracks.get(0).steady);
        assertEquals(1, tracker.steadyCount());
        assertEquals(0.405f, tracks.get(0).x, 0.01f);
    }

    @Test public void trackerSeparatesDistantPointsAndForgetsVanishedOnes() {
        HighlightTracker tracker = new HighlightTracker();
        tracker.update(points(0.2f, 0.2f, 0.8f, 0.8f), 0.75f);
        assertEquals(2, tracker.visible().size());
        tracker.update(points(0.2f, 0.2f), 0.75f);
        assertEquals(1, tracker.visible().size());
        tracker.update(Collections.emptyList(), 0.75f);
        tracker.update(Collections.emptyList(), 0.75f);
        tracker.update(Collections.emptyList(), 0.75f);
        assertTrue(tracker.visible().isEmpty());
        // A point returning after the track expired starts fresh, not steady.
        List<HighlightTracker.Track> tracks = tracker.update(points(0.2f, 0.2f), 0.75f);
        assertFalse(tracks.get(0).steady);
    }

    @Test public void steadyTrackSurvivesOneMissedFrameWithoutFlicker() {
        HighlightTracker tracker = new HighlightTracker();
        for (int i = 0; i < 3; i++) tracker.update(points(0.5f, 0.5f), 1f);
        assertEquals(1, tracker.update(Collections.emptyList(), 1f).size());
        assertEquals(0, tracker.update(Collections.emptyList(), 1f).size());
    }

    @Test public void trackerUsesAspectSoHorizontalDistanceIsNotUnderstated() {
        HighlightTracker tracker = new HighlightTracker(0.045f, 3, 2);
        int first = tracker.update(points(0.50f, 0.5f), 4f).get(0).id;
        // 0.02 of a wide frame's width is 0.08 of its height: too far to be the same point.
        assertNotEquals(first, tracker.update(points(0.52f, 0.5f), 4f).get(0).id);
        tracker.reset();
        first = tracker.update(points(0.50f, 0.5f), 0.5f).get(0).id;
        assertEquals(first, tracker.update(points(0.52f, 0.5f), 0.5f).get(0).id);
    }

    @Test public void comparisonSeparatesReflectionsFromLightSources() {
        LightComparison comparison = new LightComparison(0.75f);
        for (int i = 0; i < 3; i++) comparison.addLightOn(points(0.30f, 0.30f, 0.70f, 0.60f));
        for (int i = 0; i < 3; i++) comparison.addLightOff(points(0.705f, 0.598f));
        LightComparison.Result result = comparison.result(false);
        assertEquals(1, result.reflections);
        assertEquals(1, result.lightSources);
        assertFalse(result.moved);
        for (LightComparison.Finding finding : result.findings) {
            if (finding.kind == LightComparison.Kind.REFLECTS_PHONE_LIGHT) assertEquals(0.30f, finding.x, 0.01f);
            else assertEquals(0.70f, finding.x, 0.01f);
        }
    }

    @Test public void comparisonIgnoresOneFrameFlickerAndReportsLightOnlyWhenOff() {
        LightComparison comparison = new LightComparison(1f);
        comparison.addLightOn(points(0.5f, 0.5f));
        comparison.addLightOn(Collections.emptyList());
        comparison.addLightOn(Collections.emptyList());
        comparison.addLightOff(points(0.2f, 0.8f));
        comparison.addLightOff(points(0.21f, 0.8f));
        comparison.addLightOff(Collections.emptyList());
        LightComparison.Result result = comparison.result(true);
        assertEquals(0, result.reflections);
        assertEquals(1, result.lightSources);
        assertTrue(result.moved);
        assertTrue(new LightComparison(1f).result(false).findings.isEmpty());
    }

    @Test public void twoPointsInOneFrameNeverMergeIntoOneStablePosition() {
        List<List<float[]>> frames = new ArrayList<>();
        frames.add(points(0.50f, 0.50f, 0.52f, 0.50f));
        frames.add(points(0.50f, 0.50f, 0.52f, 0.50f));
        assertEquals(2, LightComparison.stable(frames, 1f, 0.05f, 2).size());
    }

    private static List<float[]> points(float... coordinates) {
        List<float[]> list = new ArrayList<>();
        for (int i = 0; i + 1 < coordinates.length; i += 2) list.add(new float[] {coordinates[i], coordinates[i + 1]});
        return list;
    }

    private static void assertPoint(float x, float y, float[] actual) {
        assertNotNull("Point should be visible", actual);
        assertEquals(Arrays.toString(actual), x, actual[0], 1e-5);
        assertEquals(Arrays.toString(actual), y, actual[1], 1e-5);
    }
}
