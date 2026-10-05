package com.feloguarin.lensguard;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;

/** One tab. Screens rebuild their views on each visit and keep only small state between visits. */
abstract class Screen {
    final MainActivity app;
    final Ui ui;

    Screen(MainActivity app) {
        this.app = app;
        this.ui = app.ui;
    }

    abstract void build(LinearLayout body);

    /** The tab is closing; its views are about to be discarded. */
    void leave() {}

    /** The app left the foreground. Views stay on screen and must keep working after resume. */
    void pause() {}

    void onSensors(SensorMonitor.Snapshot snapshot) {}
    void onWireless(WirelessProbe.Survey survey) {}
    void onFollower(SignalFollower.State state) {}
    void onAudio(String message) {}
    void save(Bundle state) {}
    void restore(Bundle state) {}

    String string(int id, Object... args) {
        return app.getString(id, args);
    }

    /** Shows which checklist spot new photos and readings will be linked to. */
    void spotBanner(LinearLayout body) {
        Checklist.Spot spot = Checklist.find(app.spot);
        if (spot == null) return;
        LinearLayout banner = new LinearLayout(app);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(8));
        banner.setBackground(ui.shape(Ui.AMBER_SURFACE, 14));
        TextView title = ui.text(string(R.string.spot_banner, app.getString(spot.title)), 13, Ui.AMBER);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        banner.addView(title);
        banner.addView(ui.text(string(R.string.spot_banner_detail), 12, Ui.MUTED));
        ui.row(banner, ui.button(string(R.string.spot_banner_open), () -> app.openSpot(spot.id)),
                ui.button(string(R.string.spot_banner_done), () -> { app.spot = null; app.refresh(); }));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, ui.dp(10), 0, ui.dp(4));
        body.addView(banner, params);
    }

    /** One observation, as listed on the report and on a checklist spot. */
    LinearLayout observation(LinearLayout parent, Observation observation, Runnable delete) {
        LinearLayout card = ui.card(parent, null, null);
        Checklist.Spot spot = Checklist.find(observation.spot);
        String heading = app.sourceName(observation.source) + " · " + DateUtils.formatDateTime(app, observation.time,
                DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH);
        if (spot != null) heading += " · " + app.getString(spot.title);
        TextView header = ui.text(heading, 12, Ui.MINT);
        card.addView(header);
        if (observation.summary != null) card.addView(ui.text(observation.summary, 14, Ui.TEXT));
        if (observation.note != null && !observation.note.isEmpty()) {
            TextView note = ui.text(observation.note, 14, Ui.TEXT);
            note.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
            note.setPadding(0, ui.dp(4), 0, 0);
            card.addView(note);
        }
        File photo = app.store.photoFile(observation.photo);
        if (photo != null) {
            ImageView image = new ImageView(app);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(ui.shape(Ui.RAISED, 10));
            image.setClipToOutline(true);
            image.setContentDescription(string(R.string.observation_photo_description));
            image.setOnClickListener(v -> app.viewPhoto(photo));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, ui.dp(150));
            params.setMargins(0, ui.dp(8), 0, 0);
            card.addView(image, params);
            if (photo.exists()) app.thumbnails.load(photo, image, ui.dp(320));
            else image.setVisibility(View.GONE);
        }
        if (delete != null) {
            Button remove = ui.button(string(R.string.observation_delete), delete);
            remove.setTextColor(Ui.MUTED);
            remove.setBackground(ui.outlineSurface(Ui.RAISED, 12));
            remove.setContentDescription(string(R.string.observation_delete_description, heading));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.setMargins(0, ui.dp(10), 0, 0);
            card.addView(remove, params);
        }
        return card;
    }
}
