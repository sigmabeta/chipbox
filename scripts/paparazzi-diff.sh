#!/usr/bin/env bash
#
# paparazzi-diff.sh — re-render the Paparazzi goldens and report which screenshots
# diverged most from the committed baseline, ranked by AE% (the fraction of pixels
# that differ — the same notion Paparazzi's `maxPercentDifference` thresholds on).
#
# For the top-N divergent screenshots it writes a 3-up montage (golden | new | diff)
# under build/paparazzi-review/ and prints its path so you can eyeball the change.
# Non-destructive: the regenerated goldens are reverted to the committed baseline
# afterward (pass --keep to keep them, e.g. when you've decided to re-record).
#
# Usage:
#   scripts/paparazzi-diff.sh [--keep] [--top N] [gradle-task]
#     gradle-task  defaults to recordPaparazziDebug (all screenshot modules); pass a
#                  scoped task to narrow it, e.g. :features:home:screenshot:recordPaparazziDebug
#
set -u

cd "$(dirname "$0")/.."

KEEP=0
TOP=5
NORECORD=0
TASK="recordPaparazziDebug"
while [ $# -gt 0 ]; do
  case "$1" in
    --keep)      KEEP=1 ;;
    --no-record) NORECORD=1 ;;   # skip gradle; rank whatever renders are already in the tree
    --top)       shift; TOP="${1:?--top needs a number}" ;;
    *)           TASK="$1" ;;
  esac
  shift
done

command -v magick  >/dev/null 2>&1 || { echo "error: needs ImageMagick (magick/compare/montage)"; exit 1; }
command -v compare  >/dev/null 2>&1 || { echo "error: needs ImageMagick 'compare'"; exit 1; }

GLOB='snapshots/images/.*\.png$'

if [ "$NORECORD" -eq 0 ]; then
  echo "▶ recording goldens ($TASK) …"
  if ! ./gradlew "$TASK" --console=plain -q; then
    echo "error: gradle task failed"; exit 1
  fi
else
  echo "▶ --no-record: ranking the renders already in the working tree"
fi

# Goldens that changed vs the committed baseline (HEAD — catches staged or unstaged), and
# brand-new ones with no baseline.
mapfile -t changed < <(git diff --name-only HEAD       | grep -E "$GLOB" || true)
mapfile -t added   < <(git ls-files --others --exclude-standard | grep -E "$GLOB" || true)

if [ "${#changed[@]}" -eq 0 ] && [ "${#added[@]}" -eq 0 ]; then
  echo "🎉 hooray — every screenshot matches its golden, nothing diverged."
  exit 0
fi

tmp="$(mktemp -d)"
review="build/paparazzi-review"
trap 'rm -rf "$tmp"' EXIT
# Start clean so stale montages from a previous run (e.g. a larger --top, or
# screenshots that no longer diverge) can't masquerade as current results.
rm -rf "$review"
mkdir -p "$review"
scores="$tmp/scores"; : > "$scores"

# AE% between two same-named-safe PNGs. Echoes: "<pct> <diffpx> <totalpx>"
ae_pct() {
  local old="$1" new="$2"
  local nw nh ow oh
  read -r nw nh < <(magick identify -format '%w %h' "$new" 2>/dev/null || echo "0 0")
  read -r ow oh < <(magick identify -format '%w %h' "$old" 2>/dev/null || echo "0 0")
  local total=$(( nw * nh ))
  if [ "$total" -eq 0 ] || [ "$nw" != "$ow" ] || [ "$nh" != "$oh" ]; then
    echo "100.0000 ${total:-0} ${total:-0}"; return            # size mismatch ⇒ treat as fully divergent
  fi
  local ae
  ae="$( { compare -metric AE "$old" "$new" null: 2>&1 || true; } | grep -oE '[0-9]+' | tail -1)"
  ae="${ae:-0}"
  awk -v a="$ae" -v t="$total" 'BEGIN{ printf "%.4f %d %d", (t ? 100*a/t : 0), a, t }'
}

# ImageMagick treats [..] / (..) in filenames as read modifiers, so always copy to a
# bracket-free temp name before handing a golden path to magick/compare.
for f in "${changed[@]}"; do
  git show "HEAD:$f" 2>/dev/null | git lfs smudge 2>/dev/null > "$tmp/old.png" || true
  [ -s "$tmp/old.png" ] || continue
  cp "$f" "$tmp/new.png"
  read -r pct cnt tot < <(ae_pct "$tmp/old.png" "$tmp/new.png")
  printf '%s\t%s\t%s/%s\n' "$pct" "$f" "$cnt" "$tot" >> "$scores"
done

echo
echo "Top $TOP most-divergent screenshots (AE % = pixels differing / total):"
i=0
while IFS=$'\t' read -r pct path detail; do
  i=$((i+1))
  # build a golden|new|diff montage for review
  git show "HEAD:$path" 2>/dev/null | git lfs smudge 2>/dev/null > "$tmp/old.png" || true
  cp "$path" "$tmp/new.png"
  compare "$tmp/old.png" "$tmp/new.png" "$tmp/diff.png" 2>/dev/null || true
  out="$review/$(printf '%02d_%s' "$i" "$(basename "$path")")"
  magick montage "$tmp/old.png" "$tmp/new.png" "$tmp/diff.png" \
    -tile 3x1 -geometry +6+6 -background '#202020' "$out" 2>/dev/null || true
  printf '  %6.2f%%  (%s)\n           golden: %s\n           review: %s\n' "$pct" "$detail" "$path" "$out"
done < <(sort -t$'\t' -k1 -gr "$scores" | head -"$TOP")

if [ "${#added[@]}" -gt 0 ]; then
  echo
  echo "New goldens with no baseline (not ranked):"
  printf '           %s\n' "${added[@]}"
fi

if [ "$KEEP" -eq 0 ]; then
  [ "${#changed[@]}" -gt 0 ] && git checkout HEAD -- "${changed[@]}" 2>/dev/null || true
  [ "${#added[@]}"   -gt 0 ] && rm -f "${added[@]}"
  echo
  echo "(goldens restored to the committed baseline — pass --keep to keep the new renders)"
fi
