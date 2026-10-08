#!/usr/bin/env bash
set -euo pipefail
apk=${1:?Usage: smoke-test.sh /path/to/app-debug.apk}
results=${2:-smoke-results}
package=com.ae6820dc.androidsmoketest
component="$package/.MainActivity"
mkdir -p "$results"
trap 'adb logcat -d -v threadtime > "$results/logcat.txt"; adb shell dumpsys activity activities > "$results/activities.txt"' EXIT
adb wait-for-device
api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
[[ "$api" == 36 ]] || { echo "Expected Android 16/API 36; found API $api"; exit 1; }
adb install -r "$apk"
adb logcat -c
adb shell am start -W -n "$component" | tee "$results/launch.txt"
grep -q 'Status: ok' "$results/launch.txt"
pid=$(adb shell pidof "$package" | tr -d '\r')
[[ -n "$pid" ]]
# Detect crashes/restarts during a full minute in the foreground.
for ((i=0; i<30; i++)); do
    sleep 2
    current=$(adb shell pidof "$package" | tr -d '\r')
    [[ "$current" == "$pid" ]] || { echo 'App exited or restarted'; exit 1; }
done
adb shell uiautomator dump /sdcard/android-smoke.xml
adb pull /sdcard/android-smoke.xml "$results/screen.xml"
grep -q 'Android test is running' "$results/screen.xml"
grep -Eq 'Active for [1-9][0-9]+ seconds' "$results/screen.xml"
adb shell dumpsys activity activities > "$results/foreground.txt"
grep -E 'mResumedActivity|topResumedActivity' "$results/foreground.txt" | grep -F "$package"
adb exec-out screencap -p > "$results/screen.png"
# Exercise pause/resume as well as the initial launch.
adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell am start -W -n "$component" | tee "$results/resume.txt"
grep -q 'Status: ok' "$results/resume.txt"
sleep 2
[[ -n "$(adb shell pidof "$package" | tr -d '\r')" ]]
adb logcat -d -b crash > "$results/crashes.txt"
if grep -Fq "$package" "$results/crashes.txt"; then
    echo 'App crash detected'; exit 1
fi
echo 'PASS: installed, launched, visible heartbeat, 60-second survival, pause/resume on API 36.'
