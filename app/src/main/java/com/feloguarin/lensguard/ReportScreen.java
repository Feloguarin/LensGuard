package com.feloguarin.lensguard;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Saved observations, notes, report sharing and inspection management. */
final class ReportScreen extends Screen {
    /** Unsaved note text, kept across tab changes and recreation. */
    String draft = "";
    private EditText noteInput;
    private boolean focusNote;
    private boolean exporting;

    ReportScreen(MainActivity app) {
        super(app);
    }

    @Override void build(LinearLayout body) {
        Inspection inspection = app.inspection();
        body.addView(ui.title(string(R.string.report_title)));
        ui.label(body, string(R.string.report_private));
        LinearLayout card = ui.card(body, inspection.title, summary(inspection));
        ui.row(card, ui.primary(string(R.string.report_share_pdf), this::sharePdf),
                ui.button(string(R.string.report_share_data), this::shareJson));
        Button photos = ui.button(string(R.string.report_share_photos), this::sharePhotos);
        Ui.enabled(photos, inspection.photoCount() > 0);
        Button addresses = ui.button(string(app.includeAddresses() ? R.string.report_addresses_included
                : R.string.report_addresses_hidden), () -> {
            app.setIncludeAddresses(!app.includeAddresses());
            app.refresh();
        });
        ui.row(card, photos, addresses);
        card.addView(ui.text(string(R.string.report_addresses_note), 12, Ui.MUTED));

        ui.label(body, string(R.string.report_note_label));
        spotBanner(body);
        noteInput = new EditText(app);
        noteInput.setText(draft);
        noteInput.setTextColor(Ui.TEXT);
        noteInput.setHintTextColor(Ui.MUTED);
        noteInput.setHint(string(R.string.report_note_hint));
        noteInput.setMinLines(3);
        noteInput.setGravity(Gravity.TOP);
        noteInput.setTextSize(15);
        body.addView(noteInput, new LinearLayout.LayoutParams(-1, -2));
        ui.full(body, ui.primary(string(R.string.note_save), this::saveNote));

        List<Observation> observations = inspection.observations;
        ui.label(body, app.getResources().getQuantityString(R.plurals.observations, observations.size(), observations.size()));
        if (observations.isEmpty()) body.addView(ui.text(string(R.string.report_empty), 13, Ui.MUTED));
        for (int i = observations.size() - 1; i >= 0; i--) {
            Observation observation = observations.get(i);
            observation(body, observation, () -> app.confirm(R.string.observation_delete_title,
                    R.string.observation_delete_message, R.string.action_delete, () -> deleteObservation(observation)));
        }

        ui.label(body, string(R.string.report_inspections_label));
        for (Inspection item : app.store.all()) inspectionRow(body, item, item.id.equals(inspection.id));
        ui.action(body, string(R.string.report_new_inspection), this::newInspection);
        ui.action(body, string(R.string.report_delete_all), () -> app.confirm(R.string.report_delete_all_title,
                R.string.report_delete_all_message, R.string.action_delete, app::deleteAllEvidence));
        ui.card(body, string(R.string.report_privacy_title), string(R.string.report_privacy));
        ui.card(body, string(R.string.report_version_title, BuildConfig.VERSION_NAME), string(R.string.report_version));
        if (focusNote) {
            focusNote = false;
            noteInput.requestFocus();
        }
    }

    void focusNote() {
        focusNote = true;
        if (noteInput != null) {
            focusNote = false;
            noteInput.requestFocus();
        }
    }

    private String summary(Inspection inspection) {
        int inspected = inspection.count(Inspection.INSPECTED) + inspection.count(Inspection.CLOSER_LOOK);
        return string(R.string.start_progress, inspected, Checklist.SPOTS.length) + " · "
                + app.getResources().getQuantityString(R.plurals.observations, inspection.observations.size(),
                inspection.observations.size()) + " · "
                + app.getResources().getQuantityString(R.plurals.photos, inspection.photoCount(), inspection.photoCount());
    }

    private void inspectionRow(LinearLayout body, Inspection inspection, boolean current) {
        String when = DateUtils.formatDateTime(app, inspection.updatedAt,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_MONTH);
        LinearLayout card = ui.card(body, inspection.title, string(R.string.report_inspection_detail, when,
                app.getResources().getQuantityString(R.plurals.observations, inspection.observations.size(),
                        inspection.observations.size())));
        Button delete = ui.button(string(R.string.action_delete), () -> app.confirm(R.string.report_delete_inspection_title,
                R.string.report_delete_inspection_message, R.string.action_delete, () -> {
                    app.store.delete(inspection);
                    if (current) app.spot = null;
                    app.thumbnails.clear();
                    app.refresh();
                }));
        if (current) {
            card.addView(ui.chips(ui.chip(string(R.string.report_current), Ui.MINT)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(0, ui.dp(8), 0, 0);
            card.addView(delete, params);
        } else {
            ui.row(card, ui.button(string(R.string.report_open_inspection), () -> {
                app.store.select(inspection);
                app.spot = null;
                app.refresh();
            }), delete);
        }
    }

    private void saveNote() {
        String text = noteInput.getText().toString().trim();
        if (text.isEmpty()) {
            app.toast(string(R.string.note_empty));
            return;
        }
        app.record(Observation.NOTE, null, text, null, null);
        clearDraft();
        app.toast(string(R.string.note_saved));
        app.refresh();
    }

    private void deleteObservation(Observation observation) {
        Inspection inspection = app.inspection();
        inspection.observations.remove(observation);
        File photo = app.store.photoFile(observation.photo);
        if (photo != null && photo.exists() && !photo.delete()) app.toast(string(R.string.evidence_delete_failed));
        app.saveInspection();
        app.refresh();
    }

    private void newInspection() {
        EditText input = new EditText(app);
        input.setText(app.store.defaultTitle());
        input.setSelectAllOnFocus(true);
        input.setSingleLine(true);
        LinearLayout frame = new LinearLayout(app);
        frame.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), 0);
        frame.addView(input, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(app).setTitle(R.string.report_new_inspection_title)
                .setMessage(R.string.report_new_inspection_message).setView(frame)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_create, (d, w) -> {
                    String title = input.getText().toString().trim();
                    app.store.create(title.isEmpty() ? app.store.defaultTitle()
                            : title.length() > 80 ? title.substring(0, 80) : title);
                    app.spot = null;
                    app.showTab(MainActivity.START);
                }).show();
    }

    private void sharePdf() {
        export(true);
    }

    private void shareJson() {
        export(false);
    }

    /** Renders from a copy of the inspection so later edits cannot race the background work. */
    private void export(boolean pdf) {
        if (exporting) return;
        Inspection copy;
        try {
            copy = Inspection.fromJson(app.inspection().toJson());
        } catch (org.json.JSONException unexpected) {
            app.toast(string(R.string.report_failed));
            return;
        }
        boolean addresses = app.includeAddresses();
        exporting = true;
        app.toast(string(R.string.report_preparing));
        app.inBackground(() -> {
            File file;
            try {
                file = pdf ? ReportPdf.write(app, copy, addresses, app.store) : ReportBuilder.writeJson(app, copy, addresses);
            } catch (Exception failure) {
                file = null;
            }
            final File result = file;
            app.onMain(() -> {
                exporting = false;
                if (result == null) app.toast(string(R.string.report_failed));
                else app.share(result, pdf ? "application/pdf" : "application/json");
            });
        });
    }

    private void sharePhotos() {
        List<File> files = new ArrayList<>();
        for (Observation observation : app.inspection().observations) {
            File photo = app.store.photoFile(observation.photo);
            if (photo != null && photo.exists()) files.add(photo);
        }
        if (files.isEmpty()) app.toast(string(R.string.report_no_photos));
        else app.shareAll(files, "image/jpeg");
    }

    private void keepDraft() {
        if (noteInput != null) draft = noteInput.getText().toString();
    }

    void clearDraft() {
        draft = "";
        if (noteInput != null) noteInput.setText("");
    }

    @Override void pause() {
        keepDraft();
    }

    @Override void leave() {
        keepDraft();
        noteInput = null;
    }

    @Override void save(Bundle state) {
        keepDraft();
        state.putString("report_draft", draft);
    }

    @Override void restore(Bundle state) {
        draft = state.getString("report_draft", "");
    }
}
