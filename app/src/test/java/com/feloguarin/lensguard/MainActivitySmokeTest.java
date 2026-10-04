package com.feloguarin.lensguard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.time.Duration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

/** Exercises real activity setup, navigation, saved state, and background teardown. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
@LooperMode(LooperMode.Mode.PAUSED)
public class MainActivitySmokeTest {
    private ActivityController<MainActivity> controller;

    @Before
    public void launch() {
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        drainImmediateWork();
    }

    @After
    public void destroy() {
        if (controller != null) controller.close();
        drainImmediateWork();
    }

    @Test
    public void launchesAndNavigatesWithoutStartingCameraOrRadioSurvey() {
        assertTextPresent("Inspect your space.");
        click("Open camera");
        assertTextPresent("Camera is off");
        assertTextPresent("OFF · No camera frames being analyzed");
        assertTrue(!findButton(content(), "Save photo").isEnabled());
        assertTrue(!findButton(content(), "Turn light on").isEnabled());

        click("Nearby");
        assertTextPresent("Check nearby signals.");
        assertTextPresent("READY · Tap Start scan");
        assertTrue(!findButton(content(), "Stop scan").isEnabled());

        click("Tools");
        click("Open sensor readings");
        assertTextPresent("Know your hardware.");
        assertTextPresent("LIVE SENSOR INVENTORY");

        click("All tools");
        click("Open notes");
        assertTextPresent("Keep your observations.");
        assertNotNull("Notes editor must be available", findEditor(content()));

        click("Start");
        assertTextPresent("Inspect your space.");
    }

    @Test
    public void notesAndSelectedTabSurviveActivityRecreation() {
        click("Start");
        click("Open notes");
        EditText editor = findEditor(content());
        assertNotNull(editor);
        String observation = "Corner shelf; small reflection, still unconfirmed.";
        editor.setText(observation);
        MainActivity previous = controller.get();

        controller.recreate();
        drainImmediateWork();

        assertNotSame("Recreation must build a new activity", previous, controller.get());
        assertTextPresent("Keep your observations.");
        EditText restored = findEditor(content());
        assertNotNull(restored);
        assertEquals(observation, restored.getText().toString());

        click("Camera");
        click("Start");
        click("Open notes");
        assertEquals(observation, findEditor(content()).getText().toString());
    }

    @Test
    public void pausingStopsSensorUpdatesAndResumingRestoresNavigation() {
        click("Tools");
        click("Open sensor readings");
        controller.pause();
        // Advance a bounded interval across several ticker periods. Never run all future tasks.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertTextPresent("Sensors paused");

        controller.resume().postResume();
        drainImmediateWork();
        click("Nearby");
        assertTextPresent("READY · Tap Start scan");
        click("Camera");
        assertTextPresent("Look for a lens.");
    }

    @Test
    public void optionalToolsHaveExplicitStepsAndDoNotStartMicrophone() {
        click("Tools");
        click("Open magnetic check");
        assertTextPresent("1. Set a reference");
        assertTextPresent("2. Compare near an object");
        click("All tools");
        click("Open sound check");
        assertTextPresent("Microphone off.");
        assertNotNull(findButton(content(), "Enable microphone"));
    }

    private void click(String label) {
        Button button = findButton(content(), label);
        assertNotNull("Missing navigation button: " + label, button);
        assertTrue("Button did not accept click: " + label, button.performClick());
        drainImmediateWork();
    }

    private void assertTextPresent(String value) {
        assertTrue("Missing visible text: " + value, hasText(content(), value));
    }

    private View content() {
        return controller.get().findViewById(android.R.id.content);
    }

    private static void drainImmediateWork() {
        // idle() drains only work due now, leaving SensorMonitor's repeating future ticker alone.
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static Button findButton(View view, String label) {
        if (view instanceof Button && label.contentEquals(((Button) view).getText())) {
            return (Button) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button result = findButton(group.getChildAt(i), label);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static EditText findEditor(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText result = findEditor(group.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }

    private static boolean hasText(View view, String value) {
        if (view.getVisibility() != View.VISIBLE) return false;
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(value)) {
            return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasText(group.getChildAt(i), value)) return true;
            }
        }
        return false;
    }
}
