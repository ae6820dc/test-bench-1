#!/usr/bin/env bash
set -euo pipefail
apk=${1:?Usage: smoke-test.sh app-debug.apk app-debug-androidTest.apk [results]}
test_apk=${2:?Test APK required}
results=${3:-smoke-results}
package=com.ae6820dc.hzlab
component="$package/.MainActivity"
mkdir -p "$results"
trap 'adb logcat -d -v threadtime > "$results/logcat.txt"; adb shell dumpsys activity activities > "$results/activities.txt"' EXIT
adb wait-for-device
api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
[[ "$api" == 36 ]] || { echo "Expected API 36, found $api"; exit 1; }
adb install -r "$apk"
adb install -r "$test_apk"
adb logcat -c
adb shell am instrument -w "$package.test/com.ae6820dc.hzlab.SmokeInstrumentation" | tee "$results/controls.txt"
grep -q HZ_LAB_CONTROLS_PASS "$results/controls.txt"
! grep -q HZ_LAB_FAILURE "$results/controls.txt"
# Instrumentation finishes its app process. Launch a fresh, uninstructed activity.
adb shell am start -W -n "$component" | tee "$results/launch.txt"
grep -q 'Status: ok' "$results/launch.txt"
sleep 3
pid=$(adb shell pidof "$package" | tr -d '\r')
[[ -n "$pid" ]]
adb shell uiautomator dump /sdcard/hz-lab-before.xml
adb pull /sdcard/hz-lab-before.xml "$results/before.xml"
grep -q 'Hz Lab' "$results/before.xml"
grep -q 'Jelentett frissítés' "$results/before.xml"
grep -q 'Automatikus' "$results/before.xml"
# Exercise a real Home/background transition, then resume the same process.
adb shell input keyevent KEYCODE_HOME
sleep 3
adb shell am start -W -n "$component" | tee "$results/resume.txt"
grep -q 'Status: ok' "$results/resume.txt"
sleep 3
[[ "$(adb shell pidof "$package" | tr -d '\r')" == "$pid" ]]
adb shell dumpsys activity activities > "$results/foreground.txt"
grep -E 'mResumedActivity|topResumedActivity' "$results/foreground.txt" | grep -F "$package"
adb shell uiautomator dump /sdcard/hz-lab-after.xml
adb pull /sdcard/hz-lab-after.xml "$results/after.xml"
grep -q 'Jelentett frissítés' "$results/after.xml"
grep -q 'Automatikus' "$results/after.xml"
adb exec-out screencap -p > "$results/screen.png"
adb logcat -d -b crash > "$results/crashes.txt"
if grep -Fq "$package" "$results/crashes.txt"; then echo 'App crash detected'; exit 1; fi
echo 'HZ_LAB_PASS: API 36 launch, controls, saved selection, 61s motion, Stop, Automatic, Home/resume. No physical Hz-switching claim.'
