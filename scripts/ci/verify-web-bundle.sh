#!/usr/bin/env bash
#
# verify-web-bundle.sh — assert a built apps/js distribution is actually the web app.
#
# The gate exists because webpack happily bundles a web app with no emulator modules: the WASM
# copy used to be a manual, unwired step, so `jsBrowserDistribution` could "succeed" while shipping
# an app that cannot play anything. The build now wires the copy + an execution-time emsdk check;
# this script independently verifies the *produced artifact* (used by CI and the Dockerfile).
#
# Usage:
#   scripts/ci/verify-web-bundle.sh [bundle-dir]
#
# Default bundle dir: apps/js/build/dist/js/productionExecutable
#
# Environment:
#   MIN_WASM_BYTES   minimum size of each .wasm (default 1024; catches empty/placeholder blobs)
#
# Exit codes: 0 = complete, 1 = missing/incomplete bundle, 3 = no bundle dir (mirrors verify.sh's
# SKIP convention so the local `web` task can report unavailable tooling as a skip, not a failure).

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MANIFEST="$ROOT/apps/js/wasm-emulators.txt"
BUNDLE_DIR="${1:-$ROOT/apps/js/build/dist/js/productionExecutable}"
MIN_WASM_BYTES="${MIN_WASM_BYTES:-1024}"

if [ ! -d "$BUNDLE_DIR" ]; then
  echo "web bundle directory not found: $BUNDLE_DIR" >&2
  echo "build it with: ./gradlew :apps:js:jsBrowserDistribution -Psage.js=true" \
    "-Dorg.gradle.isolated-projects=false -Dorg.gradle.configureondemand=false --no-parallel" >&2
  exit 3
fi

missing=()
checked=0

check_file() {
  local rel="$1" min_size="${2:-1}"
  checked=$((checked + 1))
  local path="$BUNDLE_DIR/$rel"
  if [ ! -f "$path" ]; then
    missing+=("$rel (missing)")
    return
  fi
  local size
  size=$(wc -c <"$path" | tr -d ' ')
  if [ "$size" -lt "$min_size" ]; then
    missing+=("$rel (${size}B < ${min_size}B)")
  fi
}

check_file "index.html"
check_file "chipbox.js"

emulator_count=0
while IFS='|' read -r dir output display; do
  dir="$(echo "$dir" | xargs)"
  output="$(echo "$output" | xargs)"
  [ -z "$dir" ] && continue
  case "$dir" in \#*) continue ;; esac
  [ -n "$output" ] || { echo "bad manifest line in $MANIFEST" >&2; exit 1; }
  emulator_count=$((emulator_count + 1))
  check_file "wasm/$output.js"
  check_file "wasm/$output.wasm" "$MIN_WASM_BYTES"
done <"$MANIFEST"

if [ "$emulator_count" -ne 9 ]; then
  echo "FAILED: expected 9 emulators in $MANIFEST, found $emulator_count" >&2
  exit 1
fi

if [ "${#missing[@]}" -ne 0 ]; then
  echo "FAILED: web bundle at $BUNDLE_DIR is incomplete ($checked entries checked):" >&2
  for entry in "${missing[@]}"; do echo "  MISSING $entry" >&2; done
  exit 1
fi

echo "Web bundle OK: $BUNDLE_DIR ($emulator_count loader/WASM pairs + index.html + chipbox.js)"
