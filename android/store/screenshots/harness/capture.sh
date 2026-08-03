#!/usr/bin/env bash
# Walk the app and capture each screen. Coordinates are given as fractions of the
# display so the same script drives the phone and both tablet AVDs.
set -euo pipefail
source /home/Koragan/projects/clothes31/android/env.sh

OUT="${1:?usage: capture.sh <outdir>}"
mkdir -p "$OUT"
read -r W H < <(adb shell wm size | tr -d '\r' | sed 's/.*: //' | tr 'x' ' ')
echo "display ${W}x${H} -> $OUT"

tap()  { adb shell input tap $(python3 -c "print(int($W*$1), int($H*$2))"); sleep "${3:-2.5}"; }
UI="/home/Koragan/projects/clothes31/android/store/screenshots/harness/uidriver.py"
anr()  { python3 "$UI" anr >/dev/null 2>&1 || true; }
tapt() { python3 "$UI" tap "$1" "${2:-2.5}" || echo "    MISS: $1"; }
shot() { anr; sleep 1.2; adb exec-out screencap -p > "$OUT/$1.png"; echo "  $1"; }
back() { adb shell input keyevent 4; sleep 2; }
scroll() { adb shell input swipe $((W/2)) $((H*7/10)) $((W/2)) $((H*3/10)) 400; sleep 1.5; }
# Shorter drag, for screens where a full page jump lands mid-element.
nudge() { adb shell input swipe $((W/2)) $((H*72/100)) $((W/2)) $((H*46/100)) 450; sleep 1.5; }

restart() {
  adb shell am force-stop com.fitrater.app
  adb shell am start -n com.fitrater.app/.MainActivity > /dev/null
  sleep 9
}

# Bottom nav sits at ~92.5% height; the five slots are evenly spread.
NAV_Y=0.925
nav() { tap "$1" "$NAV_Y" 3; }

restart
shot 01_home
scroll; shot 02_home_scrolled

# Score detail — tap the LATEST hero card.
restart
tap 0.5 0.66 3.5
shot 03_score_detail
nudge; nudge; shot 04_score_breakdown
scroll; shot 05_score_swaps

restart
nav 0.30; shot 06_studio
restart
nav 0.72; shot 07_journal
nudge; nudge; shot 08_journal_grid
restart
nav 0.91; shot 09_you

# Camera menu — the centre action button.
restart
tap 0.5 "$NAV_Y" 3
shot 10_camera_menu

# Credit / Pro features. Driven by visible text so the same script works on the
# phone and both tablets, where the sheet lays out differently.
#   row label | GO | action button | input shot | result shot
run_tool() {
  restart
  tap 0.5 "$NAV_Y" 3          # centre camera button has no text node
  tapt "$1" 3
  tapt "GO →" 5
  shot "$4"
  tapt "$3" 9
  shot "$5"
}
# Try-on opens straight on the seeded result (the harness presets resultUrl), so there
# is no action button to press — go in and shoot, then scroll for the buttons below.
restart
tap 0.5 "$NAV_Y" 3
tapt "Try on" 3
tapt "GO →" 6
shot 18_tryon_result
scroll; shot 19_tryon_result_scrolled

run_tool "Roast this"   "" "Roast it"        11_roast_input  12_roast_result
run_tool "A vs B"       "" "Let Hem call it" 13_versus_input 14_versus_result
run_tool "Decode style" "" "Decode ·"          15_decode_input 16_decode_result

# Weekly letter (Pro) via the You sheet.
restart
nav 0.91; sleep 1
tap 0.5 0.60 3
shot 17_weekly_letter

echo "done -> $OUT"
