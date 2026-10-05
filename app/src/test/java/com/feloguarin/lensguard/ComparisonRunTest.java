package com.feloguarin.lensguard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.os.Handler;
import android.os.Looper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.LooperMode;

/** The timed light on/off sequence, with a fake torch instead of camera hardware. */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class ComparisonRunTest {
    private final List<Boolean> torch = new ArrayList<>();
    private final List<Integer> progress = new ArrayList<>();
    private LightComparison.Result result;
    private boolean timedOut;
    private boolean moving;

    private ComparisonRun run(boolean userTorch) {
        return new ComparisonRun(new Handler(Looper.getMainLooper()), torch::add, () -> moving,
                new ComparisonRun.Listener() {
                    @Override public void onProgress(int step, int steps) { progress.add(step); }
                    @Override public void onDone(LightComparison.Result done) { result = done; }
                    @Override public void onTimeout() { timedOut = true; }
                }, userTorch, 0.75f);
    }

    @Test public void runsLightOnThenOffAndRestoresTheUsersSetting() {
        ComparisonRun run = run(false);
        run.start();
        assertEquals(Collections.singletonList(true), torch);
        // Frames before the light settles are ignored.
        run.offer(points(0.3f, 0.3f));
        assertEquals(Collections.singletonList(0), progress);
        idle(ComparisonRun.SETTLE_MS);
        for (int i = 0; i < 3; i++) run.offer(points(0.3f, 0.3f, 0.7f, 0.6f));
        assertEquals(Arrays.asList(true, false), torch);
        run.offer(points(0.3f, 0.3f)); // Still settling after the light went off.
        idle(ComparisonRun.SETTLE_MS);
        for (int i = 0; i < 3; i++) run.offer(points(0.7f, 0.6f));
        assertNotNull(result);
        assertEquals(1, result.reflections);
        assertEquals(1, result.lightSources);
        assertFalse(result.moved);
        assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5, 6), progress);
        assertEquals(Arrays.asList(true, false, false), torch);
        assertFalse(run.active());
        idle(ComparisonRun.TIMEOUT_MS);
        assertFalse("A finished run must not time out later", timedOut);
    }

    @Test public void restoresTheLightWhenTheUserHadItOn() {
        ComparisonRun run = run(true);
        run.start();
        idle(ComparisonRun.SETTLE_MS);
        for (int i = 0; i < 3; i++) run.offer(points());
        idle(ComparisonRun.SETTLE_MS);
        moving = true;
        for (int i = 0; i < 3; i++) run.offer(points());
        assertTrue(result.moved);
        assertTrue(result.findings.isEmpty());
        assertEquals(Boolean.TRUE, torch.get(torch.size() - 1));
    }

    @Test public void timesOutWhenFramesStopArriving() {
        ComparisonRun run = run(false);
        run.start();
        idle(ComparisonRun.SETTLE_MS);
        run.offer(points(0.5f, 0.5f));
        idle(ComparisonRun.TIMEOUT_MS);
        assertTrue(timedOut);
        assertNull(result);
        assertFalse(run.active());
        assertEquals(Boolean.FALSE, torch.get(torch.size() - 1));
        run.offer(points(0.5f, 0.5f));
        assertNull("Frames after a timeout are ignored", result);
    }

    @Test public void cancellingStopsSilentlyAndOnlyOnce() {
        ComparisonRun run = run(false);
        run.start();
        run.cancel();
        run.cancel();
        idle(ComparisonRun.TIMEOUT_MS);
        assertFalse(timedOut);
        assertNull(result);
        assertEquals(Arrays.asList(true, false), torch);
    }

    private static void idle(long millis) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
    }

    private static List<float[]> points(float... coordinates) {
        List<float[]> list = new ArrayList<>();
        for (int i = 0; i + 1 < coordinates.length; i += 2) list.add(new float[] {coordinates[i], coordinates[i + 1]});
        return list;
    }
}
