#!/usr/bin/env bash
#
# ab-compare.sh — render a corpus on BOTH the connected device and the host, then diff them.
#
# One command for the cross-ISA A/B check: it renders the same corpus on a connected Android device
# (whatever ABI it is — arm64-v8a or, with the temporary 32-bit build, armeabi-v7a) and on the host
# JVM (x86_64), then runs `abrender diff`. A bit-identical result means the emulator core produces the
# same PCM on both architectures; anything else is an ISA-specific divergence to look at.
#
# The device render runs in BATCHES (default 20 tracks) using the renderer's shard mechanism. After
# each batch its WAVs are pulled to the host and deleted from the device, so on-device storage stays
# bounded no matter how large the corpus is. The staged corpus is removed at the end.
#
# Parallelism: the host render runs in the background (it also reports the track count, which sizes the
# batches) while the device works through its batches in the foreground.
#
# Usage:
#   apps/abrender-core/ab-compare.sh --dir <corpus> [options]
#
# Options:
#   --dir <path>            corpus dir (host path; pushed to the device for you)      (required)
#   --ext <a,b>             only these extensions, e.g. spc or usf,miniusf            (default: all)
#   --seconds <n>           seconds of audio per track                                (default: 30)
#   --game <substr>         only files whose parent folder contains <substr>
#   --subsong <n>           subsong index for multi-song formats                      (default: 0)
#   --every-song            every playable file (default: one representative per folder)
#   --overwrite             re-render tracks already present (default: resume/skip)
#   --max-wall-seconds <n>  per-track wall-clock cap                                  (default: 120)
#   --batch <n>             device tracks rendered per batch before cleanup           (default: 20)
#   --out <dir>             runs root for both runs + the diff report     (default: <repo>/ab-compare)
#   --label-host <name>     host run label                                            (default: host)
#   --label-device <name>   device run label                          (default: the device's primary ABI)
#   --serial <id>           target a specific device (adb -s / ANDROID_SERIAL)
#   --top <n>               most-changed tracks to list in the diff                   (default: 20)
#
# Multi-word values (a corpus path with spaces, --game "Super Mario 64", a spaced label) are handled:
# values are double-quoted into Gradle's --args and single-quoted into the adb-shell command line.
set -euo pipefail

# Keep the machine awake for the whole run. If the laptop sleeps mid-render, wall-clock jumps forward
# and the renderer's watchdog mistakes the in-flight track for a wedged native call, halting it — which
# corrupts the comparison. Re-exec under systemd-inhibit (Linux/systemd) so idle/lid sleep is blocked
# until the script exits. No-op if systemd-inhibit isn't available (e.g. macOS — use `caffeinate -s`).
if [[ -z "${AB_COMPARE_INHIBITED:-}" ]] && command -v systemd-inhibit >/dev/null 2>&1; then
  export AB_COMPARE_INHIBITED=1
  exec systemd-inhibit --what=sleep:idle:handle-lid-switch \
    --who="ab-compare.sh" --why="device/host A/B render in progress" "$0" "$@"
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
GRADLEW="${REPO_ROOT}/gradlew"

PKG="net.sigmabeta.chipbox.abrender.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
REMOTE_BASE="/sdcard/Android/data/${PKG}/files/abrender"
REMOTE_IN="${REMOTE_BASE}/in"
REMOTE_OUT="${REMOTE_BASE}/out"

CORPUS=""
OUT="${REPO_ROOT}/ab-compare"
LABEL_HOST="host"
LABEL_DEVICE=""
SERIAL=""
TOP="20"
BATCH="20"
MAX_RETRIES="40"   # safety cap on resume relaunches after a genuine native wedge (per side / per batch)
# Render knobs applied IDENTICALLY to both sides. COMMON feeds the host's Gradle --args (--key value);
# EARGS feeds the device's `am instrument` (-e key value). Stored as arrays so values keep their spaces;
# the quoting that makes spaces survive Gradle's --args splitting and adb-shell re-splitting is applied
# at each use site via gq/shq below.
declare -a COMMON=()
declare -a EARGS=()

# gq: quote a token for Gradle's --args string. Gradle's ArgumentsSplitter honours double quotes, so a
#     value like "Super Mario 64" survives as one argument. shq: quote a value for a device shell
#     command line (single quotes, apostrophe-safe via the '\'' trick) so adb shell doesn't re-split it.
gq()  { printf '"%s"' "$1"; }
shq() { local s=${1//\'/\'\\\'\'}; printf "'%s'" "$s"; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dir)              CORPUS="$2"; shift 2 ;;
    --out)              OUT="$2"; shift 2 ;;
    --label-host)       LABEL_HOST="$2"; shift 2 ;;
    --label-device)     LABEL_DEVICE="$2"; shift 2 ;;
    --serial)           SERIAL="$2"; shift 2 ;;
    --top)              TOP="$2"; shift 2 ;;
    --batch)            BATCH="$2"; shift 2 ;;
    --ext|--seconds|--game|--subsong|--max-wall-seconds)
                        COMMON+=("$1" "$2"); EARGS+=("-e" "${1#--}" "$2"); shift 2 ;;
    --every-song|--overwrite)
                        COMMON+=("$1"); EARGS+=("-e" "${1#--}" "true"); shift ;;
    -h|--help)          sed -n '2,45p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

[[ -n "${CORPUS}" ]] || { echo "error: --dir <corpus> is required" >&2; exit 2; }
[[ -d "${CORPUS}" ]] || { echo "error: corpus dir not found: ${CORPUS}" >&2; exit 2; }
[[ "${BATCH}" =~ ^[0-9]+$ && "${BATCH}" -gt 0 ]] || { echo "error: --batch must be a positive integer" >&2; exit 2; }
CORPUS="$(cd "${CORPUS}" && pwd)"   # absolute — host render walks it; the push stages it on-device
mkdir -p "${OUT}"; OUT="$(cd "${OUT}" && pwd)"

ADB=(adb)
[[ -n "${SERIAL}" ]] && { ADB=(adb -s "${SERIAL}"); export ANDROID_SERIAL="${SERIAL}"; }

if ! "${ADB[@]}" get-state >/dev/null 2>&1; then
  echo "error: no device. Connect one (adb devices) or pass --serial <id>." >&2
  exit 1
fi

# Default the device label to its primary ABI so the run dir + diff report name themselves (e.g.
# ab-compare/arm64-v8a, diff-vs-host.tsv) — self-documenting which ISA was tested.
if [[ -z "${LABEL_DEVICE}" ]]; then
  LABEL_DEVICE="$("${ADB[@]}" shell getprop ro.product.cpu.abi | tr -d '\r[:space:]')"
  [[ -n "${LABEL_DEVICE}" ]] || LABEL_DEVICE="device"
fi

echo "==> corpus:      ${CORPUS}"
echo "==> runs root:   ${OUT}"
echo "==> host label:  ${LABEL_HOST}   device label: ${LABEL_DEVICE}   batch size: ${BATCH}"
echo

# Phase A — one Gradle build: host native libs + build & install the device-test APK. Doing both in a
# single invocation lets Gradle parallelize the tasks and avoids a second concurrent build in phase B.
echo "==> [build] host native libs + device-test APK"
"${GRADLEW}" :apps:jvm:chipboxHostNativeLibs :apps:abrender-core:installAndroidDeviceTest

# Stage the corpus once (clean any prior run first). It stays for every batch (each batch walks it
# on-device); only the rendered WAVs are deleted between batches.
echo "==> [device] staging corpus → ${REMOTE_IN}"
"${ADB[@]}" shell rm -rf "${REMOTE_BASE}"
"${ADB[@]}" shell mkdir -p "${REMOTE_IN}"
"${ADB[@]}" push "${CORPUS}/." "${REMOTE_IN}" >/dev/null

# Host render in the background, in a RESUME LOOP — produces the run we diff against AND announces the
# track count we use to size the device batches. A track that wedges in an uninterruptible native call
# trips the renderer's watchdog, which halts the JVM (Gradle reports failure); relaunching resumes (the
# wedged track is recovered as a recorded failure and skipped). Loop until a run completes cleanly
# (exit 0 = every track rendered or recorded) or it stops making progress. Without this the host stops
# at the first wedge and every later track shows up as ONLY_IN_B against the (batched, resilient) device.
HOST_LOG="${OUT}/.host-render.log"
# Build the render arg string with every value double-quoted, so spaces (in the path, label, or a
# --game value) survive Gradle's --args tokenizer.
HOST_ARGS="render --dir $(gq "${CORPUS}") --out $(gq "${OUT}") --label $(gq "${LABEL_HOST}")"
for tok in "${COMMON[@]}"; do HOST_ARGS+=" $(gq "${tok}")"; done
host_render_loop() {
  local prev=-1 cur tries=0
  while :; do
    if "${GRADLEW}" :apps:abrender:run --args="${HOST_ARGS}" >>"${HOST_LOG}" 2>&1; then
      return 0
    fi
    cur=$(tail -n +2 "${OUT}/${LABEL_HOST}/metrics.tsv" 2>/dev/null | wc -l | tr -d ' ')
    tries=$((tries + 1))
    if [[ "${cur}" -le "${prev}" || "${tries}" -ge "${MAX_RETRIES}" ]]; then
      echo "HOST: stopped progressing at ${cur} tracks after ${tries} relaunch(es); giving up." >>"${HOST_LOG}"
      return 1
    fi
    prev=${cur}
  done
}
echo "==> [render] host (${LABEL_HOST}) in background, resume-looped → ${HOST_LOG}"
: >"${HOST_LOG}"
host_render_loop &
HOST_PID=$!

# Discover N (total selected tracks) from the host render's early "Rendering N track(s)" line; fall
# back to its final "rendered=.. skipped=.." if it finished before we looked.
echo "==> [device] waiting for the host to report the track count…"
N=""
for _ in $(seq 1 300); do
  if grep -qE 'Rendering [0-9]+ track' "${HOST_LOG}" 2>/dev/null; then
    N=$(grep -oE 'Rendering [0-9]+ track' "${HOST_LOG}" | head -1 | grep -oE '[0-9]+'); break
  fi
  if grep -qE '^Done\. rendered=' "${HOST_LOG}" 2>/dev/null; then
    r=$(grep -oE 'rendered=[0-9]+' "${HOST_LOG}" | head -1 | grep -oE '[0-9]+')
    s=$(grep -oE 'skipped=[0-9]+' "${HOST_LOG}" | head -1 | grep -oE '[0-9]+')
    N=$(( ${r:-0} + ${s:-0} )); break
  fi
  if ! kill -0 "${HOST_PID}" 2>/dev/null; then break; fi
  sleep 1
done
if [[ -z "${N}" || "${N}" -le 0 ]]; then
  echo "error: could not determine track count (corpus empty, or host render failed):" >&2
  tail -25 "${HOST_LOG}" >&2; wait "${HOST_PID}" 2>/dev/null || true; exit 1
fi

S=$(( (N + BATCH - 1) / BATCH ))
echo "==> [device] ${N} tracks → ${S} batch(es) of up to ${BATCH}; pulling + deleting renders between batches"

# Pre-build the user `-e key value` portion of the device command with single-quoted values (the
# EARGS array is [-e, key, value, …]); per-batch dir/out/label/shards/shard are prepended below. The
# whole thing is sent to adb shell as ONE string so the device shell — not adb — does the splitting,
# and the quotes keep multi-word values (e.g. -e game 'Super Mario 64') intact.
DEV_EARGS=""
j=0
while (( j < ${#EARGS[@]} )); do
  DEV_EARGS+=" -e ${EARGS[j + 1]} $(shq "${EARGS[j + 2]}")"
  j=$((j + 3))
done

# Device batch loop: render shard i (the shard split is disjoint and covers everything, so S shards
# together render all N tracks), pull its output, then delete the device WAVs to bound storage.
for (( i=0; i<S; i++ )); do
  echo "    -- batch $((i + 1))/${S} (shard ${i}) --"
  BATCH_LOG="${OUT}/.device-batch-${i}.log"
  # Resume-loop the shard: a clean run prints JUnit's "OK (N tests)". A wedge halts the test process
  # (Runtime.halt) — no OK line — so relaunch; resume skips done tracks (their WAVs aren't deleted until
  # the shard finishes) and records the wedged one as a failure. Stop on no progress or the retry cap.
  DEV_CMD="am instrument -w -r -e dir $(shq "${REMOTE_IN}") -e out $(shq "${REMOTE_OUT}")"
  DEV_CMD+=" -e label $(shq "${LABEL_DEVICE}") -e shards ${S} -e shard ${i}${DEV_EARGS}"
  DEV_CMD+=" ${PKG}/${RUNNER}"
  bprev=-1; tries=0
  while :; do
    "${ADB[@]}" shell "${DEV_CMD}" >"${BATCH_LOG}" 2>&1 || true
    grep -qE 'OK \([0-9]+ test' "${BATCH_LOG}" && break
    cur=$("${ADB[@]}" shell "wc -l < $(shq "${REMOTE_OUT}/${LABEL_DEVICE}/metrics.tsv")" 2>/dev/null | tr -d '[:space:]')
    cur=${cur:-0}; tries=$((tries + 1))
    if [[ "${cur}" -le "${bprev}" || "${tries}" -ge "${MAX_RETRIES}" ]]; then
      echo "       WARNING: batch ${i} did not complete cleanly after ${tries} attempt(s) — see ${BATCH_LOG}"
      break
    fi
    bprev=${cur}
    echo "       batch ${i}: wedge/crash; resuming (attempt $((tries + 1)))…"
  done

  # Pull this batch's output (cumulative metrics.tsv + the new WAVs) into the host run dir, then drop
  # the device WAVs now that they're safely on the host.
  "${ADB[@]}" pull "${REMOTE_OUT}/${LABEL_DEVICE}" "${OUT}/" >/dev/null 2>&1 || true
  # Quote the dir but leave the * outside the quotes so the device shell still globs it.
  "${ADB[@]}" shell "rm -f $(shq "${REMOTE_OUT}/${LABEL_DEVICE}/wav/")*" 2>/dev/null || true

  # Progress from the (reliable) pulled metrics, since the instrumentation's stdout isn't in -r output.
  done_n=$(tail -n +2 "${OUT}/${LABEL_DEVICE}/metrics.tsv" 2>/dev/null | wc -l | tr -d ' ')
  echo "       device progress: ${done_n}/${N} tracks rendered (device WAVs cleared)"
done

HOST_STATUS=0; wait "${HOST_PID}" || HOST_STATUS=$?
echo "==> [render] host finished (status ${HOST_STATUS})"

# Final cleanup: remove the staged corpus and any remaining device output.
echo "==> [device] removing staged corpus + output from device"
"${ADB[@]}" shell rm -rf "${REMOTE_BASE}" 2>/dev/null || true

[[ -f "${OUT}/${LABEL_HOST}/metrics.tsv" ]] || {
  echo "error: host run produced no metrics (${OUT}/${LABEL_HOST}/metrics.tsv). See ${HOST_LOG}." >&2; exit 1; }
[[ -f "${OUT}/${LABEL_DEVICE}/metrics.tsv" ]] || {
  echo "error: device run produced no metrics (${OUT}/${LABEL_DEVICE}/metrics.tsv)." >&2; exit 1; }

# Phase C — diff. Absolute --out + plain labels (no slashes) so resolution is CWD-independent and the
# report filename (diff-vs-<labelHost>.tsv) is valid.
echo
echo "==> [diff] ${LABEL_DEVICE} (device) vs ${LABEL_HOST} (host)"
"${GRADLEW}" -q :apps:abrender:run \
  --args="diff --out $(gq "${OUT}") --a $(gq "${LABEL_HOST}") --b $(gq "${LABEL_DEVICE}") --top ${TOP}"
