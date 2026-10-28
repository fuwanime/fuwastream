#!/usr/bin/env bash
# Installs the APK on the CI emulator, walks the main screens and captures screenshots.
# Fails if the app process crashes at any point.
set -uo pipefail

APK="$1"
OUT="${2:-screenshots}"
PKG=com.fuwanime.app
mkdir -p "$OUT"

adb install -r "$APK"
adb logcat -c

shot() {
  sleep "${2:-2}"
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "captured $1"
}

# Taps the first on-screen node whose text or content-desc contains $1.
tap() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  local xy
  xy=$(python3 - "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
needle = sys.argv[1].lower()
for n in ET.parse('/tmp/ui.xml').iter('node'):
    labels = [(n.get('text') or '').lower(), (n.get('content-desc') or '').lower()]
    exact = needle in labels
    if exact or (needle.endswith('*') and any(l.startswith(needle[:-1]) for l in labels)):
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)
  if [ -z "$xy" ]; then echo "not found: $1"; return 1; fi
  adb shell input tap $xy
  echo "tapped '$1' at $xy"
}

alive() {
  if ! adb shell pidof "$PKG" >/dev/null; then
    echo "::error::App process died"
    adb logcat -d -b crash | tail -80
    exit 1
  fi
}

adb shell am start -W -n "$PKG/com.fuwanime.MainActivity"
shot 01-home 15; alive
adb shell input swipe 540 1600 540 500 400
shot 02-home-scrolled 3; alive

tap "Jelajah" && shot 03-explore 8; alive
tap "Action" && shot 04-explore-action 8; alive
# Clear the genre, then filter by type on the server.
tap "Action" && tap "Tipe" && sleep 1 && tap "Film" && shot 04b-explore-film 8; alive
tap "Jadwal" && shot 05-schedule 8; alive
tap "Koleksi" && shot 06-library 3; alive
tap "Beranda" && sleep 3
tap "Pengaturan" && shot 07-settings 3; alive
adb shell input keyevent KEYCODE_BACK; sleep 2

# Open the first hero title and start playback.
adb shell input swipe 540 500 540 1600 300; sleep 2
tap "Tonton Sekarang" && shot 08-detail 10; alive
adb shell input swipe 540 1600 540 700 400
shot 09-detail-episodes 3; alive
tap "Download" && shot 09b-detail-download 10; alive
adb shell input swipe 540 700 540 1600 400; sleep 2
tap "Tonton Ep*" && shot 10-player 20; alive
adb shell input tap 540 300
shot 11-player-controls 1; alive
# Seek checks. Controls auto-hide, so re-show them before each interaction.
controls() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml | grep -q 'Maju 10 detik' || { adb shell input tap 540 300; sleep 0.5; }
}
sleep 1                                        # seek right after start: index may not be loaded yet
controls; tap "Maju 10 detik"
sleep 8
controls; tap "Maju 10 detik"; tap "Maju 10 detik"
sleep 3
controls; adb shell input swipe 120 704 560 704 400   # drag the seek bar to ~half
sleep 5
controls; shot 11b-after-seek 1; alive
controls; tap "Pengaturan video" && sleep 2 && adb shell input swipe 540 2000 540 1200 300 && sleep 1 && tap "1.5x" && sleep 2
adb shell input keyevent KEYCODE_BACK; sleep 3
controls; shot 11c-after-speed 1; alive
adb logcat -d -s FuwaPlayer:D ExoPlayerImpl:* MatroskaExtractor:* | tail -40
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
shot 12-player-landscape 6; alive
adb shell settings put system user_rotation 0
sleep 3; alive

# Downloads tab in Koleksi.
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input keyevent KEYCODE_BACK; sleep 2
tap "Koleksi" && sleep 2 && tap "Unduhan*" && shot 13-downloads 15; alive

adb logcat -d -b crash | tail -40
echo "Smoke test passed"
