# Changelog

## Documentation and brand kit — 2026-10-04

- Reworked README with installation, quick testing, verification status and open-source entry points.
- Added beginner/device testing instructions, result template, contributor guide and issue/PR templates.
- Added charcoal/lime brand banner, editable lens/shield vectors and brand guidelines.
- Application behavior and APK code are unchanged by this documentation update.

## 1.1.0 evaluation — 2026-10-04

- Add a guided Start screen with three room-inspection steps.
- Separate Camera, Nearby and optional Tools; move raw sensors, magnetic comparisons, sound and notes into Tools.
- Add tap-to-focus for small objects and a stalled-frame status.
- Show explicit camera off/live states and an increasing analyzed-frame count. Disable photo/light controls until available.
- Fix radio status text that continued to say it was listening after a scan stopped.
- Add a nearby-scan countdown, observation counts and clear radio status; keep zoom/exposure out of the initial camera flow.
- Add navigation, saved-state and wireless countdown/cleanup regression tests on Android API 28 and 35.

## 1.0.0 evaluation — 2026-10-04

Initial GitHub release: [evaluation-2](https://github.com/Feloguarin/LensGuard/releases/tag/evaluation-2).

- Camera preview, flashlight, zoom/exposure and small-highlight observations.
- Stationary magnetic baseline, sensor inventory and environmental/motion context.
- Bounded Wi-Fi/BLE/local-service survey and optional local microphone analysis.
- Private photo/notes storage, report sharing and evidence deletion.
- Automated checks, signature/checksum assets and GitHub-hosted APK.

Build/lint/unit/activity checks passed; release install/launch passed in an Android 15 emulator. Physical Pixel 9 behavior and detection accuracy remain unverified.
