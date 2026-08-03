#!/usr/bin/env bash
set -e
SP=/home/Koragan/projects/clothes31/android/store/screenshots/harness
# Captures land straight in raw/<device>/, which is what compose_store2.py reads.
SHOTS=${SHOTS:-/home/Koragan/projects/clothes31/android/store/screenshots/raw}
LOGS=${LOGS:-/tmp}
cd /home/Koragan/projects/clothes31/android && source ./env.sh
# Phone only. Add `fit_tab7:tab7 fit_tab10:tab10` back to re-run the tablet sets.
for avd in fit_phone:phone; do
  A=${avd%%:*}; D=${avd##*:}
  adb emu kill >/dev/null 2>&1 || true; sleep 5
  nohup emulator -avd $A -gpu swiftshader_indirect -no-audio -no-boot-anim -no-snapshot > $LOGS/emu_$D.log 2>&1 &
  sleep 6; adb wait-for-device
  for i in $(seq 1 90); do b=$(adb shell getprop sys.boot_completed 2>/dev/null|tr -d '\r'); [ "$b" = "1" ] && break; sleep 3; done
  echo "== $A booted ($(adb shell wm size|tr -d '\r'))"
  adb install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null 2>&1
  $SP/clean_ui.sh
  rm -f $SHOTS/$D/*.png
  $SP/capture.sh $SHOTS/$D
done
adb emu kill >/dev/null 2>&1 || true
echo ALLDONE
