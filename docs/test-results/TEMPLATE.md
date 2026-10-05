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
| In-place update from the previous release | | |
| Checklist places, statuses and notes | | |
| Rear/front camera, zoom and exposure | | |
| Rings line up with highlights; steady state | | |
| Light on/off comparison and light restored | | |
| Rear torch / front unavailable state | | |
| Photo, PDF/JSON report and photo sharing | | |
| Addresses hidden/included in reports | | |
| Stable magnetic baseline and chart | | |
| Known metal/magnet negative control | | |
| Wi-Fi freshness and scan completion | | |
| Known BLE advertiser and Follow signal | | |
| Advertised local service | | |
| ONVIF/UPnP replies on own Wi-Fi | | |
| Leads and false leads | | |
| Sensor inventory, light and motion | | |
| Microphone granted/denied/stopped | | |
| Optional permissions denied | | |
| Background/lock cleanup | | |
| Notes restore and evidence deletion | | |
| Spanish UI and TalkBack headings | | |

## Controlled camera observations

| Target / control | Lighting, distance, angle, torch | Ring, steady, comparison result | Miss / false positive / useful clue |
| --- | --- | --- | --- |
| Known visible camera lens | | | |
| Shiny screw / glass / mirror | | | |
| Powered LED / phone screen | | | |
| Known wired/offline/non-advertising camera, if available | | | |

Record all conditions and misses. A useful clue is not a confirmed camera classification, and this table does not establish a sensitivity rate without a suitable study.

## Failures and follow-up

For each failure: expected behavior, actual behavior, minimal reproduction, whether it repeats and sanitized screenshots/log excerpts if relevant. Omit or redact private surroundings and identifiers. Link the related issue if you opened one.

## Scope

List what was not run and why. This report describes behavior on the recorded configuration. It does not certify a room as camera-free.
