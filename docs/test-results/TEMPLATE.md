# Device test report template

Copy this file to a new Markdown file for your test run. Remove this introduction and replace every placeholder. Do not commit private photos, raw JSON reports, network/device identifiers, coordinates or credentials.

## Configuration

- Test date:
- GitHub release tag / commit:
- APK SHA-256:
- Model (for example, base Pixel 9):
- Android version / build:
- App version:
- Clean install or update:
- Permission choices / radios / Location state:

## Functional results

Use **pass / fail / unavailable / not run**. Do not infer a pass from a skipped test.

| Check | Status | Sanitized observation |
| --- | --- | --- |
| Install and launch | | |
| Rear/front camera, zoom and exposure | | |
| Rear torch / front unavailable state | | |
| Photo and report sharing | | |
| Stable magnetic baseline | | |
| Known metal/magnet negative control | | |
| Wi-Fi freshness and scan completion | | |
| Known BLE advertiser | | |
| Advertised local service | | |
| Sensor inventory, light and motion | | |
| Microphone granted/denied/stopped | | |
| Optional permissions denied | | |
| Background/lock cleanup | | |
| Notes restore and evidence deletion | | |

## Controlled camera observations

| Target / control | Lighting, distance, angle, torch | Observation | Miss / false positive / useful clue |
| --- | --- | --- | --- |
| Known visible camera lens | | | |
| Shiny screw / glass / LED | | | |
| Known wired/offline/non-advertising camera, if available | | | |

Record all conditions and misses. A useful clue is not a confirmed camera classification, and this table does not establish a sensitivity rate without a suitable study.

## Failures and follow-up

For each failure: expected behavior, actual behavior, minimal reproduction, whether it repeats and sanitized screenshots/log excerpts if relevant. Omit or redact private surroundings and identifiers. Link the related issue if you opened one.

## Scope

List what was not run and why. This report describes behavior on the recorded configuration. It does not certify a room as camera-free.
