package com.feloguarin.lensguard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlertDialog;
import android.content.DialogInterface;
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
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowDialog;

/** Exercises real activity setup, navigation, checklist state, saved state and background teardown. */
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
        assertTextPresent("Smoke detectors & ceiling fixtures");
        click("Open camera");
        assertTextPresent("Camera is off");
        assertTextPresent("OFF · No camera frames being analyzed");
        assertFalse(findButton(content(), "Save photo").isEnabled());
        assertFalse(findButton(content(), "Turn light on").isEnabled());
        assertFalse(findButton(content(), "Compare light on/off").isEnabled());

        click("Nearby");
        assertTextPresent("Check nearby signals.");
        assertTextPresent("READY · Tap Start scan");
        assertFalse(findButton(content(), "Stop scan").isEnabled());
        assertFalse(findButton(content(), "Save scan to report").isEnabled());

        click("Sensors");
        click("Open sensor readings");
        assertTextPresent("Know your hardware.");
        assertTextPresent("Live sensor inventory");
        click("All sensor tools");
        click("Open magnetic check");
        assertTextPresent("1. Set a reference");
        assertTextPresent("2. Compare near an object");

        click("Report");
        assertTextPresent("Keep your observations.");
        assertTextPresent("Nothing saved yet.");
        assertNotNull("Notes editor must be available", findEditor(content()));

        click("Start");
        assertTextPresent("Inspect your space.");
    }

    @Test
    public void checklistPlaceKeepsItsStatusAndLinkedNote() {
        clickText("Smoke detectors & ceiling fixtures");
        assertTextPresent("Status: Not inspected");
        click("Needs a closer look");
        assertTextPresent("Status: Closer look");
        EditText note = findEditor(content());
        assertNotNull(note);
        note.setText("Small dark opening facing the bed.");
        click("Save note");
        assertTextPresent("1 SAVED OBSERVATION");
        assertTextPresent("Small dark opening facing the bed.");

        click("All places");
        assertTextPresent("1 of 12 places inspected");
        assertTextPresent("1 needs a closer look");
        assertTextPresent("1 observation");
        Inspection stored = new InspectionStore(RuntimeEnvironment.getApplication()).current();
        assertEquals(Inspection.CLOSER_LOOK, stored.status("smoke_detector"));
        assertEquals("smoke_detector", stored.observations.get(0).spot);

        clickText("Smoke detectors & ceiling fixtures");
        click("Clear status");
        assertTextPresent("Status: Not inspected");
    }

    @Test
    public void toolsOpenedFromAPlaceShowWhereObservationsWillBeLinked() {
        clickText("Picture frames, mirrors & wall decor");
        click("Compare magnetic field");
        assertTextPresent("Compare magnetic field.");
        assertTextPresent("Inspecting: Picture frames, mirrors & wall decor");
        click("Camera");
        assertTextPresent("Inspecting: Picture frames, mirrors & wall decor");
        click("Done with place");
        assertFalse(hasText(content(), "Inspecting:"));
        assertNull(controller.get().spot);
    }

    @Test
    public void notesDraftsAndSelectedTabSurviveActivityRecreation() {
        click("Report");
        EditText editor = findEditor(content());
        assertNotNull(editor);
        String draft = "Corner shelf; small reflection, still unconfirmed.";
        editor.setText(draft);
        MainActivity previous = controller.get();

        controller.recreate();
        drainImmediateWork();

        assertNotSame("Recreation must build a new activity", previous, controller.get());
        assertTextPresent("Keep your observations.");
        EditText restored = findEditor(content());
        assertNotNull(restored);
        assertEquals(draft, restored.getText().toString());

        click("Save note");
        assertTextPresent("1 observation");
        assertEquals("", findEditor(content()).getText().toString());
        controller.recreate();
        drainImmediateWork();
        assertTextPresent(draft);
    }

    @Test
    public void pausingStopsSensorUpdatesAndResumingKeepsControlsWorking() {
        click("Sensors");
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
        controller.pause();
        controller.resume().postResume();
        drainImmediateWork();
        // Views built before the pause must still respond.
        click("Zoom & exposure options");
        assertTextPresent("Exposure · neutral");
    }

    @Test
    public void optionalToolsHaveExplicitStepsAndDoNotStartMicrophone() {
        click("Sensors");
        click("Open magnetic check");
        assertTextPresent("1. Set a reference");
        assertTextPresent("Where it helps");
        click("All sensor tools");
        click("Open sound check");
        assertTextPresent("Microphone off.");
        assertNotNull(findButton(content(), "Enable microphone"));
        assertFalse(findButton(content(), "Save reading").isEnabled());
    }

    @Test
    public void reportStartsANewInspectionAndCanSwitchBack() {
        click("Report");
        click("Start a new inspection");
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        EditText name = findEditor(dialog.getWindow().getDecorView());
        name.setText("Hotel room 412");
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        drainImmediateWork();
        assertTextPresent("Hotel room 412");
        click("Report");
        assertTextPresent("Current");
        click("Open");
        assertTextPresent("Inspection · ");
        assertEquals(2, new InspectionStore(RuntimeEnvironment.getApplication()).all().size());
    }

    @Test
    public void leadsAreListedFirstAndABluetoothSignalCanBeFollowed() {
        java.util.List<WirelessProbe.Device> none = new java.util.ArrayList<>();
        java.util.List<WirelessProbe.Device> ble = new java.util.ArrayList<>();
        ble.add(new WirelessProbe.Device(WirelessProbe.BLE, "AA:BB:CC:DD:EE:01", "SpyCam Mini", "AA:BB:CC:DD:EE:01",
                null, -61, 0, android.os.SystemClock.elapsedRealtime(), true, false,
                java.util.Collections.singletonList(WirelessProbe.HINT_CAMERA_NAME)));
        controller.get().survey = new WirelessProbe.Survey(WirelessProbe.COMPLETE, 0, "Wi-Fi status", "Bluetooth status",
                "Network status", none, ble, none);
        click("Nearby");
        assertTextPresent("1 lead to inspect");
        assertTextPresent("Camera-like name");
        assertTextPresent("SpyCam Mini");
        assertTrue(findButton(content(), "Save scan to report").isEnabled());
        click("Save scan to report");
        assertEquals(1, controller.get().inspection().observations.size());

        click("Follow signal");
        assertTextPresent("Follow a signal.");
        assertTextPresent("SpyCam Mini");
        assertTextPresent("How to use it");
        assertNotNull(findButton(content(), "Save to report"));
        assertFalse(findButton(content(), "Save to report").isEnabled());
        click("All scan results");
        assertTextPresent("Check nearby signals.");
        assertFalse(controller.get().follower.isRunning());
    }

    @Test
    @Config(qualifiers = "es")
    public void spanishLocaleShowsTranslatedScreens() {
        assertTextPresent("Inspecciona tu espacio.");
        assertTextPresent("Detectores de humo y accesorios del techo");
        click("Cámara");
        assertTextPresent("Busca un lente.");
        assertTextPresent("APAGADA · No se analizan imágenes de la cámara");
        click("Cerca");
        assertTextPresent("Revisa las señales cercanas.");
        click("Sensores");
        assertTextPresent("Herramientas de sensores opcionales.");
        click("Informe");
        assertTextPresent("Guarda tus observaciones.");
    }

    private void click(String label) {
        Button button = findButton(content(), label);
        assertNotNull("Missing button: " + label, button);
        assertTrue("Button did not accept click: " + label, button.performClick());
        drainImmediateWork();
    }

    /** Clicks the nearest clickable container of a visible text, such as a checklist row. */
    private void clickText(String value) {
        View view = findText(content(), value);
        assertNotNull("Missing text: " + value, view);
        while (view != null && !view.isClickable()) view = (View) view.getParent();
        assertNotNull("No clickable parent for: " + value, view);
        assertTrue(view.performClick());
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
        if (view.getVisibility() != View.VISIBLE) return null;
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

    private static View findText(View view, String value) {
        if (view.getVisibility() != View.VISIBLE) return null;
        if (view instanceof TextView && value.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View result = findText(group.getChildAt(i), value);
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
