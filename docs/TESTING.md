# Try LensGuard on your Pixel 9

This guide checks whether the tools behave as documented. It does not establish that the app can reliably detect every hidden camera. You need about 10 minutes, your phone, a known camera lens and a few ordinary household objects.

## 1. Install and identify the build

Download **LensGuard.apk** from [the latest GitHub release](https://github.com/Feloguarin/LensGuard/releases/latest). Open the APK on your phone and follow Android's installer prompts. Allow the specific browser/file manager to install it if requested. Keep Play Protect enabled; review any warning rather than disabling device protections. [Google guidance](https://support.google.com/pixelphone/answer/2812853?hl=en)

Write down the release tag from GitHub, your phone model and Android version. The app shows its version at the bottom of the **Report** tab; several evaluation releases may share a version, so the release tag matters.

From 2.0.0, releases are signed with a permanent key, so a newer APK installs over the old one and keeps your inspections. **If you had LensGuard 1.0 or 1.1**, Android can't update it, because those versions were signed with temporary keys. Share anything you want to keep, uninstall the old app (**Settings → Apps → See all apps → LensGuard → Uninstall**), then install 2.0. Uninstalling deletes private notes and photos. For other installer messages, see [If it won't install](../README.md#if-it-wont-install). (Someone who updates their own 1.x build in place keeps its note and photos in an inspection named "Imported from LensGuard 1.x".)

### Optional file verification on a computer

Download `LensGuard.apk` and `SHA256SUMS.txt` from the **same release**. These commands read the files; they do not install the app:

```sh
# macOS
shasum -a 256 LensGuard.apk
# Linux
sha256sum -c SHA256SUMS.txt
```

```powershell
# Windows PowerShell
Get-FileHash .\LensGuard.apk -Algorithm SHA256
```

Compare the value with `SHA256SUMS.txt`. Matching checksums verify that the download matches the published asset; they do not prove detection accuracy or independently establish publisher identity.

## 2. Check a known lens — and a false-positive control

Open **Camera → Start camera** and grant Camera access. Use a visible webcam, another phone's camera or your own known camera as a controlled target.

- Point at the lens with **Turn light on**. Tap the lens area in the preview to focus. Dim the room and look from several angles, starting roughly 10–50 cm away. Record the actual distance.
- A thin white ring marks a small highlight; it should sit on the bright point in the preview. Hold still: a ring that stays put becomes a thicker green *steady* ring.
- Holding still on the lens, tap **Compare light on/off** and keep the phone still for about four seconds while the light switches on and off. A filled green ring means the highlight disappeared without the phone light (a reflection of it); an amber square means it stayed visible (a light source or a reflection of room light). If you moved, the app says so; repeat the comparison.
- Compare with a shiny screw, glass and an LED. These can also produce rings and reflections. A ring on them is a false positive for camera identification, although the app is correctly describing a highlight.
- Try **Switch camera**: front cameras usually have no flashlight, and the screen should explain why the light controls are disabled. Also try **Zoom & exposure options**.
- The **LIVE** frame counter should increase while the camera runs.

**Pass for basic functionality:** preview and controls work, rings line up with bright points, the comparison finishes and restores your light setting, and you can save a private photo. **Miss to record:** a known camera lens gets no ring, or the comparison does not separate it from a light source. A miss is valid evidence, not a failed experiment to discard.

### Your MacBook Pro camera as the first target

1. Leave the MacBook open. Find the small camera lens above its screen; the camera can be off for this reflection test. Lower screen brightness so it does not wash out the reflection.
2. On the Pixel, open **Camera → Start camera → Turn light on**. Use the rear camera. Start about 20–50 cm from the MacBook lens, tap that area to focus, and change angle and distance slowly.
3. Watch for a ring on the lens itself. Hold still until it is steady, then tap **Compare light on/off**. Record whether the lens ring was a reflection of the phone light, and whether the camera's status LED (if lit) stayed visible without the light.
4. Repeat with a shiny screw or glass. Record whether you can distinguish the known lens from these controls.
5. If the lens gets no ring, record a **miss**. Do not assume the app detected it. Save photos for each condition with **Save photo**, and notes with **Add a note**.

A MacBook webcam is an optical test target. Its built-in camera does not normally advertise as a standalone Wi-Fi/Bluetooth/ONVIF camera, so an empty Nearby result is not an optical test failure. This one target cannot establish sensitivity or false-positive rates for concealed cameras.

## 3. Check the checklist and magnetic calibration

On **Start**, open a place such as **Picture frames, mirrors & wall decor**. Mark it **Needs a closer look**, write a short note and tap **Save note**. Go back to **All places**: the place should show *Closer look* and the inspection summary should count it.

From that place, tap **Compare magnetic field**. A banner shows that readings you save are linked to the place. Stay away from metal furniture, chargers and magnets, tap **Set baseline** and hold the phone still for at least three seconds. If it reports low accuracy, follow its calibration guidance and retry.

Note the field in microtesla (`µT`), the change from baseline and the chart. Move slowly near an ordinary speaker or steel object, without touching or placing strong magnets against the phone. The chart should rise and **Largest change since baseline** should record the peak. Tap **Save reading**, and recalibrate when you change environments.

**Expected:** field values may change. **Interpretation:** ordinary metal/magnets/electronics are controls that show why magnetic readings cannot identify cameras. This is not an RF meter.

## 4. Check wireless discovery

Enable Wi-Fi, Bluetooth and Android Location, and connect to a Wi-Fi network you control. In **Nearby**, tap **Start 15-second scan** and allow precise Location/Nearby devices permissions for the corresponding tools.

| Control | What to check |
| --- | --- |
| Your known Wi-Fi access point | SSID, signal bars and fresh/cached age labels. This is an access-point list, not a network-client list. |
| A known BLE advertiser | Name/RSSI if it advertises. A paired device is not guaranteed to advertise or appear. Tap **Follow signal**, walk toward and away from it, and check that the trend changes and following stops after 60 seconds. |
| Devices on your own Wi-Fi network | Under *This Wi-Fi network*, a smart TV, router or media device may answer UPnP discovery; an IP camera you own may answer ONVIF discovery or advertise RTSP. Answers and service names do not confirm camera identity; many cameras answer nothing. |
| Leads | A device with a camera-like name, video service or video role appears under **Leads to inspect** with an amber tag. Ordinary names such as "Campus-WiFi" should not. |
| Radios off / Location off / permissions denied / Wi-Fi disconnected | Helpful unavailable states; other tools continue working. |

The countdown should decrease and stop after 15 seconds. Review counts, leads and per-radio messages, then tap **Save scan to report**. Avoid repeated Wi-Fi scan requests: Android normally allows four foreground requests per two minutes, and results may be cached. [Android scan rules](https://developer.android.com/develop/connectivity/wifi/wifi-scan)

## 5. Check sensor context and optional microphone

In **Sensors → Open sensor readings**, rotate the phone and cover/uncover the light sensor area. Inspect the exposed inventory and reading/status changes. A missing or restricted sensor should be shown as unavailable rather than fabricated.

If useful, in **Sensors → Open sound check** tap **Enable microphone** in a quiet room, then **Stop microphone**. This is a coarse 15–22 kHz tone observation from 48 kHz input, not a camera detector. Silence or an unrelated tone is a valid result. No test tone or ultrasonic speaker is needed for the beginner check.

## 6. Check evidence and background behavior

1. In Camera, tap **Save photo** while the camera runs.
2. In **Report**, write a neutral observation, such as “Corner shelf: small reflection visible from two angles; unconfirmed,” and tap **Save note**. Saved photos, scans and readings appear in the same list.
3. Tap **Share PDF report**, **Share data (JSON)** and **Share photos**. Choose a destination you control and verify the contents. Device addresses should be partly hidden while the button reads **Addresses: hidden**; network and device names remain, so review the report before any public upload.
4. Press Home or lock the screen. Camera/microphone use should stop, along with scanning, signal following and sensor sampling. After returning, restart any tool you want to use.
5. Try **Start a new inspection**, then **Open** the earlier one from the inspections list.
6. After exporting anything you want to keep, use **Delete all saved evidence** to check removal of local inspections, notes, photos and cached reports. Exported copies must be deleted separately.

## 7. Report what happened

Copy [the test-results template](test-results/TEMPLATE.md), fill in your configuration and mark checks **pass / fail / unavailable / not run**. Keep misses and false positives. Do not upload a raw inspection report, personal room photos, SSIDs/BSSIDs, Bluetooth identifiers or precise location publicly.

Submit a [device-test report](https://github.com/Feloguarin/LensGuard/issues/new/choose) or open a PR adding a sanitized Markdown result under `docs/test-results/`. A passed functional check does not certify a room as camera-free.

## Troubleshooting

| Problem | Next step |
| --- | --- |
| "Can't update" or "package conflicts with an existing package" | An older LensGuard (1.0/1.1 or your own build) is installed. Share what you want to keep, uninstall it, then open the APK again. Other messages: [If it won't install](../README.md#if-it-wont-install). |
| Camera black/unavailable | Grant Camera access, close another camera app, return to Camera and tap Start camera. Try rear/front selection. |
| Flashlight does not work | Start the camera first. Front cameras may have no flash; try the rear camera. |
| Baseline will not finish | Hold still, wait for fresh motion readings, move away from metal and follow low-accuracy guidance. |
| Wi-Fi empty or stale | Grant precise Location, enable Android Location/Wi-Fi, check cached age, then wait before another scan. |
| Bluetooth device missing | Check permissions and Bluetooth; the device must advertise BLE. Being paired is insufficient. |
| Microphone unavailable | Check permission and Android's microphone privacy control. Stop another recorder and retry. |
| No clue from a known camera | Record the conditions and miss. Concealed/offline/non-advertising cameras may produce no usable signal. |
| Compare light on/off is disabled | Start the camera and use a camera with a flashlight, usually the rear one. |
| Comparison says the phone moved | Rest your hand or the phone against something and compare again; positions only line up when the view stays still. |
| Nothing under *This Wi-Fi network* | Connect to the Wi-Fi network you want to check. Many devices answer neither ONVIF nor UPnP discovery, and some networks block it. |
| Follow signal finds nothing | The device must keep advertising and keep its address; wait 30 seconds before retrying because Android limits repeated Bluetooth scans. |

For deeper testing, use [DEVICE_TEST_PLAN.md](DEVICE_TEST_PLAN.md). Developer installation/debugging instructions are in [CONTRIBUTING.md](../CONTRIBUTING.md).
