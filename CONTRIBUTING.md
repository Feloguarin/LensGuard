# Contributing to LensGuard

Thank you for helping make room-inspection tools more transparent and testable. LensGuard is public and [MIT-licensed](LICENSE). Fork the repository, make a focused change and open a pull request. Maintainer review and passing checks are required before a contribution is merged.

## Useful contributions

- Sanitized physical-device test reports, especially base Pixel 9 and API 28–30 phones.
- Reproducible fixes for permissions, lifecycle cleanup, accessibility or sensor availability.
- Translations (English and Spanish exist today) and clearer inspection instructions.
- Measured optical/magnetic/audio improvements with negative controls, misses and false positives.

Keep the distinction between an observation and a camera identification. Do not add unsupported accuracy percentages, thermal/IR promises or a room-clear verdict. Use respectful discussion and reproducible evidence; do not post personal rooms or device/network identifiers in public reports.

## Set up a development build

Install Git, **JDK 17**, and Android SDK platform 35, build-tools 35.0.0 and platform-tools. Android Studio can install these via SDK Manager. Accept the SDK licenses. Point `ANDROID_HOME`/`ANDROID_SDK_ROOT` at the SDK or create ignored `local.properties` with `sdk.dir=/absolute/path/to/sdk`.

```sh
git clone https://github.com/YOUR-USERNAME/LensGuard.git
cd LensGuard
git switch -c your-change
./gradlew assembleDebug
```

Replace `YOUR-USERNAME` with the account that owns your fork. Debug output is `app/build/outputs/apk/debug/app-debug.apk`.

On Windows, use `gradlew.bat`. Android Studio can also open the cloned root as a Gradle project.

### Install a debug build

On your own phone, enable Developer options and USB debugging; connect by USB and approve that computer's debugging prompt. Use the SDK's `adb`, targeting the correct device if more than one is attached:

```sh
adb devices
adb -s YOUR-DEVICE-SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s YOUR-DEVICE-SERIAL shell am start -W -n com.feloguarin.lensguard/.MainActivity
```

Replace `YOUR-DEVICE-SERIAL` with the identifier shown by `adb devices`. A debug APK and the GitHub evaluation APK have different signers; export evidence before uninstalling a conflicting build. USB debugging is optional for normal APK users. [Official adb documentation](https://developer.android.com/tools/adb)

### Build an installable release locally

On macOS/Linux, generate an ignored local development key, then run checks:

```sh
./scripts/prepare-development-key.sh
./gradlew lint test assembleRelease
```

On Windows without a POSIX shell:

```powershell
New-Item -ItemType Directory -Force signing | Out-Null
keytool -genkeypair -noprompt -keystore signing/development.p12 -storetype PKCS12 -storepass lensguard-development -keypass lensguard-development -alias lensguard -keyalg RSA -keysize 3072 -validity 3650 -dname "CN=LensGuard Evaluation, O=LensGuard"
.\gradlew.bat lint test assembleRelease
```

Only run the Windows key-generation command if the keystore does not already exist. Retaining the local key keeps repeat local builds compatible. Release output is `app/build/outputs/apk/release/app-release.apk`. The development password is not a production secret; the actual private keystore must still remain ignored and unshared.

For a persistent private release signer, provide `LENSGUARD_KEYSTORE`, `LENSGUARD_STORE_PASSWORD`, `LENSGUARD_KEY_ALIAS` and `LENSGUARD_KEY_PASSWORD` in the build environment. Keep credentials and the keystore outside source control.

### Persistent release signing

From 2.0.0, LensGuard's releases are signed with one permanent key so they update in place. The maintainer keeps the keystore private and backed up offline, and CI reads it from repository secrets; losing it would force everyone to uninstall again. A fork that publishes its own builds can set up its own key the same way:

```sh
keytool -genkeypair -keystore lensguard-release.p12 -storetype PKCS12 -alias lensguard \
    -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=LensGuard, O=LensGuard"
base64 -i lensguard-release.p12 | gh secret set LENSGUARD_KEYSTORE_BASE64   # Linux: base64 -w0
gh secret set LENSGUARD_STORE_PASSWORD
gh secret set LENSGUARD_KEY_ALIAS --body lensguard
gh secret set LENSGUARD_KEY_PASSWORD
```

The workflow uses the key only for pushes to `main`, never for pull requests, and each release's notes state which signer was used. Without the secrets, builds fall back to a temporary evaluation key, and those builds cannot update each other.

## Project map

| Path | Responsibility |
| --- | --- |
| `app/src/main/java/.../MainActivity.java` | Tab bar, lifecycle, permissions, sharing and shared state |
| `StartScreen`, `CameraScreen`, `NearbyScreen`, `SensorsScreen`, `ReportScreen` | One class per tab, built in code with `Ui` and `Screen` helpers |
| `LensCamera.java`, `ComparisonRun.java` | CameraX binding, frame analysis and the timed light on/off sequence |
| `GlintDetector`, `FrameGeometry`, `HighlightTracker`, `LightComparison` | Lens-finder math: highlights, preview mapping, steady tracking, comparison |
| `WirelessProbe`, `NetworkDiscovery`, `DiscoveryMessages`, `DeviceHints` | Bounded Wi-Fi/BLE/mDNS scan, ONVIF/UPnP discovery and leads |
| `SignalFollower`, `SignalTrend` | Following one Bluetooth signal |
| `SensorMonitor`, `AudioProbe`, `DetectionMath` | Exposed sensors, magnetic baseline, optional microphone analysis |
| `Inspection`, `Observation`, `Checklist`, `InspectionStore` | Inspections, checklist places, private storage and 1.x migration |
| `ReportBuilder`, `ReportPdf`, `Photos`, `Thumbnails` | JSON and PDF reports, photo decoding |
| `HighlightOverlay`, `SparklineView`, `SignalBarsView` | Custom views for rings, charts and signal bars |
| `app/src/main/res/values*/strings.xml` | English text and translations |
| `app/src/test/` | Pure numerical tests and Robolectric activity tests |
| `docs/` | Testing, hardware limits, results and branding |
| `.github/workflows/android.yml` | Checks and GitHub APK publication |

## Text and translations

All user-visible text lives in `app/src/main/res/values/strings.xml`; Java code never hard-codes it. Give every string the same name and the same format arguments in each translation (`values-es/` today); lint fails the build when a translation is missing. To add a language, copy `values-es/strings.xml` into a new `values-xx/` folder, translate it, and add the locale to `res/xml/locales_config.xml` so Android's per-app language setting lists it. Keep the cautious wording: a translation must not turn a clue into a verdict.

## Validate the change

Run `./gradlew lint test assembleRelease` for app/build changes. Numerical tests should check real behavior and failure cases; activity tests cover API 28 and 35. Use [the physical-device plan](docs/DEVICE_TEST_PLAN.md) for hardware changes and state which checks were not run. Emulator success is not Pixel 9 hardware validation.

For documentation-only changes, check relative links, instructions and asset rendering. Such changes do not require repeating sensor tests. Maintainers can use `[skip ci]` on a documentation-only commit to avoid publishing an identical APK with a fresh evaluation signer. PR checks do not publish releases or receive a production signing key; do not switch to `pull_request_target` to run untrusted contributor code.

## Pull request checklist

- Explain the problem, resulting behavior and relevant validation.
- Include the Android/device configuration for hardware observations.
- Record unavailable or unrun checks honestly.
- Preserve foreground cleanup, explicit optional permissions and local evidence handling.
- Keep APKs, build output, signing keys, credentials and raw private test reports out of the commit.
- Preserve the MIT copyright/license notice when redistributing.

Use [the issue templates](https://github.com/Feloguarin/LensGuard/issues/new/choose) for bugs, feature proposals or device-test reports. See [SECURITY.md](SECURITY.md) for sensitive reports and [docs/BRAND.md](docs/BRAND.md) for image/identity usage.
