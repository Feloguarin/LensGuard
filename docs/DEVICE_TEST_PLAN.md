# Physical-device test plan

Status: **not run**. No claim of Pixel 9 device testing or hidden-camera detection accuracy is made by this release. Automated build and unit-test results are separate from this plan.

Reference device: base Google Pixel 9. Record the model, Android version, build number, app version, APK SHA-256, and signing-certificate SHA-256 for each run. Also test at least one API 28–30 device and one other Android 12+ phone when available.

## Installation and permissions

- Install the GitHub APK on a clean device. Verify the app opens and the displayed version matches the release.
- Deny camera, microphone, location, and Nearby devices separately. Verify each tool explains its unavailable state and the remaining tools continue working.
- Grant approximate location only; verify Wi-Fi scanning explains that precise location is required. Turn Location services off and verify the guidance.
- Revoke permissions in Settings while an inspection is active, then resume the app. Verify no crash or fabricated result.
- Compare signing certificates for two CI builds. When the development identities differ, verify Android rejects an in-place update and installation works after uninstalling. Export evidence before uninstalling and verify private data is removed. When a persistent private production signer is configured, separately verify in-place updates signed with that identity.

## Camera and optical hints

- Verify rear and front previews, rotation, focus, zoom limits, torch availability, and snapshot capture. A front camera without flash must not offer a functioning torch.
- Test after screen lock, backgrounding, rapid tab changes, and a competing camera app. Verify camera resources are released and resume safely.
- Inspect a visible camera lens as a controlled positive example. Repeat at different angles, distances, lighting, and with torch off/on. Record when the heuristic misses it.
- Test shiny screws, mirrors, glass, LEDs, chrome, and other bright objects. Record false positives and verify the UI always describes a bright-point clue, never a confirmed camera.
- Verify the preview and textual highlight counts behave correctly after rotation, zoom, and switching camera. The heuristic does not draw identified-lens markers. No guaranteed infrared or thermal function is expected.

## Sensors and baseline

- Compare the app inventory with Android's exposed sensors. Verify absent sensors are unavailable, and restricted or failed registrations are explicitly handled.
- Hold the phone stationary away from electronics and set a baseline. Verify field magnitude/delta are in µT and baseline state is clear.
- Approach a speaker magnet, charger, steel fixture, and ordinary electronics. Verify magnetic changes remain non-diagnostic clues; a large reading must not become a camera verdict.
- Check motion guidance while still and while walking/rotating. Check light changes, proximity cover state, and pressure readings where exposed. These must not independently classify cameras.
- Background the app and confirm sensor listeners stop. Resume and verify appropriate baseline/readings behavior without stale values presented as fresh measurements.

## Wireless discovery

- With known Wi-Fi access points, verify names, signal strength, result timing, and denied/disabled states. Press scan repeatedly and confirm Android throttling and cached results are communicated.
- Use a known BLE advertiser and a non-advertising device. Verify advertisement discovery, timestamps, and the end of the 15-second session. Confirm a missing device is never presented as evidence of a clear room.
- Test a controlled local service advertiser, an empty network, Wi-Fi disconnected, multicast-blocked Wi-Fi, and permission denial. Verify bounded discovery and useful failure states.
- Verify recognized and camera-like names remain names/services to inspect. A camera on an existing access point must not be assumed visible in the Wi-Fi access-point list.
- Move and rotate the phone around a known advertiser. Confirm RSSI is not displayed as an accurate distance or a camera probability.

## Audio and evidence privacy

- Test microphone granted, denied, occupied by another app, muted through Android privacy controls, and unavailable input configurations.
- Verify 48 kHz input initializes where supported, failure states are clear, and analysis handles silence and clipping. Use known test tones for frequency checks; no camera-identification accuracy is implied.
- Inspect app storage before/after audio analysis. Verify no raw microphone recording is saved.
- Capture a snapshot; verify private storage. Share a selected snapshot/report through Android's share sheet and verify only requested evidence is accessible to the receiver.
- Review report fields for notes, timestamps, and device/network identifiers. Use Delete saved evidence and verify private photos, notes, and cached reports are removed. Repeat using Android's Clear storage control.
- Verify camera, microphone, and discovery stop after backgrounding, screen lock, and leaving an inspection. Confirm no analytics or cloud-upload traffic from the app.

## Acceptance and reporting

Record each check as pass, fail, or unavailable, with screenshots/logs and reproduction steps for failures. A successful run means the app behaves as documented on that configuration; it does not certify a room as camera-free or establish reliable camera-detection sensitivity.

Relevant platform constraints: [sensor inventory and lifecycle](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview), [magnetometer units](https://developer.android.com/develop/sensors-and-location/sensors/sensors_position), [Wi-Fi permissions and throttling](https://developer.android.com/develop/connectivity/wifi/wifi-scan), [BLE permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions), [audio processing and foreground access](https://developer.android.com/media/platform/mediarecorder).
