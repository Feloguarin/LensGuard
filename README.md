# LensGuard

An Android inspection companion for Google Pixel 9 and other Android phones. LensGuard combines camera inspection, nearby wireless observations, magnetic-field readings, and environmental sensor context to help you choose objects to examine more closely.

**LensGuard cannot confirm that an object is a camera or prove that a room is camera-free.** Its observations are clues that need physical inspection. Concealed, offline, wired, sleeping, or non-advertising cameras may produce no useful observation.

## Install

1. Download [LensGuard.apk from the latest GitHub release](https://github.com/Feloguarin/LensGuard/releases/latest/download/LensGuard.apk).
2. Open the downloaded APK on your Android phone. If Android asks, allow the browser or file manager you used to install unknown apps, then install LensGuard.
3. Open LensGuard and grant only the permissions needed for the inspection tools you want to use. The app also works with individual tools unavailable or denied.

Requires Android 9 or newer (API 28+). Version 1.0.0 targets Android 15 (API 35). Pixel 9 is the intended reference device; **physical Pixel 9 testing has not been completed**. See the [device test plan](docs/DEVICE_TEST_PLAN.md).

### Development build signing

This is a development-signed evaluation build. No private signing key is committed to the repository. GitHub Actions generates a temporary development key for each build, so a later evaluation APK may have a different signer and require uninstalling the previous version before installation. Uninstallation removes private notes and photos; export anything you want to keep first. A privately stored, persistent release key is needed for trusted distribution and seamless updates.

## Inspection tools

| Tool | What it provides | What it does not establish |
| --- | --- | --- |
| Camera | Front/back preview, zoom, rear torch where available, and small bright-point hints | Bright reflections, lamps, glass, and shiny surfaces can trigger hints. A highlighted point is not a confirmed lens. |
| Magnetic sweep | Field strength in µT and change from a stationary baseline | Magnets, steel, speakers, chargers, and ordinary electronics can cause changes. This is not an RF detector. |
| Nearby wireless | Visible Wi-Fi access points, BLE advertisements, and advertised local services; signal strength and timestamps where available | A name, service, or strong signal does not identify a hidden camera. Wi-Fi access points are not a list of all connected devices. |
| Audio | Optional 15–22 kHz tone analysis from 48 kHz mono microphone input | There is no universal camera sound signature; the microphone and Android processing limit usable frequency response. |
| Sensor context | Exposed sensor inventory and available motion, light, proximity, and pressure readings | These support inspection conditions; they are not independent camera detections. |
| Evidence | Local private camera snapshots and notes, an optional JSON report shared through Android's share sheet, and an evidence-delete control | Observations and snapshots do not authenticate a device's purpose. |

Nearby discovery uses short, user-initiated sessions. Wi-Fi results can be cached or throttled by Android; inspect result ages and scan status. BLE devices and local services must advertise to appear. Signal strength changes with walls, orientation, and radio conditions and should not be treated as a distance estimate.

## A useful inspection routine

1. Move away from obvious magnets and electronics, hold the phone still, and set a magnetic baseline.
2. Inspect objects with a line of sight to sensitive areas. Use the camera and torch, change your viewing angle, and examine persistent bright points yourself.
3. Compare nearby wireless observations with devices you recognize. Treat unfamiliar names and services as leads to investigate.
4. Repeat suspicious observations from different positions and save a local snapshot or report if useful.

## Pixel 9 hardware and Android limits

Google lists proximity, ambient light, accelerometer, gyroscope, magnetometer, and barometer sensors for the base Pixel 9. Camera hardware also includes laser autofocus and spectral/flicker components. The phone has three microphones, Wi-Fi, Bluetooth, NFC, and GNSS. These hardware features do not all expose independent raw readings to ordinary Android apps. LensGuard inventories the sensors Android makes available, handles unavailable or restricted readings, and uses the public camera and radio APIs. It cannot access every proprietary sensor subsystem. [Google Pixel hardware specifications](https://support.google.com/pixelphone/answer/7158570?hl=en), [Android sensor framework](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview)

The base Pixel 9 has no object-temperature sensor, and LensGuard provides no thermal imaging. Google's thermometer feature is available on selected Pro models. Infrared sensitivity of a phone camera is not guaranteed; the visual tool does not promise infrared detection. [Google thermometer availability](https://support.google.com/pixelphone/answer/14103759)

Android's magnetometer API measures geomagnetic field strength, not radio-frequency transmissions. Sensor readings are collected while the app is active and listeners must be released when it pauses. [Android position sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_position), [Android sensor lifecycle guidance](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview)

Wi-Fi scan APIs require precise location permission and enabled Location services, including on modern Android versions. Android normally limits foreground applications to four requested scans per two minutes. Nearby-device permission does not replace the location requirement for `startScan()` and `getScanResults()`. [Wi-Fi scanning](https://developer.android.com/develop/connectivity/wifi/wifi-scan), [Wi-Fi permissions](https://developer.android.com/develop/connectivity/wifi/wifi-permissions)

BLE scanning needs Nearby devices permission on Android 12+. Devices that do not advertise cannot appear in its results. Audio input may be processed by the device; unprocessed capture availability must be checked rather than assumed. [Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions), [Android audio capture guidance](https://developer.android.com/media/platform/mediarecorder)

## Permissions and privacy

Camera permission enables preview and requested snapshots. Microphone permission enables optional live audio analysis. Precise location enables Android's Wi-Fi scan APIs; Bluetooth permissions enable nearby discovery. Sensor access depends on the exposed sensor and Android's permission rules. Local discovery uses network access to receive service advertisements. See [PRIVACY.md](PRIVACY.md) for data handling and sharing details.

## Build

Use JDK 17, the Android SDK with platform 35, and accepted SDK licenses. The checked-in Gradle wrapper uses Gradle 8.11.1 and Android Gradle Plugin 8.9.2. A release build needs a local development keystore at `signing/development.p12` or an externally supplied production keystore. Development key files must remain ignored by Git.

```sh
./scripts/prepare-development-key.sh
./gradlew lint test assembleRelease
```

The script uses JDK `keytool` to create the ignored local development key only if it is absent. A fresh CI runner gets a fresh evaluation identity; retaining the local key permits repeat local builds to share that local identity.

GitHub Actions runs lint, unit tests, and the release build for updates to `main`, then publishes the APK as the release asset `LensGuard.apk`. Build success checks compilation and automated rules; it does not establish detection accuracy or replace the physical-device test plan.

Initial validation passed locally: lint has no errors; all 21 tests pass for both debug and release builds, including activity startup/navigation/state checks on API 28 and 35. The signed release APK installed and launched successfully in an Android 15 emulator. Real Pixel 9 sensor accuracy and detection performance remain unverified.

For private production signing, provide `LENSGUARD_KEYSTORE`, `LENSGUARD_STORE_PASSWORD`, `LENSGUARD_KEY_ALIAS`, and `LENSGUARD_KEY_PASSWORD` in the build environment. Keep that keystore and its credentials outside the repository.

## License

See [LICENSE](LICENSE).
