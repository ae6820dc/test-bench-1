# Android 16 native smoke test

A new, independent Java/platform Android app. No source was copied from another
Android project. There are no third-party runtime dependencies, native libraries,
permissions, background services, or refresh-rate controls.

The launcher is **Android 16 Test**. Its screen shows **Android test is running**,
the Android version/API level, and a counter updated every second while visible.
Insets keep content clear of system bars on Android 16. The counter survives
activity recreation, and its callbacks stop while the activity is paused.
This is a foreground launch test, not a persistent background service.

## Build locally

Use JDK 17, Gradle **8.13**, and Android SDK platform **36** / build tools **36.0.0**.
Gradle is explicitly provisioned by CI; this project does not include a wrapper.
Set `ANDROID_HOME` to your SDK or create an ignored `local.properties` containing
`sdk.dir=/absolute/path/to/android-sdk`.

```sh
cd android16-smoke-test
sdkmanager 'platforms;android-36' 'build-tools;36.0.0'
gradle --no-daemon :app:assembleDebug :app:lintDebug
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose app/build/outputs/apk/debug/app-debug.apk
```

Output: `app/build/outputs/apk/debug/app-debug.apk` (automatically debug-signed).
Package: `com.ae6820dc.androidsmoketest`. Minimum API 26; target/compile API 36.

## Install and verify

Connect an Android 16 device with USB debugging enabled, or boot an API 36 AVD:

```sh
bash scripts/smoke-test.sh app/build/outputs/apk/debug/app-debug.apk
```

The script verifies API 36, installation, launch, the visible screen and advancing
counter, unchanged process for 60 seconds, and pause/resume. It saves screenshot,
UI XML, activity state, and crash/logcat logs under ignored `smoke-results/`.
A real device run is still useful to check device-specific behavior.

## GitHub Actions

`.github/workflows/android16-smoke-test.yml` runs on relevant main pushes, pull
requests, and manual dispatch. It builds/lints/signature-checks the APK, uploads
`android16-smoke-test-debug-apk`, then installs that exact artifact on an Android
16/API 36 emulator and uploads `android16-runtime-evidence`.
Download and extract the APK artifact from the workflow run's **Artifacts** list.
The APK remains downloadable even if the separate runtime job fails.

## Verification status in this workspace

Build and device verification are pending: this workspace has no Android SDK,
Gradle, connected device, or emulator, and its configured network proxy refuses
connections, preventing toolchain downloads. Static checks are not a substitute
for an APK build. No successful build or Android 16 runtime test is claimed.
