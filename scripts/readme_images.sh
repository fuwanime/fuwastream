#!/usr/bin/env bash
# Builds the README images from a QA screenshot artifact (1080x2400 emulator captures):
#   docs/screenshots/<name>.webp  phone-framed screenshots for the gallery
#   docs/brand/banner.webp        hero banner (wordmark + three phones)
# Usage: scripts/readme_images.sh <dir with qa*.png>    (needs ImageMagick 7: `magick`)
set -euo pipefail

SRC="${1:?QA screenshot directory}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SHOTS="$ROOT/docs/screenshots"
FONT_DIR="$ROOT/app/src/main/res/font"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# App palette (ui/theme/Theme.kt, dark scheme).
BG='#0B0A0F'; SURFACE='#16141C'; OUTLINE='#2A2633'; PRIMARY='#D4BBFF'; PINK='#FFB0C9'; TEXT='#E8E0EE'; MUTED='#A39DAE'

# frame <in> <out> <width>: screenshot with rounded corners inside a thin dark bezel, plus a soft shadow.
frame() {
  local w=$3 h=$(( $3 * 20 / 9 )) r=$(( $3 * 7 / 100 )) pad=$(( $3 / 40 ))
  magick "$1" -resize "${w}x${h}!" \
    \( -size "${w}x${h}" xc:none -fill white -draw "roundrectangle 0,0 $((w-1)),$((h-1)) $r,$r" \) \
    -compose DstIn -composite "$TMP/screen.png"
  local fw=$(( w + 2 * pad )) fh=$(( h + 2 * pad )) fr=$(( r + pad ))
  magick -size "${fw}x${fh}" xc:none -fill "$SURFACE" -stroke "$OUTLINE" -strokewidth 3 \
    -draw "roundrectangle 1,1 $((fw-2)),$((fh-2)) $fr,$fr" \
    "$TMP/screen.png" -geometry "+${pad}+${pad}" -compose over -composite \
    \( +clone -background black -shadow 55x$(( w / 22 ))+0+$(( w / 30 )) \) +swap \
    -background none -layers merge +repage "$2"
}

# name:source pairs for the gallery.
for pair in home:qa5d-home explore:qa5a-explore schedule:qa5b-schedule detail:qa4a-detail \
            player:qa4b-player collection:qa5e-favorites settings:qa5f-settings offline:qa6a-offline-home; do
  name=${pair%%:*}; src="$SRC/${pair#*:}.png"
  [ -f "$src" ] || { echo "skip $name (no $src)"; continue; }
  frame "$src" "$TMP/$name.png" 420
  magick "$TMP/$name.png" -quality 90 -define webp:alpha-quality=100 "$SHOTS/$name.webp"
  echo "framed $name"
done

# Banner: 1760x880 (shown at 880 wide), gradient + glows, wordmark on the left, three phones on the right.
W=1760; H=880
magick -size "${W}x${H}" gradient:"$BG"-'#1A1721' \
  \( -size "${W}x${H}" xc:none -fill "${PRIMARY}33" -draw "circle 1240,380 1240,40" -blur 0x140 \) -compose over -composite \
  \( -size "${W}x${H}" xc:none -fill "${PINK}22" -draw "circle 1560,760 1560,520" -blur 0x120 \) -compose over -composite \
  "$TMP/bg.png"

frame "$SRC/qa4a-detail.png" "$TMP/p-left.png" 300
frame "$SRC/qa5a-explore.png" "$TMP/p-right.png" 300
frame "$SRC/qa5d-home.png" "$TMP/p-mid.png" 340
magick "$TMP/p-left.png" -background none -rotate -8 "$TMP/p-left.png"
magick "$TMP/p-right.png" -background none -rotate 8 "$TMP/p-right.png"

# App icon (it carries its own dark tile): rounded like the launcher, with a hairline outline.
magick "$ROOT/docs/brand/icon-512.png" -resize 136x136 \
  \( -size 136x136 xc:none -fill white -draw "roundrectangle 0,0 135,135 34,34" \) -compose DstIn -composite \
  -fill none -stroke "$OUTLINE" -strokewidth 3 -draw "roundrectangle 1,1 134,134 34,34" "$TMP/icon.png"

# "fuwa" in primary, "stream" right after it: measure the first word instead of guessing its width.
BOLD="$FONT_DIR/plus_jakarta_sans_extrabold.ttf"
FUWA_W=$(magick -font "$BOLD" -pointsize 104 label:fuwa -format %w info:)

magick "$TMP/bg.png" \
  "$TMP/p-left.png" -geometry +880+170 -compose over -composite \
  "$TMP/p-right.png" -geometry +1330+170 -compose over -composite \
  "$TMP/p-mid.png" -geometry +1090+60 -compose over -composite \
  "$TMP/icon.png" -geometry +120+196 -compose over -composite \
  -font "$BOLD" -pointsize 104 \
  -fill "$PRIMARY" -annotate +116+460 'fuwa' \
  -fill "$TEXT" -annotate +$(( 116 + FUWA_W ))+460 'stream' \
  -font "$FONT_DIR/plus_jakarta_sans_semibold.ttf" -pointsize 40 -fill "$MUTED" \
  -annotate +122+540 'Teman nonton anime di Android.' \
  -font "$FONT_DIR/plus_jakarta_sans_bold.ttf" -pointsize 28 -fill "$PRIMARY" \
  -annotate +122+640 'OFFLINE-READY   ·   MATERIAL 3   ·   TANPA IKLAN' \
  -strip -quality 90 "$ROOT/docs/brand/banner.webp"
echo "banner done"
