# Hz Lab

A separate native Java experiment with Hungarian controls. Application ID
`com.ae6820dc.hzlab`; it can coexist with `com.ae6820dc.androidsmoketest`.
The working launch-test project is preserved. Minimum API 31, target/compile API
36; JDK 17, AGP 8.11.1, Gradle 8.13, build tools 36.0.0 reuse the verified toolchain.

## What it requests

- `Display.getRefreshRate()`, `getMode()` and `getSupportedModes()` expose the
  Android-reported refresh rate, active mode, resolution and all supported modes.
  These are OS reports, not a measurement of physical panel switching. On adaptive
  refresh displays the reported refresh rate can differ from a mode's nominal rate.
- Supported mode selection uses `WindowManager.LayoutParams.preferredDisplayModeId`
  only for modes with the current mode's exact physical width and height.
  The app revalidates that restriction immediately before requesting a mode and
  clears unavailable mode preferences after display changes. Mode IDs are not
  persisted; saved rate/resolution are matched to current modes on relaunch.
- Custom selection uses `preferredRefreshRate`, with explicit mode ID cleared.
  Values start at 30 and advance by 1 Hz to the maximum across all reported modes;
  a fractional maximum is included as a final endpoint. It does not create modes.
  The system chooses whether/how to honor the preference and may override it.
- Automatic clears both public preferences set by this app:
  `preferredDisplayModeId` and `preferredRefreshRate`, and removes the saved selection. No Surface/View frame-rate requests are set by the app.
- Apply saves the selection in app-private preferences. It does not report a
  successful physical switch. The requested value and independently reported
  Android state always appear separately.

There are no permissions, hidden APIs, root requirements, settings writes, or
phone-wide controls. Display information updates on resume, DisplayManager events
and a one-second foreground poll. Edge-to-edge insets include system bars,
display cutouts and IME; the screen is scrollable and uses light-bar contrast.

The optional motion test uses `Choreographer` and elapsed frame timestamps for
smooth time-based movement. Its callback count is **not** a display-Hz or physical
frame-timing measurement. Callbacks stop on Stop and while backgrounded; an active
test resumes with the activity. A selection applies only to this app's window.

Public API references:
- https://developer.android.com/reference/android/view/Display
- https://developer.android.com/reference/android/view/WindowManager.LayoutParams
- https://developer.android.com/reference/android/hardware/display/DisplayManager.DisplayListener
- https://developer.android.com/reference/android/view/Choreographer
- https://developer.android.com/develop/ui/views/layout/edge-to-edge

## Build and validate

With JDK 17, Gradle 8.13 and `ANDROID_HOME` configured:

```sh
cd hz-lab
sdkmanager 'platforms;android-36' 'build-tools;36.0.0'
gradle --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose app/build/outputs/apk/debug/app-debug.apk
bash scripts/smoke-test.sh app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

The smoke script requires an API 36 emulator/device. Its dependency-free test
instrumentation invokes the real UI handlers and inspects window attributes:
resolution-safe mode choice, 30 Hz, +/-1 Hz, exact maximum, persisted 31 Hz request,
relaunch, Automatic clearing, motion Start/Stop and at least 61 seconds of operation.
ADB in the **test harness** then verifies a normal launch and Home/background/resume
with the same process, visible display info and reset state. The app requests no
ADB/system permissions. Logs, UI XML and screenshot are uploaded as evidence.
These tests verify app behavior, not physical refresh-rate switching on a phone.

`.github/workflows/hz-lab.yml` installs the SDK/Gradle dependencies, builds/lints,
verifies the debug APK signature, uploads `hz-lab-debug-apk`, and runs the API 36
smoke test with that exact APK. `hz-lab-test-apk` is test-only, not the user app.
Validation runs first on `hz-lab-validation`, then main after successful checks.

## Actual validation result

[GitHub Actions run 37825181557](https://github.com/ae6820dc/test-bench-1/actions/runs/37825181557)
passed on 2026-10-08 for commit `6ae54913357336adfa0b79bc2f78238261ca7141`.
That exact tested commit was promoted to main after successful checks.

- `build`: success; app and test APK build, Android lint, and app signature
  verification (APK Signature Scheme v2) passed.
- `android16-runtime`: success; API 36 install/launch, native control assertions,
  supported-mode resolution safety, custom bounds and steps, saved-selection
  relaunch, Automatic clearing, 61 seconds of motion callbacks, Stop, and normal
  Home/background/resume passed.
- [Download debug app APK artifact](https://github.com/ae6820dc/test-bench-1/actions/runs/37825181557/artifacts/11570733478)
  and extract `app-debug.apk`. Do not install the separate test APK for normal use.
- [Runtime evidence](https://github.com/ae6820dc/test-bench-1/actions/runs/37825181557/artifacts/11571690245)
  includes screenshot, control assertions, UI XML, launch/resume and crash logs.

The emulator exposes a 60 Hz mode. Requesting another Hz preference was tested by
inspecting app window attributes; no physical panel switching is proven by this
result. Hz Lab has not yet been validated on a physical Android 16 phone.
