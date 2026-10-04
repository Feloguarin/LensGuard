# Try LensGuard on your Pixel 9

This guide checks whether the tools behave as documented. It does not establish that the app can reliably detect every hidden camera. You need about 10 minutes, your phone, a known camera lens and a few ordinary household objects.

## 1. Install and identify the build

Download **LensGuard.apk** from [the latest GitHub release](https://github.com/Feloguarin/LensGuard/releases/latest). Open the APK on your phone and follow Android's installer prompts. Allow the specific browser/file manager to install it if requested. Keep Play Protect enabled; review any warning rather than disabling device protections. [Google guidance](https://support.google.com/pixelphone/answer/2812853?hl=en)

Write down the release tag from GitHub, your phone model and Android version. The app displays version 1.0.0 in Notes; multiple evaluation releases may share that version, so the release tag matters.

If you already installed an evaluation APK and Android says an update cannot be installed, export your evidence, uninstall the old app and install the new APK. Each CI build uses a different development signer. Uninstalling deletes private notes/photos.

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

Open **Sweep → Start camera** and grant Camera access. Use a visible webcam, another phone's camera or your own known camera as a controlled target.

- Point at the lens. Try **Light off/on**, front/rear **Flip**, the **Zoom** slider and **Exposure** slider.
- Dim the room and look from several angles, starting roughly 10–50 cm away. Record the actual distance; a lens may produce no usable highlight.
- Compare with a shiny screw, glass and an LED. These can also produce bright points. A clue from them is a false positive for camera identification, although the app is correctly describing a highlight.
- The center cross is a framing guide. Highlight counts are textual; the app does not mark an identified lens or draw a bounding box around one.

**Pass for basic functionality:** preview/controls work, unavailable front flashlight is explained, hints remain observations and you can save a private photo. **Miss to record:** a known camera produces no useful visual clue. A miss is valid evidence, not a failed experiment to discard.

## 3. Check magnetic calibration

Stay away from metal furniture, chargers and magnets. In **Sweep**, tap **Calibrate baseline** and hold the phone still for at least three seconds. If it reports low accuracy, follow its calibration guidance and retry.

Note the field in microtesla (`µT`) and baseline change. Move slowly near an ordinary speaker or steel object, without touching or placing strong magnets against the phone. Compare several positions and recalibrate when you change environments.

**Expected:** field values may change. **Interpretation:** ordinary metal/magnets/electronics are controls that show why magnetic readings cannot identify cameras. This is not an RF meter.

## 4. Check wireless discovery

Enable Wi-Fi, Bluetooth and Android Location. In **Signals**, tap **Scan · 15 seconds** and allow precise Location/Nearby devices permissions for the corresponding tools.

| Control | What to check |
| --- | --- |
| Your known Wi-Fi access point | SSID, signal strength and fresh/cached age labels. This is an access-point list, not a network-client list. |
| A known BLE advertiser | Name/RSSI if it advertises. A paired device is not guaranteed to advertise or appear. |
| A device advertising RTSP/HTTP/ONVIF via mDNS | A service name may appear on the same network. Service names do not confirm camera identity; not all cameras advertise these services. |
| Radios off / Location off / permissions denied | Helpful unavailable states; other tools continue working. |

Wait for the survey to finish. Avoid repeated Wi-Fi scan requests: Android normally allows four foreground requests per two minutes, and results may be cached. [Android scan rules](https://developer.android.com/develop/connectivity/wifi/wifi-scan)

## 5. Check sensor context and optional microphone

In **Sensors**, rotate the phone and cover/uncover the light sensor area. Inspect the exposed inventory and reading/status changes. A missing or restricted sensor should be shown as unavailable rather than fabricated.

If useful, in **Signals** tap **Enable microphone** in a quiet room, then **Stop microphone**. This is a coarse 15–22 kHz tone observation from 48 kHz input, not a camera detector. Silence or an unrelated tone is a valid result. No test tone or ultrasonic speaker is needed for the beginner check.

## 6. Check evidence and background behavior

1. In Sweep, tap **Save photo** while the camera runs.
2. In Notes, write a neutral observation, such as “Corner shelf: small reflection visible from two angles; unconfirmed.”
3. Tap **Share latest photo** and **Share report**. Choose a destination you control and verify the intended item. The report may contain nearby network/device identifiers; redact them before any public upload.
4. Press Home or lock the screen. Camera/microphone use should stop, along with scanning and sensor sampling. After returning, restart any tool you want to use.
5. After exporting anything you want to keep, use **Delete saved evidence** to check removal of local notes, photos and cached reports. Exported copies must be deleted separately.

## 7. Report what happened

Copy [the test-results template](test-results/TEMPLATE.md), fill in your configuration and mark checks **pass / fail / unavailable / not run**. Keep misses and false positives. Do not upload a raw inspection report, personal room photos, SSIDs/BSSIDs, Bluetooth identifiers or precise location publicly.

Submit a [device-test report](https://github.com/Feloguarin/LensGuard/issues/new/choose) or open a PR adding a sanitized Markdown result under `docs/test-results/`. A passed functional check does not certify a room as camera-free.

## Troubleshooting

| Problem | Next step |
| --- | --- |
| APK update rejected | Export evidence first; different evaluation signers may require uninstalling before installing. |
| Camera black/unavailable | Grant Camera access, close another camera app, return to Sweep and tap Start camera. Try rear/front selection. |
| Flashlight does not work | Start the camera first. Front cameras may have no flash; try the rear camera. |
| Baseline will not finish | Hold still, wait for fresh motion readings, move away from metal and follow low-accuracy guidance. |
| Wi-Fi empty or stale | Grant precise Location, enable Android Location/Wi-Fi, check cached age, then wait before another scan. |
| Bluetooth device missing | Check permissions and Bluetooth; the device must advertise BLE. Being paired is insufficient. |
| Microphone unavailable | Check permission and Android's microphone privacy control. Stop another recorder and retry. |
| No clue from a known camera | Record the conditions and miss. Concealed/offline/non-advertising cameras may produce no usable signal. |

For deeper testing, use [DEVICE_TEST_PLAN.md](DEVICE_TEST_PLAN.md). Developer installation/debugging instructions are in [CONTRIBUTING.md](../CONTRIBUTING.md).
