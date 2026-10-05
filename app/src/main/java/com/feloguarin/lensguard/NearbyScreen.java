package com.feloguarin.lensguard;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** The bounded nearby survey, its leads, and following one Bluetooth signal. */
final class NearbyScreen extends Screen {
    static final int SURVEY = 0, FOLLOW = 1;
    private static final int DISPLAY_LIMIT = 20;

    int page = SURVEY;
    private String followAddress;
    private String followName;
    private TextView status;
    private Button scanButton, stopButton, saveButton;
    private LinearLayout results;
    private TextView followSignal, followTrend, followStatus, followBest;
    private SparklineView followChart;
    private Button followButton, followSave;
    private SignalFollower.State lastFollow;

    NearbyScreen(MainActivity app) {
        super(app);
    }

    @Override void build(LinearLayout body) {
        if (page == FOLLOW && followAddress != null) buildFollow(body);
        else buildSurvey(body);
    }

    private void buildSurvey(LinearLayout body) {
        page = SURVEY;
        body.addView(ui.title(string(R.string.nearby_title)));
        body.addView(ui.text(string(R.string.nearby_intro), 14, Ui.MUTED));
        spotBanner(body);
        status = ui.text("", 15, Ui.MINT);
        status.setPadding(0, ui.dp(10), 0, 0);
        body.addView(status);
        scanButton = ui.primary(string(R.string.nearby_start), () -> app.permissions(app.radioPermissions(), app.wireless::start));
        stopButton = ui.button(string(R.string.nearby_stop), app.wireless::stop);
        ui.row(body, scanButton, stopButton);
        body.addView(ui.text(string(R.string.nearby_explain), 13, Ui.MUTED));
        results = new LinearLayout(app);
        results.setOrientation(LinearLayout.VERTICAL);
        body.addView(results);
        saveButton = ui.action(body, string(R.string.nearby_save), this::saveSurvey);
        render(app.survey != null ? app.survey : app.wireless.survey());
    }

    @Override void onWireless(WirelessProbe.Survey survey) {
        if (page == SURVEY) render(survey);
    }

    private void render(WirelessProbe.Survey survey) {
        if (status == null || results == null) return;
        Ui.set(status, summary(survey));
        Ui.enabled(stopButton, survey.running());
        Ui.enabled(scanButton, !survey.running());
        Ui.enabled(saveButton, survey.state == WirelessProbe.COMPLETE || survey.state == WirelessProbe.STOPPED);
        results.removeAllViews();
        if (survey.state == WirelessProbe.READY) return;
        List<WirelessProbe.Device> leads = survey.leads();
        if (!leads.isEmpty()) {
            LinearLayout card = ui.card(results, app.getResources().getQuantityString(R.plurals.nearby_leads,
                    leads.size(), leads.size()), string(R.string.nearby_leads_explain));
            card.setBackground(ui.shape(Ui.AMBER_SURFACE, 16));
            for (WirelessProbe.Device device : limit(leads)) deviceRow(card, device);
        } else if (!survey.running()) {
            ui.card(results, string(R.string.nearby_no_leads_title), string(R.string.nearby_no_leads));
        }
        section(survey.wifi, string(R.string.nearby_section_wifi), survey.wifiStatus, string(R.string.nearby_wifi_note),
                string(R.string.nearby_wifi_empty));
        section(survey.ble, string(R.string.nearby_section_ble), survey.bleStatus, string(R.string.nearby_ble_note),
                string(R.string.nearby_ble_empty));
        section(survey.network, string(R.string.nearby_section_network), survey.networkStatus,
                string(R.string.nearby_network_note), string(R.string.nearby_network_empty));
    }

    private String summary(WirelessProbe.Survey survey) {
        String state;
        switch (survey.state) {
            case WirelessProbe.SCANNING:
                state = app.getResources().getQuantityString(R.plurals.nearby_state_scanning, survey.remainingSeconds,
                        survey.remainingSeconds);
                break;
            case WirelessProbe.COMPLETE: state = string(R.string.nearby_state_complete); break;
            case WirelessProbe.STOPPED: state = string(R.string.nearby_state_stopped); break;
            default: return string(R.string.nearby_state_ready);
        }
        return state + "\n" + string(R.string.nearby_counts, survey.wifi.size(), survey.ble.size(), survey.network.size());
    }

    private void section(List<WirelessProbe.Device> devices, String title, String radioStatus, String note, String empty) {
        LinearLayout card = ui.card(results, title, radioStatus);
        TextView caveat = ui.text(note, 12, Ui.MUTED);
        caveat.setPadding(0, ui.dp(4), 0, ui.dp(4));
        card.addView(caveat);
        if (devices.isEmpty()) card.addView(ui.text(empty, 13, Ui.TEXT));
        for (WirelessProbe.Device device : limit(devices)) deviceRow(card, device);
        if (devices.size() > DISPLAY_LIMIT) {
            card.addView(ui.text(string(R.string.nearby_more, devices.size() - DISPLAY_LIMIT), 12, Ui.MUTED));
        }
    }

    private static List<WirelessProbe.Device> limit(List<WirelessProbe.Device> devices) {
        return devices.size() > DISPLAY_LIMIT ? devices.subList(0, DISPLAY_LIMIT) : devices;
    }

    private void deviceRow(LinearLayout card, WirelessProbe.Device device) {
        LinearLayout row = new LinearLayout(app);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, ui.dp(10), 0, ui.dp(4));
        LinearLayout top = new LinearLayout(app);
        top.setGravity(Gravity.CENTER_VERTICAL);
        if (device.rssi != WirelessProbe.NO_RSSI) {
            SignalBarsView bars = new SignalBarsView(app);
            bars.setRssi(device.rssi);
            bars.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-2, -2);
            barParams.setMargins(0, 0, ui.dp(8), 0);
            top.addView(bars, barParams);
        }
        TextView name = ui.text(displayName(device), 14, Ui.TEXT);
        top.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(top);
        String detail = detail(device);
        row.addView(ui.text(detail, 12, Ui.MUTED));
        if (!device.hints.isEmpty()) {
            List<TextView> chips = new ArrayList<>();
            for (String hint : device.hints) chips.add(ui.chip(hintLabel(hint), Ui.AMBER));
            row.addView(ui.chips(chips.toArray(new TextView[0])));
        }
        if (device.followable()) {
            Button follow = ui.button(string(R.string.nearby_follow), () -> follow(device));
            follow.setContentDescription(string(R.string.nearby_follow_description, displayName(device)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.setMargins(0, ui.dp(6), 0, 0);
            row.addView(follow, params);
        }
        row.setContentDescription(displayName(device) + ". " + detail + hintsDescription(device));
        card.addView(row);
    }

    private String hintsDescription(WirelessProbe.Device device) {
        StringBuilder text = new StringBuilder();
        for (String hint : device.hints) text.append(". ").append(hintLabel(hint));
        return text.toString();
    }

    String displayName(WirelessProbe.Device device) {
        if (device.name != null) return device.name;
        switch (device.kind) {
            case WirelessProbe.WIFI: return string(R.string.nearby_unnamed_wifi);
            case WirelessProbe.BLE: return string(R.string.nearby_unnamed_ble);
            case WirelessProbe.ONVIF: return string(R.string.nearby_unnamed_onvif);
            case WirelessProbe.UPNP: return string(R.string.nearby_unnamed_upnp);
            default: return string(R.string.nearby_unnamed_service);
        }
    }

    private String detail(WirelessProbe.Device device) {
        long now = SystemClock.elapsedRealtime();
        String address = device.address != null ? device.address : string(R.string.nearby_address_unavailable);
        switch (device.kind) {
            case WirelessProbe.WIFI: {
                String age = device.seenMs <= 0 ? string(R.string.nearby_age_unknown)
                        : string(device.fresh ? R.string.nearby_age_fresh : R.string.nearby_age_cached,
                        (now - device.seenMs) / 1000f);
                return string(R.string.nearby_wifi_detail, address, device.rssi, device.frequency, age);
            }
            case WirelessProbe.BLE:
                return string(R.string.nearby_ble_detail, address, device.rssi, Math.max(0, now - device.seenMs) / 1000);
            case WirelessProbe.SERVICE:
                return string(device.lost ? R.string.nearby_service_detail_lost : R.string.nearby_service_detail,
                        serviceName(device.detail));
            case WirelessProbe.ONVIF:
                return device.detail == null ? string(R.string.nearby_onvif_detail_short, address)
                        : string(R.string.nearby_onvif_detail, address, device.detail);
            default:
                return device.detail == null ? string(R.string.nearby_upnp_detail_short, address)
                        : string(R.string.nearby_upnp_detail, address, device.detail);
        }
    }

    private String serviceName(String type) {
        if (type == null) return "";
        if (type.startsWith("_rtsp.")) return string(R.string.service_rtsp);
        if (type.startsWith("_onvif.")) return string(R.string.service_onvif);
        if (type.startsWith("_http.")) return string(R.string.service_http);
        return type;
    }

    String hintLabel(String hint) {
        switch (hint) {
            case WirelessProbe.HINT_VIDEO_SERVICE: return string(R.string.hint_video_service);
            case WirelessProbe.HINT_CAMERA_ROLE: return string(R.string.hint_camera_role);
            default: return string(R.string.hint_camera_name);
        }
    }

    private void saveSurvey() {
        WirelessProbe.Survey survey = app.survey;
        if (survey == null || survey.state == WirelessProbe.READY || survey.running()) return;
        JSONObject data;
        try {
            data = survey.toJson();
        } catch (JSONException unexpected) {
            data = null;
        }
        String text = string(R.string.nearby_saved_summary, survey.wifi.size(), survey.ble.size(), survey.network.size())
                + " · " + app.getResources().getQuantityString(R.plurals.nearby_leads, survey.leads().size(), survey.leads().size());
        app.record(Observation.NEARBY, text, null, null, data);
        app.toast(string(R.string.observation_saved));
    }

    private void follow(WirelessProbe.Device device) {
        followAddress = device.address;
        followName = displayName(device);
        lastFollow = null;
        page = FOLLOW;
        app.showTab(MainActivity.NEARBY);
        app.permissions(app.radioPermissions(), () -> app.follower.start(followAddress, followName));
    }

    private void buildFollow(LinearLayout body) {
        ui.action(body, string(R.string.follow_back), () -> {
            page = SURVEY;
            app.showTab(MainActivity.NEARBY);
        });
        body.addView(ui.title(string(R.string.follow_title)));
        body.addView(ui.text(followName, 16, Ui.TEXT));
        body.addView(ui.text(followAddress, 12, Ui.MUTED));
        spotBanner(body);
        followSignal = ui.text(string(R.string.follow_no_signal), 34, Ui.MINT);
        followSignal.setPadding(0, ui.dp(12), 0, 0);
        body.addView(followSignal);
        followTrend = ui.text("", 16, Ui.TEXT);
        followTrend.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        body.addView(followTrend);
        followChart = new SparklineView(app);
        LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(-1, ui.dp(96));
        chartParams.setMargins(0, ui.dp(10), 0, ui.dp(6));
        body.addView(followChart, chartParams);
        followBest = ui.text("", 12, Ui.MUTED);
        body.addView(followBest);
        followStatus = ui.text(string(R.string.follow_idle), 13, Ui.MUTED);
        body.addView(followStatus);
        followButton = ui.primary(string(R.string.follow_stop), this::toggleFollow);
        followSave = ui.button(string(R.string.follow_save), this::saveFollow);
        ui.row(body, followButton, followSave);
        ui.card(body, string(R.string.follow_how_title), string(R.string.follow_how));
        onFollower(app.follower.state());
    }

    private void toggleFollow() {
        if (app.follower.isRunning()) app.follower.stop();
        else app.permissions(app.radioPermissions(), () -> app.follower.start(followAddress, followName));
    }

    @Override void onFollower(SignalFollower.State state) {
        if (followSignal == null || page != FOLLOW) return;
        if (followAddress == null || !followAddress.equals(state.address)) {
            followButton.setText(string(R.string.follow_start));
            Ui.set(followStatus, string(R.string.follow_idle));
            Ui.enabled(followSave, false);
            return;
        }
        lastFollow = state;
        Ui.set(followSignal, state.hasSignal() ? string(R.string.follow_dbm, state.latest) : string(R.string.follow_no_signal));
        int trend = state.trend;
        Ui.set(followTrend, !state.hasSignal() ? string(R.string.follow_waiting) : trend == SignalTrend.STRONGER
                ? string(R.string.follow_stronger) : trend == SignalTrend.WEAKER ? string(R.string.follow_weaker)
                : string(R.string.follow_steady));
        followChart.setValues(state.history, Float.NaN, state.hasSignal()
                ? string(R.string.follow_chart_description, Math.round(state.smoothed)) : string(R.string.follow_no_signal));
        Ui.set(followBest, state.strongest == Integer.MIN_VALUE ? "" : string(R.string.follow_strongest, state.strongest));
        Ui.set(followStatus, state.running ? string(R.string.follow_running, state.remainingSeconds) + "\n" + state.status
                : state.status);
        followButton.setText(string(state.running ? R.string.follow_stop : R.string.follow_start));
        Ui.enabled(followSave, state.hasSignal());
    }

    private void saveFollow() {
        SignalFollower.State state = lastFollow;
        if (state == null || !state.hasSignal()) return;
        JSONObject data = new JSONObject();
        try {
            data.put("kind", WirelessProbe.BLE);
            data.put("name", followName);
            data.put("address", followAddress);
            data.put("latestRssiDbm", state.latest);
            data.put("strongestRssiDbm", state.strongest);
        } catch (JSONException ignored) {
            // Plain values only.
        }
        app.record(Observation.NEARBY, string(R.string.follow_saved_summary, followName, state.latest, state.strongest),
                null, null, data);
        app.toast(string(R.string.observation_saved));
    }

    @Override void leave() {
        status = null;
        results = null;
        scanButton = stopButton = saveButton = null;
        followSignal = followTrend = followStatus = followBest = null;
        followChart = null;
        followButton = followSave = null;
    }

    @Override void save(Bundle state) {
        state.putInt("nearby_page", page);
        state.putString("nearby_follow_address", followAddress);
        state.putString("nearby_follow_name", followName);
    }

    @Override void restore(Bundle state) {
        page = state.getInt("nearby_page", SURVEY);
        followAddress = state.getString("nearby_follow_address");
        followName = state.getString("nearby_follow_name");
    }
}
