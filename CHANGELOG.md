# Changelog

## Unreleased

- Install help: clearer steps for people who had 1.0 or 1.1, and a table of Android installer messages with what to do about each.

## 2.0.0 evaluation — 2026-10-04

A guided inspection, a lens finder that shows where highlights are, network discovery and shareable reports.

**Inspect**

- Start is now an inspection: a checklist of 12 common hiding places with guidance. Mark each place *Inspected* or *Needs a closer look*, and add notes per place.
- Opening a tool from a place links what you save to it; a banner shows the active place.
- Keep several inspections (for example, one per room or trip). Rename, switch and delete them.

**Camera — lens finder**

- Rings on the preview mark each small highlight. Highlights that stay in place while you hold still are marked *steady*.
- New **Compare light on/off**: LensGuard records highlights with the flashlight on, then off. Points that disappear without the light reflected it, as a lens can and as glass and metal also do. Points that stay are light sources or reflections of room light. The comparison warns when the phone moved.
- Analysis uses 1280×960 frames and keeps the brightest pixel in each block, so a one-pixel glint is no longer skipped by subsampling. Analysis is cropped to exactly what the preview shows.
- Sweep controls sit directly under the preview; the screen explains when the selected camera has no flashlight.

**Nearby**

- Results are a structured list with signal bars, freshness and a **Leads to inspect** section. Leads are camera-like names, RTSP/ONVIF video services and devices that describe a video role. A lead is never presented as proof.
- On the connected Wi-Fi network, the scan sends standard ONVIF WS-Discovery and UPnP SSDP requests and lists the devices that answer. It never connects to or logs in to them.
- **Follow signal**: track one Bluetooth advertiser's strength for 60 seconds with a trend and chart to guide a physical search. It is not a distance.
- Save a scan to the report.

**Sensors and report**

- The magnetic check adds a 30-second chart against the baseline and the largest change since baseline. Magnetic and sound readings can be saved.
- Notes, photos, scans and readings become observations in one log, with thumbnails and per-item delete.
- Share a **PDF report** with limits on its first page, plus the checklist, observations, photos and leads. A versioned **JSON report** (`lensguard-report`, format 2) and all photos can also be shared. Device addresses are partly hidden unless you include them.
- A 1.x note and saved photos move into an imported inspection on first launch.

**Languages, accessibility and quality**

- Spanish translation, with LensGuard listed in Android's per-app language settings on Android 13+.
- Screen titles and section labels are headings for screen readers; status, comparison and trend updates are announced politely; buttons give touch feedback.
- MainActivity is split into one class per tab plus tested components for highlight tracking, light comparison, discovery parsing, hints, signal trends and storage.
- Lint warnings drop from 13 to the 5 dependency-update notices.
- Releases are signed with a permanent release key kept in repository secrets and used only for pushes to `main`, so later downloads install as updates. Versions 1.0 and 1.1 used temporary keys; uninstall them once before installing 2.0. CI names each release from the app version.

Build, lint and 78 automated tests (per build variant) pass. The Android 15 emulator checks are in [the 2.0 test record](docs/test-results/2026-10-04-emulator-2.0.md). Physical Pixel 9 behavior and detection accuracy remain unverified.

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
