#!/usr/bin/env bash
#
# benchmark-device-common.sh — the shared engine behind the per-axis device benchmark scripts
# (benchmark-device-audio.sh, -ui.sh, -startup.sh). Sourced, never run directly.
#
# A wrapper sets two variables and calls `benchmark_device_main "$@"`:
#   AXIS        the perfbench axis id ("audio", "ui", "startup")
#   TEST_CLASS  the fully-qualified instrumentation test class that measures it
#
# It runs the axis on EVERY attached device: it scans the corpus into a snapshot, pushes the corpus +
# snapshot to each, runs only that axis's test class (AGP fanning out to all connected devices, in the
# reused Gradle daemon), then diffs each device's result against that device's locally-recorded
# baseline with `perfbench diff --json` (ratio = baseline ÷ current; below the threshold — default
# 0.95 — is a regression).
#
# One axis per process is the point: the axes are never interesting together, a failing/hanging one
# must not take the others with it, and it keeps the audio axis's result alive. The benchmark
# library's `Outputs` singleton wipes the whole additional-test-output directory the first time a
# macrobenchmark initialises it, so an axis written by a non-macrobenchmark test earlier in the same
# run (audio) would be deleted before the run ends. Running the audio axis alone means no
# macrobenchmark ever initialises `Outputs` in that process, so its result survives.
#
# The device set is deliberately NOT pinned to one serial: AGP's `--serial` option throws
# UnsupportedOperationException on AGP 9.2.1 (getFilteredDevices removes matched serials from the
# immutable list the option yields), and the ANDROID_SERIAL env it falls back to is read at
# configuration time, so a reused Gradle daemon can never see a newly exported value. Fan-out needs
# neither, so it works with the warm daemon. (A device attached over two transports — e.g. mDNS and
# IP — would be run twice and collide, so that is detected and the run is skipped with a flag.)
#
# Exit codes, shared by every axis script:
#   0  passed — or the baseline was (re)recorded
#   3  skipped — no device, or a device attached over two transports; a FLAG, not a failure
#   1  failed — the test run failed, or an axis regressed past the threshold
#
# The baseline is *local* and never committed; the first run (or `--record`) writes it. It is keyed
# by each device's hardware identity (model + ABI), so a new device records a fresh baseline rather
# than gating one device's numbers against another's.
#
# Usage (as printed by `-h`, per wrapper):
#   scripts/benchmark-device-<axis>.sh <corpus-dir> [--record] [--threshold <ratio>]
#
# Environment:
#   CHIPBOX_PERF_BASELINE   baseline root (default build/perfbench-baseline/device)
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

EXIT_PASS=0
EXIT_FAIL=1
EXIT_SKIP=3

REMOTE_ROOT="/sdcard/Music/chipbox-test"
# Absolute: the `:apps:perfbench:run` task runs with the module dir (apps/perfbench) as its working
# directory, so any path handed to it — snapshot --out, diff --a/--b — must not be repo-relative.
ADDITIONAL_OUT="$ROOT/benchmark/build/outputs/connected_android_test_additional_output"

: "${AXIS:?benchmark-device-common.sh must be sourced with AXIS set}"
: "${TEST_CLASS:?benchmark-device-common.sh must be sourced with TEST_CLASS set}"

CORPUS=""
THRESHOLD="0.95"
RECORD=0
OUT="$ROOT/build/perfbench-device/$AXIS"
SNAPSHOT="$OUT/library-snapshot.json"
CURRENT="$OUT/current"
RESULT_FILE="results-$AXIS.json"

if [ -t 1 ]; then R=$'\e[31m'; G=$'\e[32m'; Y=$'\e[33m'; B=$'\e[1m'; Z=$'\e[0m'; else R= G= Y= B= Z=; fi

die() { echo "${R}error:${Z} $*" >&2; exit "$EXIT_FAIL"; }
usage() { echo "${R}error:${Z} $*" >&2; exit 2; }
flag() { echo "${Y}flag:${Z} $*" >&2; }
skip() { flag "$1"; exit "$EXIT_SKIP"; }

# The baseline dir for one device, from its result's fingerprint (model + ABI), under the override root.
baseline_dir_for() {
  # -j (no trailing newline) so tr's complement doesn't turn the newline into a stray trailing '_'.
  local label
  label="$(jq -jr '"\(.machine.model)-\(.machine.abi)"' "$1" 2>/dev/null | tr -c 'A-Za-z0-9._-' '_')"
  [ -n "$label" ] || label="unknown"
  echo "$(realpath -m "${CHIPBOX_PERF_BASELINE:-$ROOT/build/perfbench-baseline/device}/$label")"
}

# ---- the one entry point a wrapper calls -----------------------------------
benchmark_device_main() {
  # ---- arg parsing ---------------------------------------------------------
  while [ $# -gt 0 ]; do
    case "$1" in
      -h|--help)
        cat <<EOF
benchmark-device-$AXIS.sh — run the on-device $AXIS benchmark on every attached device and gate on a regression.

Runs :benchmark:connectedBenchmarkAndroidTest filtered to $TEST_CLASS on all connected
devices, compares each device's measured $AXIS axis against its local baseline, and fails on a
regression or a test failure. A missing device is a flag (exit 3), not a failure. The corpus dir is
required — the audio axis renders every file in it, and every axis pins its result to it, so use a
small corpus.

Usage:
  scripts/benchmark-device-$AXIS.sh <corpus-dir> [options]

Options:
  --record             (re)record each device's baseline from this run instead of gating against it
  --threshold <ratio>  regress when a device's geometric-mean ratio drops below <ratio>  (default 0.95)
  --corpus <dir>       the corpus dir (same as the positional argument)

Environment:
  CHIPBOX_PERF_BASELINE   baseline root (default build/perfbench-baseline/device)

Artifacts: build/perfbench-device/$AXIS/ (library-snapshot.json, current/<device>/$RESULT_FILE).
EOF
        return 0
        ;;
      --record) RECORD=1 ;;
      --threshold) [ $# -ge 2 ] || usage "--threshold needs a value"; THRESHOLD="$2"; shift ;;
      --threshold=*) THRESHOLD="${1#--threshold=}" ;;
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

  awk -v t="$THRESHOLD" 'BEGIN { exit !(t + 0 > 0 && t + 0 <= 1) }' ||
    usage "--threshold must be a ratio in (0, 1] (was '$THRESHOLD')"

  command -v adb >/dev/null || die "adb not found on PATH"
  command -v jq >/dev/null || die "jq not found on PATH (needed to read the diff report)"

  # ---- corpus --------------------------------------------------------------
  # Every axis is corpus-pinned by the run's corpus block, and the audio axis renders the corpus
  # files themselves, so the corpus is required and validated before touching any device.
  [ -n "$CORPUS" ] || usage "a corpus dir is required: scripts/benchmark-device-$AXIS.sh <corpus-dir>"
  [ -d "$CORPUS" ] || die "corpus is not a directory: $CORPUS"
  CORPUS="$(cd "$CORPUS" && pwd)"

  # ---- the device set ------------------------------------------------------
  # Run on everything attached. But one phone can be attached over two transports at once (mDNS and
  # IP); AGP would then run two sessions on it and collide. Group the online serials by hardware
  # serial (ro.serialno) and skip if any device appears twice.
  mapfile -t online < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
  [ "${#online[@]}" -gt 0 ] || skip "no device attached — attach a physical device (or fix adb authorization) and re-run"

  declare -A by_hardware=()
  for s in "${online[@]}"; do
    hw="$(timeout 20 adb -s "$s" shell getprop ro.serialno 2>/dev/null | tr -d '\r')"
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

  echo "${B}On-device $AXIS benchmark${Z} — ${#online[@]} device(s): ${online[*]}"

  # ---- scan + push ---------------------------------------------------------
  # The snapshot seeds each app's library (the UI/startup axes scroll/launch it) and its hash is part
  # of every axis's corpus block (corpusPin requires it), so it is pushed for every axis.
  mkdir -p "$OUT"
  echo "==> Scanning corpus → $SNAPSHOT"
  ./gradlew --console=plain :apps:perfbench:run --args="snapshot --dir $CORPUS --out $SNAPSHOT" ||
    die "snapshot generation failed"

  echo "==> Pushing corpus + snapshot to ${#online[@]} device(s)"
  for s in "${online[@]}"; do
    # Clear the corpus first: adb push merges into whatever is already there, so stale files from an
    # earlier (larger) corpus would otherwise linger and the audio axis would render them too.
    adb -s "$s" shell rm -rf "$REMOTE_ROOT/corpus" >/dev/null || die "adb corpus clean failed on $s"
    adb -s "$s" shell mkdir -p "$REMOTE_ROOT/corpus" >/dev/null || die "adb mkdir failed on $s"
    adb -s "$s" push "$SNAPSHOT" "$REMOTE_ROOT/" >/dev/null || die "adb push of the snapshot failed on $s"
    adb -s "$s" push "$CORPUS/." "$REMOTE_ROOT/corpus/" >/dev/null || die "adb push of the corpus failed on $s"
  done

  # ---- run this axis on every device ---------------------------------------
  # No serial is passed: AGP fans `connectedBenchmarkAndroidTest` out to every attached device, which
  # keeps the reused Gradle daemon in play (see the header on why pinning a serial doesn't).
  echo "==> Running the $AXIS benchmark (${TEST_CLASS##*.}) on all devices"
  rm -rf "$ADDITIONAL_OUT"
  ./gradlew --console=plain \
    -Pandroid.testInstrumentationRunnerArguments.class="$TEST_CLASS" \
    :benchmark:connectedBenchmarkAndroidTest ||
    die "the on-device $AXIS benchmark failed"

  # ---- collect -------------------------------------------------------------
  rm -rf "$CURRENT"
  mkdir -p "$CURRENT"
  mapfile -t results < <(find "$ADDITIONAL_OUT" -name "$RESULT_FILE" -type f 2>/dev/null | sort)
  [ "${#results[@]}" -gt 0 ] ||
    die "the run produced no $RESULT_FILE under $ADDITIONAL_OUT"

  # ---- record or gate, per device ------------------------------------------
  regressed=0
  flagged=0
  compared=0
  recorded=0
  for f in "${results[@]}"; do
    baseline="$(baseline_dir_for "$f")"
    label="$(basename "$baseline")"
    mkdir -p "$CURRENT/$label"
    cp "$f" "$CURRENT/$label/$RESULT_FILE"

    if [ "$RECORD" -eq 1 ] || [ ! -f "$baseline/$RESULT_FILE" ]; then
      mkdir -p "$baseline"
      cp "$f" "$baseline/$RESULT_FILE"
      printf '    %-24s %srecorded%s → %s\n' "$label" "$B" "$Z" "$baseline/$RESULT_FILE"
      recorded=$((recorded + 1))
      continue
    fi

    # Diff against the sanitized copy, never $f: AGP's per-device output dir is named after the
    # device (e.g. "Pixel 6a - 13"), and its spaces would be split by Gradle's --args into bogus
    # positional arguments. $CURRENT/$label/$RESULT_FILE is the space-free copy made above.
    report="$(./gradlew -q --console=plain :apps:perfbench:run --args="diff --a $baseline/$RESULT_FILE --b $CURRENT/$label/$RESULT_FILE --json" 2>/dev/null)"
    if [ $? -ne 0 ]; then
      flag "$label: diff failed for the $AXIS axis"
      flagged=1
      continue
    fi
    if ! printf '%s' "$report" | jq -e '.geometric_mean' >/dev/null 2>&1; then
      # No JSON: the harness refused to compare (corpus/manifest/scenario drift). Not a regression.
      flag "$label: cannot compare the $AXIS axis — $(printf '%s' "$report" | head -1)"
      flagged=1
      continue
    fi
    mean="$(printf '%s' "$report" | jq -r '.geometric_mean')"
    compared=$((compared + 1))
    if awk -v m="$mean" -v t="$THRESHOLD" 'BEGIN { exit !(m < t) }'; then
      printf '    %-24s %sx  %sREGRESSION%s\n' "$label" "$mean" "$R" "$Z"
      regressed=1
    else
      printf '    %-24s %sx  %sok%s\n' "$label" "$mean" "$G" "$Z"
    fi
  done

  # ---- verdict -------------------------------------------------------------
  if [ "$regressed" -eq 1 ]; then
    echo "${R}${B}x the $AXIS axis regressed on at least one device${Z} — re-run once to rule out noise, or --record to accept"
    return "$EXIT_FAIL"
  fi
  if [ "$flagged" -eq 1 ]; then
    echo "${Y}flagged${Z}: no regression found in the $compared device(s) compared — see the flags above for what was skipped"
    return "$EXIT_PASS"
  fi
  if [ "$recorded" -gt 0 ] && [ "$compared" -eq 0 ]; then
    echo "${G}${B}✔ recorded $recorded $AXIS baseline(s)${Z} — re-run without --record to gate — artifacts in $OUT/"
    return "$EXIT_PASS"
  fi
  echo "${G}${B}✔ no $AXIS regression${Z} ($compared device(s) compared) — artifacts in $OUT/"
  return "$EXIT_PASS"
}
