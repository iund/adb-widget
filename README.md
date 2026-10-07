# adb-widget

A tiny home-screen widget that shows your device's `adb` over Wi-Fi address (`192.168.x.x:5555`) and
toggles it on and off with a tap. Grey = off, blue = on.

Works from Android 3.0 (API 11) up to current Android. Nothing is written to `/system`.

## How it works

| Device | Mechanism |
|---|---|
| Rooted (`su` allowed for the app) | Sets `service.adb.tcp.port` and restarts `adbd`. Fixed port (default 5555), re-applied at boot if it was on. |
| Not rooted, Android 11+ | Toggles **Wireless Debugging** (`adb_wifi_enabled`). The port is random, so the widget finds the live one over mDNS. |

The non-root path needs a one-time grant over adb:

```sh
adb shell pm grant x.adbwifi android.permission.WRITE_SECURE_SETTINGS
```

## Install

Download the APK from [Releases](../../releases) and `adb install adbwifi-*.apk`, then add the
**ADB WiFi** widget from the home screen widget picker. (The app has no launcher icon; adding the
widget is what starts it, which is also what lets it receive the boot broadcast.)

To choose a different fixed port on a rooted device:

```sh
adb shell am broadcast -a x.adbwifi.CONFIG -n x.adbwifi/x.AdbWidget --ei port 5555
```

## Build

```sh
./build.sh        # needs the Android SDK (ANDROID_HOME) and a JDK; outputs build/adbwifi.apk
```

## Releases

Pushing a tag like `v1.0.0` runs the workflow in `.github/workflows/release.yml`, builds the APK and
publishes it as a GitHub release. Without signing secrets it signs with a throwaway key; set the
repo secrets `KEYSTORE_BASE64`, `KS_PASS` and `KEY_ALIAS` to sign with a stable key.
