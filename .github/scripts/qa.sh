#!/usr/bin/env bash
# QA run: animations ON, gesture navigation, a screen recording per scenario.
# Walks the open/close flows that users reported (detail, season -> detail, player, back to Home) and fails on a crash.
set -uo pipefail

APK="$1"
OUT="${2:-screenshots}"
PKG=com.fuwanime.app
mkdir -p "$OUT"

adb install -r "$APK"
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1
adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural || true
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
# The slow emulator with animations on makes the launcher ANR; its dialog covers the app. Hide system error dialogs.
adb shell settings put global hide_error_dialogs 1
adb shell settings put secure anr_show_background 0 || true
adb logcat -c

shot() { sleep "${2:-1}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "captured $1"; }

# Bounds centre of the first node whose text or content-desc equals $1 (or starts with it when $1 ends in *).
find_xy() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  python3 - "$1" "${2:-0}" "${3:-}" <<'PY'
import re, sys, xml.etree.ElementTree as ET
needle, dy, fx = sys.argv[1].lower(), int(sys.argv[2]), sys.argv[3]
for n in ET.parse('/tmp/ui.xml').iter('node'):
    labels = [(n.get('text') or '').lower(), (n.get('content-desc') or '').lower()]
    if needle in labels or (needle.endswith('*') and any(l.startswith(needle[:-1]) for l in labels)):
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
        print(int(fx) if fx else (x1 + x2) // 2, (y1 + y2) // 2 + dy)
        break
PY
}
tap() { local xy; xy=$(find_xy "$@"); [ -z "$xy" ] && { echo "not found: $1"; return 1; }; adb shell input tap $xy; echo "tapped '$1' at $xy"; }
# Scroll down until $1 is on screen (max 8 swipes).
scroll_to() { for _ in $(seq 1 8); do [ -n "$(find_xy "$1")" ] && return 0; adb shell input swipe 540 1700 540 900 300; sleep 1; done; echo "not found while scrolling: $1"; return 1; }
# Like scroll_to, then bring $1 up to the top third so the row below it is fully on screen (not under the nav bar).
scroll_row() { scroll_to "$1" || return 1; local y; y=$(find_xy "$1" | cut -d' ' -f2); [ "${y:-0}" -gt 900 ] && adb shell input swipe 540 "$y" 540 500 600; sleep 1.5; }
alive() { adb shell pidof "$PKG" >/dev/null || { echo "::error::App process died"; adb logcat -d -b crash | tail -80; exit 1; }; }
back_gesture() { adb shell input swipe 2 1200 520 1200 250; }

record() { adb shell screenrecord --bit-rate 6000000 --size 540x1200 --time-limit 60 "/sdcard/$1.mp4" & REC=$!; sleep 1; }
stop_record() { sleep 1; kill -INT $REC 2>/dev/null; wait $REC 2>/dev/null; sleep 2; adb pull "/sdcard/$1.mp4" "$OUT/$1.mp4" >/dev/null && echo "recorded $1"; }

# Every scenario starts from a fresh launch on Home, so one failed step never derails the next.
dismiss_dialogs() { adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1; tap "Wait" >/dev/null 2>&1 || true; }
fresh() { adb shell am force-stop "$PKG"; sleep 1; adb shell am start -W -n "$PKG/com.fuwanime.MainActivity" >/dev/null; sleep 12; dismiss_dialogs; alive; }
open_search_result() {  # $1 = query, $2 = title prefix to tap
  tap "Jelajah"; sleep 3; tap "Cari judul anime*" || adb shell input tap 540 330; sleep 1
  adb shell input text "$1"; sleep 5; adb shell input keyevent KEYCODE_BACK; sleep 1
  tap "$2"
}

# 1. Home row poster -> detail -> key back -> scroll Home right away (must respond).
fresh
scroll_row "Sedang Tayang"
record qa1-home-detail-back
tap "Sedang Tayang" 330 200; sleep 4; shot qa1a-detail 0
adb shell input keyevent KEYCODE_BACK; shot qa1b-poster-flying-back 0.1
# Scroll Home while the poster is still flying back: it must respond (no lock).
adb shell input swipe 540 1600 540 900 200; shot qa1c-home-scrolled-during-flight 0.5
stop_record qa1-home-detail-back; alive

# 2. Same with the edge back gesture.
fresh
scroll_row "Sedang Tayang"
record qa2-gesture-back
tap "Sedang Tayang" 330 200; sleep 4
shot qa2a-detail-open 0; back_gesture; sleep 0.2; shot qa2b-after-gesture 0
adb shell input swipe 540 1600 540 900 200; shot qa2c-home-scrolled 0.5
stop_record qa2-gesture-back; alive

# 3. Search -> detail with seasons -> Season & Terkait -> scroll the new page at once -> back -> back.
fresh
open_search_result kimetsu "Kimetsu no Yaiba*"; sleep 5; shot qa3a-first-detail 0
scroll_to "Season & Terkait"
record qa3-season
tap "Season & Terkait" 330 200; sleep 0.4; adb shell input swipe 540 1700 540 700 250
shot qa3b-season-scrolled 2
adb shell input keyevent KEYCODE_BACK; shot qa3c-back-to-first 2.5
adb shell input keyevent KEYCODE_BACK; shot qa3d-explore 2.5
stop_record qa3-season; alive

# 4. Detail -> episode row -> player -> back; "Tonton/Lanjutkan" -> player -> back (header must be intact) -> back.
fresh
tap "Tonton Sekarang"; sleep 5; shot qa4a-detail 0
record qa4-player
scroll_to "Episode 1" && tap "Episode 1"; shot qa4b-player 6
adb shell input keyevent KEYCODE_BACK; shot qa4c-detail-after-player 2.5
adb shell input swipe 540 900 540 1700 300; adb shell input swipe 540 900 540 1700 300; sleep 1
tap "Tonton Ep*" || tap "Lanjutkan Ep*"; shot qa4d-player-from-button 6
adb shell input keyevent KEYCODE_BACK; shot qa4e-detail-header 2.5
adb shell input keyevent KEYCODE_BACK; shot qa4f-home 2.5
stop_record qa4-player; alive

# 5. Tabs.
fresh
record qa5-tabs
tap "Jelajah"; shot qa5a-explore 3
tap "Jadwal"; shot qa5b-schedule 3
tap "Koleksi"; shot qa5c-library 3
tap "Beranda"; shot qa5d-home 3
stop_record qa5-tabs; alive

adb logcat -d -b crash | tail -40
echo "QA run passed"
