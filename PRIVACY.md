# LensGuard privacy

LensGuard is designed to analyze inspection observations on your phone. It does not use an account, analytics service, advertising SDK, or cloud upload endpoint.

## Data used by each tool

| Data | Use and storage |
| --- | --- |
| Camera frames | Live preview and bright-point analysis. A snapshot is saved only when you request one, in the app's private storage. |
| Inspection notes | Text you enter is saved in the app's private storage and included in a report when you request sharing. |
| Microphone samples | Optional live audio analysis. Raw microphone recordings are not saved. |
| Sensor readings | Live measurements, baseline comparison, and an optional inspection report. Readings alone do not identify a camera. |
| Wi-Fi scan results | Nearby access-point names, identifiers, signal strength, and result timing, subject to Android access restrictions. |
| Bluetooth advertisements | Nearby names, identifiers, advertised information, signal strength, and timestamps when Android makes them available. |
| Local service advertisements | Nearby network service information that devices advertise on the local network. |

Wireless discovery observes information that nearby devices expose through Android scanning and local service discovery. It does not log into devices, guess credentials, or access camera streams.

## Permissions

- **Camera:** for the visual inspection tool and requested snapshots.
- **Microphone:** for optional live audio analysis; permission denial leaves other tools available.
- **Precise location:** required by Android for Wi-Fi scanning and its results. This requirement applies even when an app does not request a GPS position. Location services must also be enabled. [Android Wi-Fi permission requirements](https://developer.android.com/develop/connectivity/wifi/wifi-permissions)
- **Nearby devices / Bluetooth:** for BLE scanning and permitted device information. Older Android versions use location-related rules for Bluetooth discovery. [Android Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- **Physical activity:** for exposed step-related sensor readings, if you enable access to those inventory sensors. It is not needed by the camera or magnetic tools.
- **Wi-Fi and network access:** to request/read Wi-Fi scans and discover advertised services on your local network.

Some exposed sensors have their own Android permission requirements. The app must handle inaccessible readings without treating them as measurements. Android does not provide raw access to every physical subsystem through the sensor framework. [Android sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview)

Inspections run while you use the app. Camera, audio, discovery, and sensor resources are stopped when the app leaves the foreground.

## Evidence and sharing

Saved snapshots and notes remain in the app's private storage until you delete them, clear app data, or uninstall. A JSON report is created in private cache when you request sharing. If you choose to share a snapshot or report, Android's share sheet gives the receiving app access to the item you selected. That app's handling of the shared information is outside LensGuard's control.

Reports can contain wireless device identifiers, nearby network names, timestamps, and inspection readings. Check a report before sharing it; these details can reveal information about your surroundings.

## Removing data

Use **Notes → Delete saved evidence** to remove private photos, notes, and cached reports. Alternatively, use Android Settings → Apps → LensGuard → Storage & cache → Clear storage, or uninstall the app. Copies that you exported or shared must be removed separately.

## Evaluation status

Version 1.0.0 is a development-signed evaluation build. No private signing key is committed; CI generates a temporary development identity for each build. Installing a later APK with a different signer may require uninstalling the previous version, which removes private app data. Physical Pixel 9 verification and the device privacy checks in [DEVICE_TEST_PLAN.md](docs/DEVICE_TEST_PLAN.md) have not yet been completed.
