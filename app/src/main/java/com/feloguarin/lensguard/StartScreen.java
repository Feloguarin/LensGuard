package com.feloguarin.lensguard;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** The current inspection and a checklist of places to examine. */
final class StartScreen extends Screen {
    /** The checklist spot whose details are open, or null for the overview. */
    String openSpot;
    private EditText spotNote;
    private String spotDraft = "";
    private String spotDraftFor;

    StartScreen(MainActivity app) {
        super(app);
    }

    @Override void build(LinearLayout body) {
        spotNote = null;
        Checklist.Spot selected = Checklist.find(openSpot);
        if (selected != null) {
            buildSpot(body, selected);
            return;
        }
        openSpot = null;
        Inspection inspection = app.inspection();
        body.addView(ui.title(string(R.string.start_title)));
        body.addView(ui.text(string(R.string.start_intro), 15, Ui.MUTED));
        ui.label(body, string(R.string.start_current_label));
        LinearLayout card = ui.card(body, inspection.title, progress(inspection));
        ui.row(card, ui.primary(string(R.string.start_open_camera), () -> app.showTab(MainActivity.CAMERA)),
                ui.button(string(R.string.start_scan_nearby), () -> app.showTab(MainActivity.NEARBY)));
        ui.row(card, ui.button(string(R.string.start_rename), this::rename),
                ui.button(string(R.string.start_open_report), () -> app.showTab(MainActivity.REPORT)));
        ui.label(body, string(R.string.start_where_label));
        body.addView(ui.text(string(R.string.start_where_intro), 13, Ui.MUTED));
        for (Checklist.Spot spot : Checklist.SPOTS) spotRow(body, spot, inspection.status(spot.id));
        ui.card(body, string(R.string.start_limits_title), string(R.string.start_limits));
    }

    private String progress(Inspection inspection) {
        int inspected = inspection.count(Inspection.INSPECTED) + inspection.count(Inspection.CLOSER_LOOK);
        int closer = inspection.count(Inspection.CLOSER_LOOK);
        int observations = inspection.observations.size();
        StringBuilder text = new StringBuilder(string(R.string.start_progress, inspected, Checklist.SPOTS.length));
        if (closer > 0) text.append(" · ").append(app.getResources().getQuantityString(R.plurals.start_closer, closer, closer));
        text.append(" · ").append(app.getResources().getQuantityString(R.plurals.observations, observations, observations));
        return text.toString();
    }

    private void spotRow(LinearLayout body, Checklist.Spot spot, String status) {
        LinearLayout row = new LinearLayout(app);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(14), ui.dp(12), ui.dp(12), ui.dp(12));
        row.setMinimumHeight(ui.dp(52));
        row.setBackground(ui.surface(Ui.CARD, 14));
        row.setClickable(true);
        row.setFocusable(true);
        TextView title = ui.text(string(spot.title), 14, Ui.TEXT);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView chip = ui.chip(statusLabel(status), statusColor(status));
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(-2, -2);
        chipParams.setMargins(ui.dp(8), 0, 0, 0);
        row.addView(chip, chipParams);
        row.setContentDescription(string(spot.title) + ", " + statusLabel(status));
        row.setOnClickListener(v -> app.openSpot(spot.id));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, ui.dp(6), 0, 0);
        body.addView(row, params);
    }

    String statusLabel(String status) {
        if (Inspection.INSPECTED.equals(status)) return string(R.string.status_inspected);
        if (Inspection.CLOSER_LOOK.equals(status)) return string(R.string.status_closer_look);
        return string(R.string.status_not_inspected);
    }

    static int statusColor(String status) {
        if (Inspection.INSPECTED.equals(status)) return Ui.MINT;
        if (Inspection.CLOSER_LOOK.equals(status)) return Ui.AMBER;
        return Ui.MUTED;
    }

    private void buildSpot(LinearLayout body, Checklist.Spot spot) {
        Inspection inspection = app.inspection();
        ui.action(body, string(R.string.spot_back), () -> {
            openSpot = null;
            app.showTab(MainActivity.START);
        });
        body.addView(ui.title(string(spot.title)));
        body.addView(ui.text(string(spot.hint), 14, Ui.MUTED));
        String status = inspection.status(spot.id);
        TextView statusView = ui.text(string(R.string.spot_status, statusLabel(status)), 14, statusColor(status));
        statusView.setPadding(0, ui.dp(10), 0, 0);
        body.addView(statusView);

        LinearLayout tools = ui.card(body, string(R.string.spot_tools_title), string(R.string.spot_tools_copy));
        if (spot.nearby) tools.addView(ui.primary(string(R.string.spot_use_nearby), () -> app.inspectSpot(spot.id, MainActivity.NEARBY)));
        Button camera = spot.nearby ? ui.button(string(R.string.spot_use_camera), () -> app.inspectSpot(spot.id, MainActivity.CAMERA))
                : ui.primary(string(R.string.spot_use_camera), () -> app.inspectSpot(spot.id, MainActivity.CAMERA));
        addAction(tools, camera);
        if (spot.magnetic) {
            addAction(tools, ui.button(string(R.string.spot_use_magnetic), () -> app.inspectSpot(spot.id, MainActivity.SENSORS)));
            tools.addView(ui.text(string(R.string.spot_magnetic_why), 12, Ui.MUTED));
        }

        ui.label(body, string(R.string.spot_mark_label));
        ui.row(body, statusButton(spot, Inspection.INSPECTED, status), statusButton(spot, Inspection.CLOSER_LOOK, status));
        if (status != null) ui.action(body, string(R.string.spot_mark_clear), () -> setStatus(spot, null));

        ui.label(body, string(R.string.spot_note_label));
        spotNote = new EditText(app);
        spotNote.setTextColor(Ui.TEXT);
        spotNote.setHintTextColor(Ui.MUTED);
        spotNote.setHint(string(R.string.spot_note_hint));
        spotNote.setMinLines(2);
        spotNote.setGravity(Gravity.TOP);
        spotNote.setTextSize(15);
        if (spot.id.equals(spotDraftFor)) spotNote.setText(spotDraft);
        spotDraftFor = spot.id;
        body.addView(spotNote, new LinearLayout.LayoutParams(-1, -2));
        ui.action(body, string(R.string.note_save), () -> {
            String text = spotNote.getText().toString().trim();
            if (text.isEmpty()) {
                app.toast(string(R.string.note_empty));
                return;
            }
            app.recordAt(spot.id, Observation.NOTE, null, text, null, null);
            spotNote.setText("");
            app.toast(string(R.string.note_saved));
            app.refresh();
        });

        List<Observation> saved = inspection.forSpot(spot.id);
        ui.label(body, app.getResources().getQuantityString(R.plurals.spot_observations, saved.size(), saved.size()));
        if (saved.isEmpty()) body.addView(ui.text(string(R.string.spot_observations_none), 13, Ui.MUTED));
        for (int i = saved.size() - 1; i >= 0; i--) observation(body, saved.get(i), null);
    }

    private void addAction(LinearLayout parent, Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, ui.dp(8), 0, 0);
        parent.addView(button, params);
    }

    private Button statusButton(Checklist.Spot spot, String value, String current) {
        String title = string(Inspection.INSPECTED.equals(value) ? R.string.spot_mark_inspected : R.string.spot_mark_closer);
        Button button = value.equals(current) ? ui.primary(title, () -> setStatus(spot, value))
                : ui.button(title, () -> setStatus(spot, value));
        button.setSelected(value.equals(current));
        return button;
    }

    private void setStatus(Checklist.Spot spot, String status) {
        app.inspection().setStatus(spot.id, status);
        app.saveInspection();
        app.refresh();
    }

    private void rename() {
        EditText input = new EditText(app);
        input.setText(app.inspection().title);
        input.setSelectAllOnFocus(true);
        input.setSingleLine(true);
        LinearLayout frame = new LinearLayout(app);
        frame.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), 0);
        frame.addView(input, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(app).setTitle(R.string.start_rename_title).setView(frame)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    String title = input.getText().toString().trim();
                    if (title.isEmpty()) return;
                    app.inspection().title = title.length() > 80 ? title.substring(0, 80) : title;
                    app.saveInspection();
                    app.refresh();
                }).show();
    }

    @Override void leave() {
        if (spotNote != null) spotDraft = spotNote.getText().toString();
        spotNote = null;
    }

    @Override void save(Bundle state) {
        state.putString("start_spot", openSpot);
    }

    @Override void restore(Bundle state) {
        openSpot = state.getString("start_spot");
    }
}
