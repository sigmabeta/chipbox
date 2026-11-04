#!/usr/bin/env bash
#
# device-cache.sh — run the on-device production-cache probe on every attached device.
#
# Unlike the perfbench axes (audio/ui/startup), the cache probe has no perfbench result document to
# diff against a baseline: it is a self-gating instrumentation test. Its own assertions fail the run
# on a warm cache hit that loads/tears down the native core, a trimmed track that reports the wrong
# length, a corrupt source that kills the process, or native heap growth over the sustained-load
# bound (2 KiB/open, measured over 60 cold skips of a mini2sf and of a SAVE-bearing fixture).
# So the "gate" is the test's exit status — this wrapper stages the corpus, runs the class on all
# devices, and collects each device's cache-device.tsv for the evidence trail.
#
# Runs on every attached device (AGP fans out; see device-common.sh for why no serial is pinned).
#
# Usage:
#   scripts/benchmark/device-cache.sh <corpus-dir>
#
# Exit codes (same convention as the axis scripts):
#   0  passed
#   3  skipped — no device, or a device attached over two transports
#   1  failed — the test run failed
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

EXIT_PASS=0
EXIT_FAIL=1
EXIT_SKIP=3

REMOTE_ROOT="/sdcard/Music/chipbox-test"
ADDITIONAL_OUT="$ROOT/benchmark/build/outputs/connected_android_test_additional_output"
OUT="$ROOT/build/perfbench-device/cache"
TEST_CLASS="net.sigmabeta.chipbox.benchmark.DeviceCacheBenchmark"
RESULT_FILE="cache-device.tsv"
PKG="net.sigmabeta.chipbox.benchmark"

if [ -t 1 ]; then R=$'\e[31m'; G=$'\e[32m'; Y=$'\e[33m'; B=$'\e[1m'; Z=$'\e[0m'; else R= G= Y= B= Z=; fi
die() { echo "${R}error:${Z} $*" >&2; exit "$EXIT_FAIL"; }
usage() { echo "${R}error:${Z} $*" >&2; exit 2; }
skip() { echo "${Y}flag:${Z} $*" >&2; exit "$EXIT_SKIP"; }

CORPUS=""
while [ $# -gt 0 ]; do
  case "$1" in
    -h|--help)
      cat <<EOF
device-cache.sh — run the on-device production PCM-cache probe on every attached device.

The test asserts its own bounds (warm hits perform zero native loads/teardowns, trailing silence is
trimmed, a corrupt source reports an error, and native heap growth over 60 cold skips stays under
2 KiB/open for both the core and the SAVE ownership paths), so its exit status is the gate.

Usage:
  scripts/benchmark/device-cache.sh <corpus-dir>

Artifacts: $OUT/<device>/cache-device.tsv
EOF
      exit 0
      ;;
    --corpus) [ $# -ge 2 ] || usage "--corpus needs a value"; CORPUS="$2"; shift ;;
    --corpus=*) CORPUS="${1#--corpus=}" ;;
    -*) usage "unknown option: $1" ;;
    *)
      [ -z "$CORPUS" ] || usage "unexpected argument: $1 (corpus already set to $CORPUS)"
      CORPUS="$1"
      ;;
  esac
  shift
done

command -v adb >/dev/null || die "adb not found on PATH"
[ -n "$CORPUS" ] || usage "a corpus dir is required: scripts/benchmark/device-cache.sh <corpus-dir>"
[ -d "$CORPUS" ] || die "corpus is not a directory: $CORPUS"
CORPUS="$(cd "$CORPUS" && pwd)"

mapfile -t online < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
[ "${#online[@]}" -gt 0 ] || skip "no device attached — attach a physical device (or fix adb authorization) and re-run"

# A phone attached over two transports (mDNS and IP) would run twice and collide.
declare -A by_hardware=()
for s in "${online[@]}"; do
  hw="$(adb -s "$s" shell getprop ro.serialno 2>/dev/null | tr -d '\r')"
  [ -n "$hw" ] || hw="$s"
  by_hardware["$hw"]+="$s "
done
duplicated=()
for hw in "${!by_hardware[@]}"; do
  read -r -a serials <<<"${by_hardware[$hw]}"
  [ "${#serials[@]}" -gt 1 ] && duplicated+=("$hw: ${by_hardware[$hw]}")
done
if [ "${#duplicated[@]}" -gt 0 ]; then
  skip "a device is attached over more than one transport ($(printf '%s' "${duplicated[*]}")) — adb disconnect the duplicate(s) and re-run"
fi

echo "${B}On-device cache probe${Z} — ${#online[@]} device(s): ${online[*]}"

echo "==> Pushing corpus to ${#online[@]} device(s)"
for s in "${online[@]}"; do
  adb -s "$s" shell rm -rf "$REMOTE_ROOT/corpus" >/dev/null || die "adb corpus clean failed on $s"
  adb -s "$s" shell mkdir -p "$REMOTE_ROOT/corpus" >/dev/null || die "adb mkdir failed on $s"
  adb -s "$s" push "$CORPUS/." "$REMOTE_ROOT/corpus/" >/dev/null || die "adb push of the corpus failed on $s"
  # The probe writes nothing outside its own cache dir, but it needs All Files Access to read the
  # pushed corpus (DeviceBenchmark.grantStorageAccess also sets it at test start).
  adb -s "$s" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow >/dev/null 2>&1 || true
done

echo "==> Running ${TEST_CLASS##*.} on all devices"
rm -rf "$ADDITIONAL_OUT"
./gradlew --console=plain \
  -Pandroid.testInstrumentationRunnerArguments.class="$TEST_CLASS" \
  :benchmark:connectedBenchmarkAndroidTest ||
  die "the on-device cache probe failed (see the assertion in the log)"

rm -rf "$OUT"
mkdir -p "$OUT"
mapfile -t results < <(find "$ADDITIONAL_OUT" -name "$RESULT_FILE" -type f 2>/dev/null | sort)
[ "${#results[@]}" -gt 0 ] || die "the run produced no $RESULT_FILE under $ADDITIONAL_OUT"

for f in "${results[@]}"; do
  # AGP names the per-device output dir after the device (spaces, e.g. "Pixel 6a - 13").
  label="$(basename "$(dirname "$f")" | tr -c 'A-Za-z0-9._-' '_')"
  mkdir -p "$OUT/$label"
  cp "$f" "$OUT/$label/$RESULT_FILE"
  printf '    %-24s %s\n' "$label" "$OUT/$label/$RESULT_FILE"
  grep -E '^sustained-' "$f" | sed 's/^/        /'
done

echo "${G}${B}✔ cache probe passed${Z} on ${#results[@]} device(s) — artifacts in $OUT/"
