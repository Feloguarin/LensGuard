# Contributing to LensGuard

Thank you for helping make room-inspection tools more transparent and testable. LensGuard is public and [MIT-licensed](LICENSE). Fork the repository, make a focused change and open a pull request. Maintainer review and passing checks are required before a contribution is merged.

## Useful contributions

- Sanitized physical-device test reports, especially base Pixel 9 and API 28–30 phones.
- Reproducible fixes for permissions, lifecycle cleanup, accessibility or sensor availability.
- Localization and clearer inspection instructions.
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

For a persistent private release signer, provide `LENSGUARD_KEYSTORE`, `LENSGUARD_STORE_PASSWORD`, `LENSGUARD_KEY_ALIAS` and `LENSGUARD_KEY_PASSWORD` in the build environment. Keep credentials and the keystore outside source control. Current GitHub CI uses temporary evaluation signing; it does not provision production secrets.

## Project map

| Path | Responsibility |
| --- | --- |
| `app/src/main/java/.../MainActivity.java` | Native Android screens, permissions, camera/evidence flow |
| `SensorMonitor.java` | Exposed sensors, lifecycle, stable magnetic baseline |
| `WirelessProbe.java` | Bounded Wi-Fi/BLE/mDNS survey |
| `AudioProbe.java` | Optional in-memory microphone analysis |
| `DetectionMath.java`, `GlintDetector.java` | Numerical and optical helpers |
| `app/src/test/` | Pure numerical and Robolectric activity tests |
| `docs/` | Testing, hardware limits, results and branding |
| `.github/workflows/android.yml` | Checks and GitHub APK publication |

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
