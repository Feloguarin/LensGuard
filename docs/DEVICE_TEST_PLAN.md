# Physical-device test plan

Status: **not run**. No claim of Pixel 9 device testing or hidden-camera detection accuracy is made by this release. Automated build and unit-test results are separate from this plan.

Reference device: base Google Pixel 9. Record the model, Android version, build number, app version, APK SHA-256, and signing-certificate SHA-256 for each run. Also test at least one API 28–30 device and one other Android 12+ phone when available.

## Installation and permissions

- Install the GitHub APK on a clean device. Verify the app opens and the displayed version matches the release.
- Deny camera, microphone, location, and Nearby devices separately. Verify each tool explains its unavailable state and the remaining tools continue working.
- Grant approximate location only; verify Wi-Fi scanning explains that precise location is required. Turn Location services off and verify the guidance.
- Revoke permissions in Settings while an inspection is active, then resume the app. Verify no crash or fabricated result.
- Verify two releases from 2.0.0 on share the permanent signing certificate and that installing the newer one over the older updates in place, keeping inspections and photos. Verify a 1.0/1.1 evaluation build cannot be updated in place, that installing 2.0 works after uninstalling it, and that uninstalling removes private data; export evidence first.

## Camera and optical hints

- Verify rear and front previews, rotation, focus, zoom limits, torch availability, and snapshot capture. A front camera without flash must not offer a functioning torch.
- Test after screen lock, backgrounding, rapid tab changes, and a competing camera app. Verify camera resources are released and resume safely.
- Inspect a visible camera lens as a controlled positive example. Repeat at different angles, distances, lighting, and with torch off/on. Record when the heuristic misses it.
- Test shiny screws, mirrors, glass, LEDs, chrome, and other bright objects. Record false positives and verify the UI always describes a bright-point clue, never a confirmed camera.
- Verify rings line up with the bright points in the preview at 1×, after zooming, after rotation, and on the front camera (whose preview is mirrored). Rings mark highlights to inspect, never an identified lens. No guaranteed infrared or thermal function is expected.
- Light comparison: with the phone braced, compare a known lens, a mirror, glass, a chrome screw, a powered LED and a phone screen. Record which ones are reported as reflecting the phone light and which as visible without it, the distance and angle, and any result marked as moved. Verify the flashlight returns to its previous state, including after backgrounding the app mid-comparison.
- Verify the steady state appears when the phone is held still and clears when it sweeps, and that the comparison is unavailable on cameras without a flashlight.

## Sensors and baseline

- Compare the app inventory with Android's exposed sensors. Verify absent sensors are unavailable, and restricted or failed registrations are explicitly handled.
- Hold the phone stationary away from electronics and set a baseline. Verify field magnitude/delta are in µT and baseline state is clear.
- Approach a speaker magnet, charger, steel fixture, and ordinary electronics. Verify magnetic changes remain non-diagnostic clues; a large reading must not become a camera verdict.
- Check motion guidance while still and while walking/rotating. Check light changes, proximity cover state, and pressure readings where exposed. These must not independently classify cameras.
- Background the app and confirm sensor listeners stop. Resume and verify appropriate baseline/readings behavior without stale values presented as fresh measurements.

## Checklist, inspections and migration

- Mark several places, add notes from a place and from Report, and save photos with a place active. Verify each observation shows the right place, and that statuses and notes survive force-stop, rotation and restart.
- Create, rename, switch and delete inspections. Verify deleting one removes only its photos.
- Install a 1.x build signed with the same local key, save a note and photos, then update to 2.0 in place. Verify a single "Imported from LensGuard 1.x" inspection contains them and the import does not repeat.

## Wireless discovery

- With known Wi-Fi access points, verify names, signal strength, result timing, and denied/disabled states. Press scan repeatedly and confirm Android throttling and cached results are communicated.
- Use a known BLE advertiser and a non-advertising device. Verify advertisement discovery, timestamps, and the end of the 15-second session. Confirm a missing device is never presented as evidence of a clear room.
- Test a controlled local service advertiser, an empty network, Wi-Fi disconnected, multicast-blocked Wi-Fi, and permission denial. Verify bounded discovery and useful failure states.
- Verify recognized and camera-like names remain names/services to inspect. A camera on an existing access point must not be assumed visible in the Wi-Fi access-point list.
- Move and rotate the phone around a known advertiser. Confirm RSSI is not displayed as an accurate distance or a camera probability.
- On a network you control, verify ONVIF WS-Discovery and UPnP SSDP replies from known devices (an ONVIF camera you own, a smart TV, a router), with Wi-Fi as the only network and with mobile data also active. Verify nothing is sent when Wi-Fi is disconnected and that discovery ends with the 15-second scan.
- Verify leads: camera-like names, RTSP/ONVIF services and video roles are tagged; ordinary names are not. Record false leads.
- Follow signal: verify the trend while walking toward and away from a known advertiser, the 60-second limit, the quiet-signal message when the device stops advertising, and that following stops when leaving the screen or the app.

## Audio and evidence privacy

- Test microphone granted, denied, occupied by another app, muted through Android privacy controls, and unavailable input configurations.
- Verify 48 kHz input initializes where supported, failure states are clear, and analysis handles silence and clipping. Use known test tones for frequency checks; no camera-identification accuracy is implied.
- Inspect app storage before/after audio analysis. Verify no raw microphone recording is saved.
- Capture a snapshot; verify private storage. Share a selected snapshot/report through Android's share sheet and verify only requested evidence is accessible to the receiver.
- Share the PDF and JSON reports and all photos. Verify photo orientation, readable layout across pages, and that addresses are partly hidden unless **Addresses: included** is chosen. Review notes, timestamps and network/device names before sharing.
- Use Delete all saved evidence and verify private inspections, photos, notes and cached reports are removed. Repeat using Android's Clear storage control.
- Verify camera, microphone, and discovery stop after backgrounding, screen lock, and leaving an inspection. Confirm no analytics or cloud-upload traffic from the app.

## Languages and accessibility

- With the phone or LensGuard set to Spanish (Android 13+: Settings → Apps → LensGuard → Language), check every tab for untranslated or clipped text, and that numbers use the local decimal separator.
- With TalkBack, verify screen titles and section labels are announced as headings, buttons and checklist rows have clear labels, and status, comparison and trend updates are announced without flooding.

## Acceptance and reporting

Record each check as pass, fail, or unavailable, with screenshots/logs and reproduction steps for failures. A successful run means the app behaves as documented on that configuration; it does not certify a room as camera-free or establish reliable camera-detection sensitivity.

Relevant platform constraints: [sensor inventory and lifecycle](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview), [magnetometer units](https://developer.android.com/develop/sensors-and-location/sensors/sensors_position), [Wi-Fi permissions and throttling](https://developer.android.com/develop/connectivity/wifi/wifi-scan), [BLE permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions), [audio processing and foreground access](https://developer.android.com/media/platform/mediarecorder).
