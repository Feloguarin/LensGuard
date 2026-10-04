# Hardware and detection limits


Google lists proximity, ambient light, accelerometer, gyroscope, magnetometer, and barometer sensors for the base Pixel 9. Camera hardware also includes laser autofocus and spectral/flicker components. The phone has three microphones, Wi-Fi, Bluetooth, NFC, and GNSS. These hardware features do not all expose independent raw readings to ordinary Android apps. LensGuard inventories the sensors Android makes available, handles unavailable or restricted readings, and uses the public camera and radio APIs. It cannot access every proprietary sensor subsystem. [Google Pixel hardware specifications](https://support.google.com/pixelphone/answer/7158570?hl=en), [Android sensor framework](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview)

The base Pixel 9 has no object-temperature sensor, and LensGuard provides no thermal imaging. Google's thermometer feature is available on selected Pro models. Infrared sensitivity of a phone camera is not guaranteed; the visual tool does not promise infrared detection. [Google thermometer availability](https://support.google.com/pixelphone/answer/14103759)

Android's magnetometer API measures geomagnetic field strength, not radio-frequency transmissions. Sensor readings are collected while the app is active and listeners must be released when it pauses. [Android position sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_position), [Android sensor lifecycle guidance](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview)

Wi-Fi scan APIs require precise location permission and enabled Location services, including on modern Android versions. Android normally limits foreground applications to four requested scans per two minutes. Nearby-device permission does not replace the location requirement for `startScan()` and `getScanResults()`. [Wi-Fi scanning](https://developer.android.com/develop/connectivity/wifi/wifi-scan), [Wi-Fi permissions](https://developer.android.com/develop/connectivity/wifi/wifi-permissions)

BLE scanning needs Nearby devices permission on Android 12+. Devices that do not advertise cannot appear in its results. Audio input may be processed by the device; unprocessed capture availability must be checked rather than assumed. [Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions), [Android audio capture guidance](https://developer.android.com/media/platform/mediarecorder)


## Interpreting observations

A camera can be wired, offline, sleeping, hidden behind opaque material or absent from wireless advertisements. A shiny surface can look like a lens; a speaker or steel fixture can change a magnetic reading. No universal acoustic camera signature is established. LensGuard does not produce a camera probability or a room-clear verdict. Confirm an observation by inspecting the physical object; do not treat the app as a security certification.
