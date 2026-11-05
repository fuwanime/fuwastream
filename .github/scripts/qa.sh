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
alive() { adb shell pidof "$PKG" >/dev/null || { echo "::error::App process died"; adb logcat -d -b crash | tail -80; adb logcat -d | tail -3000 > "$OUT/death-logcat.txt"; exit 1; }; }
back_gesture() { adb shell input swipe 2 1200 520 1200 250; }

record() { adb shell screenrecord --bit-rate 6000000 --size 540x1200 --time-limit 60 "/sdcard/$1.mp4" & REC=$!; sleep 1; }
stop_record() { sleep 1; kill -INT $REC 2>/dev/null; wait $REC 2>/dev/null; sleep 2; adb pull "/sdcard/$1.mp4" "$OUT/$1.mp4" >/dev/null && echo "recorded $1"; }

# Every scenario starts from a fresh launch on Home, so one failed step never derails the next.
dismiss_dialogs() { adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1; tap "Wait" >/dev/null 2>&1 || true; }
fresh() { adb shell am force-stop "$PKG"; sleep 1; adb shell am start -W -n "$PKG/com.fuwanime.MainActivity" >/dev/null; sleep 12; tap "Saya berusia 13 tahun atau lebih — Lanjutkan" >/dev/null 2>&1; sleep 2; dismiss_dialogs; alive; }
open_search_result() {  # $1 = query, $2 = title prefix to tap
  tap "Jelajah"; sleep 3; tap "Judul, genre, atau studio*" || adb shell input tap 540 330; sleep 1
  adb shell input text "$1"; sleep 5; adb shell input keyevent KEYCODE_BACK; sleep 1
  tap "$2"
}

# 0. Upgrade from the latest release: the Room v2 -> v3 migration must keep watch history.
upgrade_from_release() {
  rm -rf /tmp/old && mkdir -p /tmp/old
  gh release download --repo "${GITHUB_REPOSITORY:-SakamotoMrX/fuwanime}" --pattern '*.apk' -D /tmp/old >/dev/null 2>&1 \
    || { echo "upgrade: skipped (no release apk)" | tee -a "$OUT/qa-checks.txt"; return; }
  adb uninstall "$PKG" >/dev/null 2>&1
  adb install "$(ls /tmp/old/*.apk | head -1)" >/dev/null || { echo "upgrade: skipped (old apk install failed)" | tee -a "$OUT/qa-checks.txt"; adb install -r "$APK"; return; }
  adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
  fresh
  tap "Tonton"; sleep 5; scroll_to "Episode 1" && tap "Episode 1"; sleep 20
  adb shell input keyevent KEYCODE_BACK; sleep 2; adb shell input keyevent KEYCODE_BACK; sleep 2
  # A different signing key can't upgrade in place; that says nothing about the migration, so skip.
  adb install -r "$APK" >/dev/null || { echo "upgrade: skipped (signature mismatch)" | tee -a "$OUT/qa-checks.txt"; adb uninstall "$PKG" >/dev/null; adb install "$APK"; adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true; return; }
  fresh; tap "Koleksi"; shot qa0-upgraded 3
  [ -n "$(find_xy "EP 1")" ] && echo "upgrade: history kept" | tee -a "$OUT/qa-checks.txt" || echo "upgrade: history missing" | tee -a "$OUT/qa-checks.txt"
  alive
}
upgrade_from_release

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
record qa4-player
tap "Tonton"; shot qa4a0-hero-poster-flying 0.15; sleep 5; shot qa4a-detail 0
scroll_to "Episode 1" && tap "Episode 1"; shot qa4b-player 6
adb shell input keyevent KEYCODE_BACK; shot qa4c-detail-after-player 2.5
stop_record qa4-player
# Second clip: screenrecord stops at 60 s, and the hero landing on Home is the part users report.
record qa4x-button-player-home
adb shell input swipe 540 900 540 1700 300; adb shell input swipe 540 900 540 1700 300; sleep 1
tap "Tonton EP*" || tap "Lanjut EP*"; shot qa4d-player-from-button 6
adb shell input keyevent KEYCODE_BACK; shot qa4e-detail-header 2.5
adb shell input keyevent KEYCODE_BACK; shot qa4f0-hero-landing 0.15; shot qa4f-home 2.5
stop_record qa4x-button-player-home; alive

# 5. Tabs.
fresh
record qa5-tabs
tap "Jelajah"; shot qa5a-explore 3
tap "Jadwal"; shot qa5b-schedule 3
tap "Koleksi"; shot qa5c-library 3
tap "Beranda"; shot qa5d-home 3
stop_record qa5-tabs; alive

# 5b. README shots: two favourites from the hero, Koleksi → Favorit, Pengaturan.
fresh
tap "Tambah ke Koleksi"; sleep 1
adb shell input swipe 900 700 180 700 250; sleep 2
tap "Tambah ke Koleksi"; sleep 1
tap "Koleksi"; sleep 2; tap "Favorit*"; shot qa5e-favorites 3
tap "Beranda"; sleep 2; tap "Pengaturan"; shot qa5f-settings 3
adb shell input keyevent KEYCODE_BACK; sleep 1; alive

# Launcher surfaces after an episode was played (scenario 4) and favourites exist (5b).
echo "shortcut continue: $(adb shell dumpsys shortcut | grep -c 'id=continue')" | tee -a "$OUT/qa-checks.txt"
echo "widget provider: $(adb shell dumpsys appwidget | grep -c ContinueWidgetReceiver)" | tee -a "$OUT/qa-checks.txt"

# 8. Koleksi: Favorit sort + badge, long-press multi-select, back clears it, Statistik + recap card.
fresh
tap "Koleksi"; sleep 2; tap "Favorit*"; sleep 2
tap "Terakhir ditonton"; shot qa8a-fav-sort 2
chip_y=$(find_xy "Terakhir ditonton" | cut -d' ' -f2)
adb shell input swipe 190 $(( ${chip_y:-600} + 300 )) 190 $(( ${chip_y:-600} + 300 )) 900; shot qa8b-select 1.5
adb shell input keyevent KEYCODE_BACK; shot qa8c-select-cleared 1.5
[ -n "$(find_xy "Koleksi")" ] && echo "select: back stays on Koleksi" | tee -a "$OUT/qa-checks.txt" || echo "select: back left Koleksi" | tee -a "$OUT/qa-checks.txt"
tap "Statistik"; shot qa8d-stats 3
scroll_to "Bagikan rekap"; shot qa8e-recap 2
adb shell input keyevent KEYCODE_BACK; sleep 1; alive

# 9. Media session: media keys, notification, background audio setting, PiP actions, remembered aspect mode.
session_state() { adb shell dumpsys media_session | grep -A40 "package=$PKG" | grep -o "PlaybackState {state=[^,]*" | head -1 | sed 's/.*state=//'; }
check() { echo "$1: $2" | tee -a "$OUT/qa-checks.txt"; }
open_player_settings() { tap "Pengaturan video" >/dev/null 2>&1 || { adb shell input tap 540 350; sleep 0.7; tap "Pengaturan video"; }; }
play_ep1() { tap "Tonton"; sleep 5; scroll_to "Episode 1" && tap "Episode 1"; sleep 20; }
adb logcat -c
fresh
play_ep1
check "session playing" "$(session_state)"
adb shell input keyevent KEYCODE_MEDIA_PAUSE; sleep 2
check "session after media pause" "$(session_state)"
adb shell input keyevent KEYCODE_MEDIA_PLAY; sleep 3
check "media notification" "$(adb shell dumpsys notification --noredact | grep -A60 "pkg=$PKG" | grep -c "mediaSession\|MediaStyle")"
adb shell input keyevent KEYCODE_SLEEP; sleep 6
check "screen off, setting off" "$(session_state)"
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard; sleep 2
# PiP (Auto-PiP is on by default): the window must carry the custom actions.
adb shell input keyevent KEYCODE_MEDIA_PLAY; sleep 2
adb shell input keyevent KEYCODE_HOME; sleep 4
shot qa9a-pip 0
# Tapping the PiP window shows its controls: the three custom actions must be there.
adb shell input tap 740 1660; shot qa9a2-pip-actions 1
adb shell am start -n "$PKG/com.fuwanime.MainActivity" >/dev/null; sleep 3
# Aspect mode is remembered across player sessions.
open_player_settings; sleep 1; scroll_to "Isi layar" && tap "Isi layar"; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 2
scroll_to "Episode 1" && tap "Episode 1"; sleep 15
open_player_settings; sleep 1; scroll_to "Isi layar"; shot qa9b-resize-remembered 1
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 2; alive
# Background audio on: screen off keeps playing; only one session for the app. Relaunch lands on Home (settings icon).
fresh
tap "Pengaturan"; sleep 2; scroll_to "Putar audio di latar" && tap "Putar audio di latar"; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1
play_ep1
adb shell input keyevent KEYCODE_SLEEP; sleep 6
check "screen off, setting on" "$(session_state)"
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard; sleep 2
check "sessions for app" "$(adb shell dumpsys media_session | grep -c "package=$PKG")"
shot qa9c-player-after-wake 1
adb shell input keyevent KEYCODE_BACK; sleep 12
adb logcat -d | grep -iE "fuwanime|media3|MediaSession|ForegroundService|PlaybackService" > "$OUT/qa9-logcat.txt" || true
alive

# 10. Sub-project C: AniList meta on Detail, AniSkip button, multi-genre + random, Musim ini.
scroll_tab() { for _ in 1 2 3; do tap "$1" && return 0; adb shell input swipe 900 330 200 330 300; sleep 1; done; return 1; }
fresh
tap "Tonton"; sleep 10; scroll_to "Info dari AniList*"; shot qa10a-detail-meta 1
check "anilist footer" "$(find_xy "Info dari AniList*" | wc -l)"
scroll_to "Episode 1" && tap "Episode 1"; sleep 25
# Episode 1 may resume past its opening: show the controls and tap the seekbar at ~1:40 (inside AniSkip's OP, 64–151 s of 23:40).
adb shell input tap 540 350; sleep 1; adb shell input tap 106 705; sleep 6
check "aniskip button" "$( [ -n "$(find_xy "Lewati OP")" ] && echo 1 || echo 0 )"; shot qa10b-player-skip 0
adb shell input keyevent KEYCODE_BACK; sleep 2; adb shell input keyevent KEYCODE_BACK; sleep 2; alive
fresh
tap "Jelajah"; sleep 4; tap "Action"; sleep 2; tap "Adventure"; sleep 5; shot qa10c-two-genres 1
tap "Anime acak"; sleep 5; shot qa10d-random-detail 1; adb shell input keyevent KEYCODE_BACK; sleep 2
tap "Jadwal"; sleep 3; scroll_tab "Musim ini"; sleep 10; shot qa10e-musim-ini 1
check "musim ini" "$( [ -n "$(find_xy "Baru musim ini*")$(find_xy "Lanjutan*")" ] && echo 1 || echo 0 )"
alive

# 11. Sub-project D: frosted bars, poster-tinted Detail (dark), TalkBack nodes, font 200%, rail on a wide screen.
dump_ui() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/$1.xml" >/dev/null 2>&1; }
fresh
adb shell input swipe 540 1700 540 1100 400; shot qa11a-frosted-bottom-bar 1.5
dump_ui qa11-home-ui
check "merged poster descriptions on Home" "$(grep -o 'content-desc="[^"]*, [^"]*"' "$OUT/qa11-home-ui.xml" | wc -l)"
tap "Koleksi"; sleep 3; dump_ui qa11-library-ui
check "Pilih action nodes in Koleksi" "$(grep -c 'long-clickable="true"' "$OUT/qa11-library-ui.xml")"
adb shell cmd uimode night yes; sleep 2
fresh
tap "Tonton"; shot qa11b-detail-tint-dark 6
adb shell input swipe 540 1600 540 600 400; shot qa11c-detail-frosted-topbar 1.5
adb shell input keyevent KEYCODE_BACK; sleep 2
tap "Statistik" >/dev/null 2>&1 || { tap "Koleksi"; sleep 2; tap "Statistik"; }; sleep 3
adb shell input swipe 540 1600 540 800 400; shot qa11d-stats-frosted-header-dark 1.5
adb shell cmd uimode night no; sleep 2; alive
# Font 200%.
adb shell settings put system font_scale 2.0
fresh; shot qa11e-font200-home 1
tap "Tonton"; sleep 3; [ -n "$(find_xy "Tonton")" ] && tap "Tonton"; shot qa11f-font200-detail 5
scroll_to "Episode 1"; shot qa11f2-font200-detail-episodes 1; adb shell input keyevent KEYCODE_BACK; sleep 2
tap "Jelajah"; shot qa11g-font200-explore 3
tap "Koleksi"; shot qa11h-font200-library 3
tap "Jadwal"; shot qa11i-font200-schedule 3
adb shell settings put system font_scale 1.0; alive
# Wide screen (800dp): navigation rail and readable width.
adb shell wm size 1600x2560; adb shell wm density 320; sleep 3
fresh; shot qa11j-wide-home 1
check "rail on wide screen" "$( [ -n "$(find_xy "Jelajah")" ] && echo 1 || echo 0 )"
tap "Jelajah"; shot qa11k-wide-explore 3
tap "Beranda"; sleep 2; tap "Tonton"; shot qa11l-wide-detail 6; adb shell input keyevent KEYCODE_BACK; sleep 2
tap "Pengaturan"; shot qa11m-wide-settings 3; adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell wm size reset; adb shell wm density reset; sleep 3
# Phone in landscape gets the rail too.
adb shell wm user-rotation lock 1; sleep 3
fresh; shot qa11n-landscape-home 1
adb shell wm user-rotation free; sleep 3; alive

# 6. Offline: Beranda from the saved data with the offline pill, then back online.
adb shell svc wifi disable; adb shell svc data disable
fresh; shot qa6a-offline-home 2
adb shell svc wifi enable; adb shell svc data enable
shot qa6b-back-online 3; alive

# 7. Cold start, same APK both ways (median of 5 TotalTime, written to startup.txt):
#    none    = compiled state reset, JIT only (a fresh sideload before profileinstaller runs)
#    profile = the APK's baseline profile installed by profileinstaller, then compiled speed-profile
measure() {
  local times=() t
  for _ in 1 2 3 4 5; do
    adb shell am force-stop "$PKG"; sleep 2
    t=$(adb shell am start -W -n "$PKG/com.fuwanime.MainActivity" | tr -d '\r' | awk -F': ' '/TotalTime/{print $2}')
    times+=("${t:-0}"); sleep 3
  done
  echo "startup $1 TotalTime ms: ${times[*]}" >&2
  printf '%s\n' "${times[@]}" | sort -n | sed -n 3p
}
startup() {
  adb shell am force-stop "$PKG"
  adb shell cmd package compile --reset "$PKG" >/dev/null || true
  adb shell cmd package art clear-app-profiles "$PKG" >/dev/null 2>&1 || true
  local none; none=$(measure none)
  adb shell cmd package compile --reset "$PKG" >/dev/null || true
  # Profiles gathered by the runs above must not count as the baseline profile.
  adb shell cmd package art clear-app-profiles "$PKG" >/dev/null 2>&1 || true
  adb shell am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE "$PKG/androidx.profileinstaller.ProfileInstallReceiver" >/dev/null
  sleep 2
  adb shell cmd package compile -f -m speed-profile "$PKG" >/dev/null || true
  local profile; profile=$(measure profile)
  echo "startup median ms: none=$none profile=$profile"
  printf 'median_none_ms=%s\nmedian_profile_ms=%s\n' "$none" "$profile" > "$OUT/startup.txt"
}
startup

adb logcat -d -b crash | tail -40
echo "QA run passed"
