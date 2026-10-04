package com.feloguarin.lensguard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
@LooperMode(LooperMode.Mode.PAUSED)
public class WirelessLifecycleTest {
    @Test public void countdownFinishesAndDoesNotPublishAfterStop() {
        List<String> updates = new ArrayList<>();
        WirelessProbe probe = new WirelessProbe(RuntimeEnvironment.getApplication(), updates::add);
        assertTrue(probe.summary().startsWith("READY"));
        probe.start();
        assertTrue(probe.isRunning());
        assertTrue(probe.summary().contains("15 seconds left"));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5));
        assertTrue(probe.summary().contains("10 seconds left"));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10));
        assertFalse(probe.isRunning());
        assertTrue(updates.get(updates.size() - 1).contains("survey complete"));
        assertFalse(updates.get(updates.size() - 1).contains("Discovering advertised"));
        assertFalse(updates.get(updates.size() - 1).contains("Listening for BLE"));
        int count = updates.size();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        assertTrue(updates.size() == count);
    }

    @Test public void stoppingEarlyCancelsCountdownAndAllowsAnotherSurvey() {
        List<String> updates = new ArrayList<>();
        WirelessProbe probe = new WirelessProbe(RuntimeEnvironment.getApplication(), updates::add);
        probe.start();
        probe.stop();
        assertFalse(probe.isRunning());
        assertTrue(updates.get(updates.size() - 1).contains("Survey stopped"));
        int count = updates.size();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16));
        assertTrue(updates.size() == count);
        probe.start();
        assertTrue(probe.summary().contains("15 seconds left"));
        probe.stop();
    }
}
