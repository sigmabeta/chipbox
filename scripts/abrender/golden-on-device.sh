#!/usr/bin/env bash
#
# golden-on-device.sh — run the bit-identity golden check on a connected Android device (arm64).
#
# The host `:testing:abrender:recordPcmGolden`/`verifyPcmGolden` tasks only exercise the x86_64 emulator
# cores. This pushes a corpus and, in verify mode, a golden run recorded on the host, runs the
# SAME comparison on a real device (arm64), and fails if any track is not bit-identical. PCM must not
# depend on the CPU architecture, so a divergence here is a real bug — this is the automated form of
# the cross-ISA `ab-compare.sh`.
#
# Usage:
#   scripts/abrender/golden-on-device.sh --corpus <local-corpus> [--golden <host-golden-run>]
#   scripts/abrender/golden-on-device.sh --corpus <local-corpus> --record [--golden <host-golden-run>]
#
# Options:
#   --corpus <path>          local corpus dir to push to the device              (required)
#   --golden <path>          host golden run dir (metrics.tsv + wav/)            (default: <repo>/ab-goldens/golden)
#   --record                 record the golden set on device and pull it to --golden, instead of verifying
#   --seconds <n>            seconds of audio per track                          (default: 30)
#   --ext <a,b>              only these extensions, e.g. usf,miniusf             (default: all supported)
#   --game <substr>          only files whose parent folder contains <substr>
#   --every-song             every playable file (default: one representative per folder)
#   --subsong <n>            subsong index for multi-song formats                (default: 0)
#   --limit <n>              cap the number of tracks (quick checks)
#   --max-wall-seconds <n>   per-track wall-clock cap                            (default: 120)
#   --serial <id>            target a specific device (adb -s); else the only connected device
#   --skip-build             don't rebuild/reinstall the test APK (corpus push + run only)
#
# Notes:
#   - The corpus and golden run are pushed to the test app's external files dir
#     (/sdcard/Android/data/net.sigmabeta.chipbox.abrender.test/files/abrender). Pass the same
#     --seconds/--ext/... as when the host golden was recorded, or the track sets will differ.
#   - Exit status is the instrumentation result: 0 = pass, 1 = a divergence (or a test failure).
set -euo pipefail

PKG="net.sigmabeta.chipbox.abrender.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="net.sigmabeta.chipbox.abrender.PcmGoldenDeviceTest"
REMOTE_BASE="/sdcard/Android/data/${PKG}/files/abrender"
REMOTE_IN="${REMOTE_BASE}/in"
REMOTE_GOLDEN="${REMOTE_BASE}/golden"
REMOTE_WORK="${REMOTE_BASE}/golden-work"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

CORPUS=""
GOLDEN="${REPO_ROOT}/ab-goldens/golden"
RECORD=0
SERIAL=""
SKIP_BUILD=0
# Instrumentation -e key/value pairs forwarded to the test, mirroring the host golden properties.
declare -a EARGS=()

add_e() { EARGS+=("-e" "$1" "$2"); }
# Single-quote a value for a device shell command line (apostrophe-safe via the '\'' trick), so a
# multi-word value like -e game 'Super Mario 64' survives `adb shell` re-splitting on the device.
shq() { local s=${1//\'/\'\\\'\'}; printf "'%s'" "$s"; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --corpus)           CORPUS="$2"; shift 2 ;;
    --golden)           GOLDEN="$2"; shift 2 ;;
    --record)           RECORD=1; shift ;;
    --seconds)          add_e seconds "$2"; shift 2 ;;
    --ext)              add_e ext "$2"; shift 2 ;;
    --game)             add_e game "$2"; shift 2 ;;
    --subsong)          add_e subsong "$2"; shift 2 ;;
    --limit)            add_e limit "$2"; shift 2 ;;
    --max-wall-seconds) add_e max-wall-seconds "$2"; shift 2 ;;
    --every-song)       add_e every-song true; shift ;;
    --serial)           SERIAL="$2"; shift 2 ;;
    --skip-build)       SKIP_BUILD=1; shift ;;
    -h|--help)          sed -n '2,42p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

[[ -n "${CORPUS}" ]] || { echo "error: --corpus <local-corpus> is required" >&2; exit 2; }
[[ -d "${CORPUS}" ]] || { echo "error: corpus dir not found: ${CORPUS}" >&2; exit 2; }
if [[ "${RECORD}" -eq 0 && ! -f "${GOLDEN}/metrics.tsv" ]]; then
  echo "error: no golden run at ${GOLDEN} (expected metrics.tsv)." >&2
  echo "       Record one on the host first:" >&2
  echo "         ./gradlew :testing:abrender:recordPcmGolden -Pchipbox.golden.corpus=${CORPUS}" >&2
  exit 2
fi

ADB=(adb)
[[ -n "${SERIAL}" ]] && ADB=(adb -s "${SERIAL}")

if ! "${ADB[@]}" get-state >/dev/null 2>&1; then
  echo "error: no device. Connect one (adb devices) or pass --serial <id>." >&2
  exit 1
fi

# The corpus + golden run go to fixed device paths; the test honours -e dir/-e golden/-e work.
add_e dir "${REMOTE_IN}"
add_e golden "${REMOTE_GOLDEN}"
add_e work "${REMOTE_WORK}"

if [[ "${SKIP_BUILD}" -eq 0 ]]; then
  echo "==> Building + installing the device-test APK"
  ( cd "${REPO_ROOT}" && ./gradlew :testing:abrender-core:installAndroidDeviceTest )
fi

echo "==> Pushing corpus → ${REMOTE_IN}"
"${ADB[@]}" shell mkdir -p "${REMOTE_IN}"
"${ADB[@]}" push "${CORPUS}/." "${REMOTE_IN}" >/dev/null

if [[ "${RECORD}" -eq 1 ]]; then
  echo "==> Recording goldens on device"
  METHOD="recordGolden"
else
  echo "==> Pushing golden set → ${REMOTE_GOLDEN}"
  "${ADB[@]}" shell rm -rf "${REMOTE_GOLDEN}"
  "${ADB[@]}" shell mkdir -p "${REMOTE_GOLDEN}"
  "${ADB[@]}" push "${GOLDEN}/." "${REMOTE_GOLDEN}" >/dev/null
  echo "==> Verifying on device (arm64) against the host golden"
  METHOD="verifyGolden"
fi

# Build ONE command string with single-quoted -e values and hand it to adb shell whole, so the device
# shell (not adb) does the word-splitting and multi-word values stay intact.
DEV_CMD="am instrument -w -r -e class ${TEST_CLASS}#${METHOD}"
j=0
while (( j < ${#EARGS[@]} )); do
  DEV_CMD+=" -e ${EARGS[j + 1]} $(shq "${EARGS[j + 2]}")"
  j=$((j + 3))
done
DEV_CMD+=" ${PKG}/${RUNNER}"

set +e
OUT="$("${ADB[@]}" shell "${DEV_CMD}" 2>&1)"
RC=$?
set -e
printf '%s\n' "${OUT}"

if [[ "${RECORD}" -eq 1 ]]; then
  echo "==> Pulling recorded golden → ${GOLDEN}"
  mkdir -p "$(dirname "${GOLDEN}")"
  rm -rf "${GOLDEN}"
  "${ADB[@]}" pull "${REMOTE_GOLDEN}" "$(dirname "${GOLDEN}")" >/dev/null
fi

if grep -q "FAILURES!!!" <<<"${OUT}"; then
  echo "golden check FAILED on device — see the divergence above." >&2
  exit 1
fi
if ! grep -qE "OK \([0-9]+ test" <<<"${OUT}"; then
  echo "golden check did not complete cleanly (rc=${RC}) — a wedge or crash may have halted it." >&2
  exit 1
fi

if [[ "${RECORD}" -eq 1 ]]; then
  echo "Done. Golden set recorded on device at ${GOLDEN}."
else
  echo "Done. Device (arm64) render is bit-identical to the host golden."
fi
