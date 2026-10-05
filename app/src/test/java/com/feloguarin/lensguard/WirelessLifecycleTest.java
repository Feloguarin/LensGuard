package com.feloguarin.lensguard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
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
        Context context = RuntimeEnvironment.getApplication();
        List<WirelessProbe.Survey> updates = new ArrayList<>();
        WirelessProbe probe = new WirelessProbe(context, updates::add);
        assertEquals(WirelessProbe.READY, probe.survey().state);
        probe.start();
        assertTrue(probe.isRunning());
        assertEquals(15, probe.survey().remainingSeconds);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5));
        assertEquals(10, probe.survey().remainingSeconds);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10));
        assertFalse(probe.isRunning());
        WirelessProbe.Survey last = updates.get(updates.size() - 1);
        assertEquals(WirelessProbe.COMPLETE, last.state);
        assertFalse(last.running());
        assertNotEquals(context.getString(R.string.nearby_ble_listening), last.bleStatus);
        assertNotEquals(context.getString(R.string.nearby_network_discovering), last.networkStatus);
        int count = updates.size();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        assertEquals(count, updates.size());
    }

    @Test public void stoppingEarlyCancelsCountdownAndAllowsAnotherSurvey() {
        List<WirelessProbe.Survey> updates = new ArrayList<>();
        WirelessProbe probe = new WirelessProbe(RuntimeEnvironment.getApplication(), updates::add);
        probe.start();
        probe.stop();
        assertFalse(probe.isRunning());
        assertEquals(WirelessProbe.STOPPED, updates.get(updates.size() - 1).state);
        int count = updates.size();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16));
        assertEquals(count, updates.size());
        probe.start();
        assertEquals(15, probe.survey().remainingSeconds);
        assertTrue(probe.survey().leads().isEmpty());
        probe.stop();
    }

    @Test public void surveyExportsStructuredResults() throws Exception {
        WirelessProbe probe = new WirelessProbe(RuntimeEnvironment.getApplication(), survey -> { });
        probe.start();
        probe.stop();
        org.json.JSONObject json = probe.survey().toJson();
        assertEquals("stopped", json.getString("state"));
        assertEquals(0, json.getJSONArray("bluetooth").length());
        assertTrue(json.has("networkStatus"));
    }
}
