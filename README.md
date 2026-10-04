![LensGuard — Inspect your space.](docs/assets/lensguard-banner.png)

# LensGuard

[![Android build](https://github.com/Feloguarin/LensGuard/actions/workflows/android.yml/badge.svg)](https://github.com/Feloguarin/LensGuard/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-B7F779?labelColor=0D1417)](LICENSE)
[![Android 9+](https://img.shields.io/badge/Android-9%2B-B7F779?labelColor=0D1417)](#install-on-your-phone)

**An open-source Android companion for inspecting your space.** Camera observations, magnetic readings and nearby wireless advertisements help you decide what to examine more closely. Designed for Google Pixel 9; adapts to the sensors Android exposes on other phones.

**[Download the APK](https://github.com/Feloguarin/LensGuard/releases/latest/download/LensGuard.apk)** · [Try it in 10 minutes](docs/TESTING.md) · [Contribute](CONTRIBUTING.md) · [Brand kit](docs/BRAND.md)

> LensGuard provides inspection clues. It cannot confirm a hidden camera or prove that a room is camera-free. This is a development-signed evaluation build; physical Pixel 9 validation is still pending.

*The banner is a conceptual brand illustration, not an app screenshot or a confirmed-camera example.*

## Install on your phone

1. On your Pixel 9, download **LensGuard.apk** from [GitHub Releases](https://github.com/Feloguarin/LensGuard/releases/latest). APKs are Android installers; no Android Studio or account is required to try the app.
2. Open the download. If Android requests it, allow installation from the specific browser/file manager you used, then follow the installer prompts. Keep Play Protect enabled and review any warning before proceeding. [Google's Play Protect guidance](https://support.google.com/pixelphone/answer/2812853?hl=en)
3. Open **LensGuard**. Grant permissions when you choose a tool. Enable Wi-Fi, Bluetooth and Android Location for a complete wireless survey.
4. Start with a known camera and ordinary household controls, following the [quick test](docs/TESTING.md).

**Requirements:** Android 9+ (API 28). Current app: 1.0.0; targets API 35. No Play Store listing yet.

**Updating an evaluation build:** CI generates a new development signing key for each APK. Android may reject an update with a different signer. Export your notes/photos first, uninstall the older evaluation app and install the new one. Uninstalling removes private app data. A persistent private release key is needed for seamless updates.

## What you can do

| Tab | Tools | How to interpret them |
| --- | --- | --- |
| **Sweep** | Front/rear camera, flashlight, zoom, exposure, small bright-point hints, private photo capture, stationary magnetic baseline | Compare reflections from several angles. Magnetic changes can come from metal, magnets or ordinary electronics. |
| **Signals** | 15-second Wi-Fi/BLE/local-service survey; optional 15–22 kHz sound analysis | Names, signal strength and tones are observations, not device identity. Wi-Fi scans list access points, not all devices on a network. |
| **Sensors** | Every Android-exposed sensor, raw readings/status, light/proximity/pressure/motion context; optional activity-sensor permission | Public hardware inventory; inaccessible streams are reported. Proprietary camera/biometric subsystems are not general raw sensors. |
| **Notes** | Private notes, JSON report sharing, latest-photo sharing, local evidence deletion | Evidence stays local until you explicitly share it. Review reports for nearby-device identifiers first. |

No accounts, ads, analytics or cloud detection. Camera frames and microphone samples are analyzed locally; audio is not recorded to storage. [Privacy details](PRIVACY.md)

## Your first 10-minute test

1. **Sweep → Start camera:** point at a visible webcam or another phone's camera. Toggle the light, move slowly and change angles. Try a shiny screw or glass too: these can also produce highlights.
2. **Calibrate baseline:** hold the phone still, away from metal, for about three seconds. Move near an ordinary speaker or steel object and compare the field/delta. A change is not a camera verdict.
3. **Signals → Scan · 15 seconds:** check whether your known Wi-Fi access point appears. Review fresh/cached labels. Bluetooth devices appear only if they advertise.
4. **Sensors:** cover/uncover the light sensor area and rotate the phone. Watch the available readings and unavailable statuses.
5. **Save photo**, then **Notes → Share report** or **Share latest photo**. Verify that sharing contains the observations you intended. Background the app and check that camera/microphone use stops.

Record passes, failures and misses instead of assuming detection worked. Follow the [full beginner guide](docs/TESTING.md), [physical-device checklist](docs/DEVICE_TEST_PLAN.md) and [test-results template](docs/test-results/TEMPLATE.md).

## What has actually been verified

| Check | Current evidence |
| --- | --- |
| Build and lint | Passed; lint has no errors. Non-fatal warnings remain. |
| Automated tests | 21 tests pass in both debug and release variants; startup/navigation/state tests cover API 28 and 35. |
| APK signature and download | Verified; GitHub release includes APK, SHA-256 checksum and signing-certificate information. |
| Android 15 emulator | Signed release APK installed and launched successfully. |
| Physical Pixel 9 behavior | **Pending community/device testing.** |
| Hidden-camera detection accuracy | **Not established.** No certified sensitivity, false-positive rate or room-clear claim. |

See [hardware and detection limits](docs/HARDWARE_AND_LIMITS.md) for official Google/Android sources and [CHANGELOG.md](CHANGELOG.md) for project history.

## Build from source

Install Git, JDK 17 and the Android SDK with platform 35 and build-tools 35.0.0. Android Studio can manage the SDK. Set `ANDROID_HOME`/`ANDROID_SDK_ROOT`, or put `sdk.dir=/absolute/path/to/your/sdk` in ignored `local.properties`.

```sh
git clone https://github.com/Feloguarin/LensGuard.git
cd LensGuard
./scripts/prepare-development-key.sh
./gradlew lint test assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`. Windows users can run `gradlew.bat`; see [CONTRIBUTING.md](CONTRIBUTING.md) for setup, development signing, installing debug builds and project structure.

The Gradle wrapper pins Gradle 8.11.1; Android Gradle Plugin is 8.9.2. GitHub Actions validates the app and publishes evaluation APKs for pushes to `main`. Pull requests run checks without publishing.

## Open source and contributions

The source and included brand assets are **MIT-licensed**: you may inspect, modify and redistribute them under the [license terms](LICENSE). The repository is public. You do not need permission to fork it or open a pull request.

Useful first contributions include real-device test reports, permission/lifecycle fixes, accessibility improvements, localization and better measured heuristics. Please avoid unsupported detection claims and keep private photos/network identifiers out of public reports.

- [Contributing guide](CONTRIBUTING.md)
- [Report a bug or device test](https://github.com/Feloguarin/LensGuard/issues/new/choose)
- [Security reporting](SECURITY.md)
- [Brand assets and usage](docs/BRAND.md)

Built by [Felipe Guarin](https://github.com/Feloguarin). Independent project; not affiliated with Google. Pixel is Google's product name.
