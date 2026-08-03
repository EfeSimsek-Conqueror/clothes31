#!/usr/bin/env bash
# Pin the device clock and put SysUI into demo mode so the status bar is clean
# (no debug/adb icons, full battery, 9:41) across every capture.
set -e
source /home/Koragan/projects/clothes31/android/env.sh
adb root >/dev/null 2>&1 || true; sleep 2
adb shell settings put global auto_time 0 >/dev/null
adb shell "date 082009412026.00" >/dev/null 2>&1 || true
adb shell settings put global sysui_demo_allowed 1 >/dev/null
b() { adb shell am broadcast -a com.android.systemui.demo "$@" >/dev/null; }
b -e command enter
b -e command clock -e hhmm 0941
b -e command battery -e level 100 -e plugged false
# `fully true` is what clears the "!" no-internet badge on the wifi glyph — the
# emulator has no real uplink, and without it every capture ships a broken-wifi icon.
b -e command network -e wifi show -e level 4 -e fully true
b -e command network -e mobile show -e datatype none -e level 4 -e fully true
b -e command notifications -e visible false
echo "device date: $(adb shell date | tr -d '\r')"
