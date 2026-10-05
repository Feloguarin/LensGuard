package com.feloguarin.lensguard;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Private inspection storage, 1.x migration and report export. */
@RunWith(RobolectricTestRunner.class)
public class InspectionStoreTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
    }

    @Test public void inspectionsRoundTripAndTheCurrentOneIsRemembered() {
        InspectionStore store = new InspectionStore(context);
        Inspection first = store.current();
        assertTrue(first.title.startsWith("Inspection · "));
        first.setStatus("smoke_detector", Inspection.CLOSER_LOOK);
        Observation note = new Observation(Observation.NOTE, 1_000);
        note.note = "Shelf corner: reflection from two angles.";
        note.spot = "shelves_decor";
        first.observations.add(note);
        assertTrue(store.save(first));

        Inspection second = store.create("Hotel room 412");
        assertEquals(2, store.all().size());
        assertEquals("Hotel room 412", new InspectionStore(context).current().title);

        store.select(first);
        Inspection reloaded = new InspectionStore(context).current();
        assertEquals(first.id, reloaded.id);
        assertEquals(Inspection.CLOSER_LOOK, reloaded.status("smoke_detector"));
        assertEquals(1, reloaded.forSpot("shelves_decor").size());
        assertEquals(note.note, reloaded.observations.get(0).note);
        assertNotNull(reloaded.find(note.id));

        assertTrue(store.delete(second));
        assertEquals(1, store.all().size());
    }

    @Test public void deletingTheCurrentInspectionFallsBackToAnotherOne() {
        InspectionStore store = new InspectionStore(context);
        Inspection older = store.create("Older");
        Inspection newer = store.create("Newer");
        store.delete(newer);
        assertEquals(older.id, store.current().id);
        store.delete(older);
        assertNotEquals(older.id, store.current().id);
    }

    @Test public void damagedFilesAndUnsafeNamesAreIgnored() throws Exception {
        File folder = new File(context.getFilesDir(), "inspections");
        assertTrue(folder.mkdirs() || folder.isDirectory());
        try (FileOutputStream out = new FileOutputStream(new File(folder, "broken.json"))) {
            out.write("{not json".getBytes(StandardCharsets.UTF_8));
        }
        InspectionStore store = new InspectionStore(context);
        assertTrue(store.all().isEmpty());
        assertNull(store.photoFile("../secret.jpg"));
        assertNull(store.photoFile(".hidden"));
        assertNull(store.photoFile(null));
        assertEquals("inspection-1.jpg", store.photoFile("inspection-1.jpg").getName());
    }

    @Test public void legacyNoteAndPhotosMoveIntoAnInspectionOnce() throws Exception {
        SharedPreferences legacy = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE);
        legacy.edit().putString("note", "Lamp base looked odd.").commit();
        File evidence = new File(context.getFilesDir(), "evidence");
        assertTrue(evidence.mkdirs() || evidence.isDirectory());
        File photo = new File(evidence, "inspection-1700000000000.jpg");
        try (FileOutputStream out = new FileOutputStream(photo)) {
            out.write(new byte[] {1, 2, 3});
        }
        InspectionStore store = new InspectionStore(context);
        assertTrue(store.migrateLegacy(legacy));
        Inspection imported = store.current();
        assertEquals("Imported from LensGuard 1.x", imported.title);
        assertEquals(2, imported.observations.size());
        assertEquals(photo.getName(), imported.observations.get(0).photo);
        assertEquals("Lamp base looked odd.", imported.observations.get(1).note);
        assertFalse(legacy.contains("note"));
        assertFalse(new InspectionStore(context).migrateLegacy(legacy));
        assertEquals(1, store.all().size());
    }

    @Test public void freshInstallMarksMigrationDoneWithoutCreatingAnything() {
        SharedPreferences legacy = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE);
        InspectionStore store = new InspectionStore(context);
        assertFalse(store.migrateLegacy(legacy));
        assertTrue(store.all().isEmpty());
    }

    @Test public void deleteAllRemovesInspectionsPhotosAndReportCopies() throws Exception {
        InspectionStore store = new InspectionStore(context);
        Inspection inspection = store.current();
        File photo = store.newPhotoFile();
        try (FileOutputStream out = new FileOutputStream(photo)) {
            out.write(new byte[] {9});
        }
        Observation observation = new Observation(Observation.CAMERA, 5);
        observation.photo = photo.getName();
        inspection.observations.add(observation);
        store.save(inspection);
        File report = ReportBuilder.writeJson(context, inspection, false);
        assertTrue(report.exists());
        assertTrue(store.deleteAll());
        assertFalse(photo.exists());
        assertFalse(report.exists());
        assertTrue(store.all().isEmpty());
    }

    @Test public void reportMasksAddressesUnlessIncluded() throws Exception {
        Inspection inspection = new Inspection("Test room", 0);
        inspection.setStatus("vents", Inspection.INSPECTED);
        Observation scan = new Observation(Observation.NEARBY, 10);
        JSONObject device = new JSONObject().put("kind", "ble").put("name", "SpyCam")
                .put("address", "AA:BB:CC:DD:EE:FF").put("hints", new JSONArray().put("camera_name"));
        scan.data = new JSONObject().put("bluetooth", new JSONArray().put(device))
                .put("network", new JSONArray().put(new JSONObject().put("address", "192.168.1.20")));
        inspection.observations.add(scan);

        JSONObject masked = ReportBuilder.json(context, inspection, false);
        assertEquals("lensguard-report", masked.getString("format"));
        assertEquals(2, masked.getInt("formatVersion"));
        assertEquals("masked", masked.getString("addresses"));
        assertTrue(masked.getString("limitation").contains("No measurement confirms a camera"));
        JSONObject body = masked.getJSONObject("inspection");
        assertEquals(Checklist.SPOTS.length, body.getJSONArray("checklist").length());
        JSONObject vents = null;
        for (int i = 0; i < Checklist.SPOTS.length; i++) {
            JSONObject spot = body.getJSONArray("checklist").getJSONObject(i);
            if (spot.getString("id").equals("vents")) vents = spot;
            else assertEquals("not_inspected", spot.getString("status"));
        }
        assertNotNull(vents);
        assertEquals("inspected", vents.getString("status"));
        JSONObject data = body.getJSONArray("observations").getJSONObject(0).getJSONObject("data");
        assertEquals("AA:BB:CC:••:••:••", data.getJSONArray("bluetooth").getJSONObject(0).getString("address"));
        assertEquals("192.168.•.•", data.getJSONArray("network").getJSONObject(0).getString("address"));
        // The stored inspection keeps the original values.
        assertEquals("AA:BB:CC:DD:EE:FF", device.getString("address"));

        JSONObject included = ReportBuilder.json(context, inspection, true);
        assertEquals("included", included.getString("addresses"));
        assertEquals("AA:BB:CC:DD:EE:FF", included.getJSONObject("inspection").getJSONArray("observations")
                .getJSONObject(0).getJSONObject("data").getJSONArray("bluetooth").getJSONObject(0).getString("address"));
    }

    @Test public void recentReportCopiesStayForReceivingAppsAndOldOnesAreRemoved() throws Exception {
        File folder = new File(context.getCacheDir(), "reports");
        assertTrue(folder.mkdirs() || folder.isDirectory());
        File old = new File(folder, "LensGuard-report-old.pdf");
        File recent = new File(folder, "LensGuard-report-recent.pdf");
        for (File file : new File[] {old, recent}) {
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write('%');
            }
        }
        assertTrue(old.setLastModified(System.currentTimeMillis() - ReportBuilder.KEEP_SHARED_MS - 60_000));
        File newest = ReportBuilder.writeJson(context, new Inspection("Room", 0), false);
        assertTrue(newest.getName().startsWith("LensGuard-report-"));
        assertTrue(newest.exists());
        assertTrue("A copy shared minutes ago must stay readable", recent.exists());
        assertFalse("Copies older than an hour are removed", old.exists());
    }
}
