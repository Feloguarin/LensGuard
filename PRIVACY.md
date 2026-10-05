# LensGuard privacy

LensGuard is designed to analyze inspection observations on your phone. It does not use an account, analytics service, advertising SDK, or cloud upload endpoint.

## Data used by each tool

| Data | Use and storage |
| --- | --- |
| Camera frames | Live preview, bright-point analysis and the light on/off comparison, in memory. A photo is saved only when you tap **Save photo**, in the app's private storage. |
| Inspections and notes | Checklist statuses, notes, and the photos, scans and readings you choose to save are kept as inspections in the app's private storage, and included in a report when you share one. An unsaved note draft is kept privately until you save or delete it. |
| Microphone samples | Optional live audio analysis. Raw microphone recordings are not saved. |
| Sensor readings | Live measurements, baseline comparison, and an optional inspection report. Readings alone do not identify a camera. |
| Wi-Fi scan results | Nearby access-point names, identifiers, signal strength, and result timing, subject to Android access restrictions. |
| Bluetooth advertisements | Nearby names, identifiers, advertised information, signal strength, and timestamps when Android makes them available. |
| Local network discovery | During a scan, when Wi-Fi is connected, LensGuard sends standard ONVIF WS-Discovery and UPnP SSDP requests on that network and records each answering device's address, the name and model it reports and its device type. It also lists RTSP, HTTP and ONVIF services that devices advertise. |
| Followed Bluetooth signal | While you use **Follow signal**, signal strength from one device for up to 60 seconds, in memory unless you save it. |

Wireless discovery observes information that nearby devices expose through Android scanning and standard discovery. Discovery requests are visible to devices on the same network, as with any media or network app. LensGuard does not connect to devices, log into them, guess credentials or access camera streams.

## Permissions

- **Camera:** for the visual inspection tool and requested snapshots.
- **Microphone:** for optional live audio analysis; permission denial leaves other tools available.
- **Precise location:** required by Android for Wi-Fi scanning and its results. This requirement applies even when an app does not request a GPS position. Location services must also be enabled. [Android Wi-Fi permission requirements](https://developer.android.com/develop/connectivity/wifi/wifi-permissions)
- **Nearby devices / Bluetooth:** for BLE scanning and permitted device information. Older Android versions use location-related rules for Bluetooth discovery. [Android Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- **Physical activity:** for exposed step-related sensor readings, if you enable access to those inventory sensors. It is not needed by the camera or magnetic tools.
- **Wi-Fi and network access:** to request/read Wi-Fi scans, send discovery requests and receive answers and advertised services on your local network. The app has no cloud endpoint.

Some exposed sensors have their own Android permission requirements. The app must handle inaccessible readings without treating them as measurements. Android does not provide raw access to every physical subsystem through the sensor framework. [Android sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview)

Inspections run while you use the app. Camera, audio, discovery, signal following and sensor resources are stopped when the app leaves the foreground.

## Evidence and sharing

Saved inspections, photos and notes remain in the app's private storage until you delete them, clear app data, or uninstall. A PDF or JSON report is created in private cache when you request sharing; copies older than an hour are removed the next time you share a report, and all copies are removed with your evidence. If you choose to share a photo or report, Android's share sheet gives the receiving app access to the items you selected. That app's handling of the shared information is outside LensGuard's control.

Reports can contain nearby network and device names, timestamps, notes, photos and inspection readings. Device hardware and network addresses are partly hidden in reports unless you switch **Addresses** to *included* on the Report tab; the copy kept on your phone is unchanged. Check a report before sharing it; these details can reveal information about your surroundings.

## Removing data

Use **Report → Delete all saved evidence** to remove every inspection, photo, note and cached report, or delete single observations and inspections from the Report tab. Alternatively, use Android Settings → Apps → LensGuard → Storage & cache → Clear storage, or uninstall the app. Copies that you exported or shared must be removed separately.

## Evaluation status

Version 2.0.0 is an evaluation build. No private signing key is committed. From 2.0.0, CI signs releases with a permanent release key kept in repository secrets, so later releases install as updates and keep your data. Versions 1.0 and 1.1 used temporary keys; moving from them to 2.0 requires uninstalling, which removes private app data. Physical Pixel 9 verification and the device privacy checks in [DEVICE_TEST_PLAN.md](docs/DEVICE_TEST_PLAN.md) have not yet been completed.
