#!/usr/bin/env bash
#
# run-on-device.sh — render an A/B corpus on a connected Android device (arm64), then pull the run.
#
# The desktop `:apps:abrender` only proves the x86_64 emulators. This drives the SAME render engine
# (:apps:abrender-core, the `DeviceRenderTest` instrumentation) on a real device so the arm64 `.so`
# files are exercised. Output is shape-identical to a desktop run (`<out>/<label>/metrics.tsv` + per-
# track WAVs under `wav/`), so you can diff it against an x86_64 oracle with the desktop tool:
#
#   ./gradlew :apps:abrender:run --args="diff --a <x86_64-run> --b ./ab-runs-device/<label>"
#
# Usage:
#   apps/abrender-core/run-on-device.sh --dir <local-corpus> [--label arm64] [options]
#
# Options (each maps to a desktop `abrender render` flag, passed as an instrumentation arg):
#   --dir <path>           local corpus dir to push to the device                 (required)
#   --label <name>         run name; output goes to <out>/<name>/                  (default: arm64)
#   --out <path>           local dir to pull the run into                          (default: ./ab-runs-device)
#   --seconds <n>          seconds of audio per track                              (default: 30)
#   --ext <a,b>            only these extensions, e.g. usf,miniusf                 (default: all supported)
#   --game <substr>        only files whose parent folder contains <substr>
#   --every-song           every playable file (default: one representative per folder)
#   --subsong <n>          subsong index for multi-song formats                    (default: 0)
#   --limit <n>            cap the number of tracks (quick checks)
#   --overwrite            re-render tracks already present (default: resume/skip)
#   --max-wall-seconds <n> per-track wall-clock cap                               (default: 120)
#   --serial <id>          target a specific device (adb -s); else the only connected device
#   --skip-build           don't rebuild/reinstall the test APK (corpus push + run only)
#
# Notes:
# - The corpus is pushed to, and the run written under, the test app's external files dir
#   (/sdcard/Android/data/net.sigmabeta.chipbox.abrender.test/files/abrender). adb can push/pull
#   there on standard builds; if an OEM blocks adb access to Android/data, render into a path the app
#   can read and pass it through with no change here (the test honours -e dir / -e out).
# - A track that wedges in an uninterruptible native call halts the process (the engine's watchdog);
#   re-run to resume past it — the on-device run dir keeps its progress just like the desktop one.
set -euo pipefail

PKG="net.sigmabeta.chipbox.abrender.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
REMOTE_BASE="/sdcard/Android/data/${PKG}/files/abrender"
REMOTE_IN="${REMOTE_BASE}/in"
REMOTE_OUT="${REMOTE_BASE}/out"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

CORPUS=""
LABEL="arm64"
LOCAL_OUT="./ab-runs-device"
SERIAL=""
SKIP_BUILD=0
# Instrumentation -e key/value pairs forwarded to DeviceRenderTest, mirroring desktop flags.
declare -a EARGS=()

add_e() { EARGS+=("-e" "$1" "$2"); }

# Single-quote a value for a device shell command line (apostrophe-safe via the '\'' trick), so a
# multi-word value like -e game 'Super Mario 64' survives `adb shell` re-splitting on the device.
shq() { local s=${1//\'/\'\\\'\'}; printf "'%s'" "$s"; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dir)              CORPUS="$2"; shift 2 ;;
    --label)            LABEL="$2"; add_e label "$2"; shift 2 ;;
    --out)              LOCAL_OUT="$2"; shift 2 ;;
    --seconds)          add_e seconds "$2"; shift 2 ;;
    --ext)              add_e ext "$2"; shift 2 ;;
    --game)             add_e game "$2"; shift 2 ;;
    --subsong)          add_e subsong "$2"; shift 2 ;;
    --limit)            add_e limit "$2"; shift 2 ;;
    --max-wall-seconds) add_e max-wall-seconds "$2"; shift 2 ;;
    --every-song)       add_e every-song true; shift ;;
    --overwrite)        add_e overwrite true; shift ;;
    --serial)           SERIAL="$2"; shift 2 ;;
    --skip-build)       SKIP_BUILD=1; shift ;;
    -h|--help)          sed -n '2,40p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

[[ -n "${CORPUS}" ]] || { echo "error: --dir <local-corpus> is required" >&2; exit 2; }
[[ -d "${CORPUS}" ]] || { echo "error: corpus dir not found: ${CORPUS}" >&2; exit 2; }

ADB=(adb)
[[ -n "${SERIAL}" ]] && ADB=(adb -s "${SERIAL}")

# Fail early with a clear message rather than deep inside gradle/adb if no device is attached.
if ! "${ADB[@]}" get-state >/dev/null 2>&1; then
  echo "error: no device. Connect one (adb devices) or pass --serial <id>." >&2
  exit 1
fi

# The test APK always honours -e dir/out; set them to the device paths the corpus is pushed to.
add_e dir "${REMOTE_IN}"
add_e out "${REMOTE_OUT}"

if [[ "${SKIP_BUILD}" -eq 0 ]]; then
  echo "==> Building + installing the device-test APK"
  ( cd "${REPO_ROOT}" && ./gradlew :apps:abrender-core:installAndroidDeviceTest )
fi

echo "==> Pushing corpus → ${REMOTE_IN}"
"${ADB[@]}" shell mkdir -p "${REMOTE_IN}"
"${ADB[@]}" push "${CORPUS}/." "${REMOTE_IN}" >/dev/null

echo "==> Rendering on device (am instrument)"
# Build ONE command string with single-quoted -e values and hand it to adb shell whole, so the device
# shell (not adb) does the word-splitting and multi-word values stay intact. -w waits for completion.
DEV_CMD="am instrument -w -r"
j=0
while (( j < ${#EARGS[@]} )); do
  DEV_CMD+=" -e ${EARGS[j + 1]} $(shq "${EARGS[j + 2]}")"
  j=$((j + 3))
done
DEV_CMD+=" ${PKG}/${RUNNER}"
"${ADB[@]}" shell "${DEV_CMD}"

echo "==> Pulling run → ${LOCAL_OUT}/${LABEL}"
mkdir -p "${LOCAL_OUT}"
"${ADB[@]}" pull "${REMOTE_OUT}/${LABEL}" "${LOCAL_OUT}/" >/dev/null

echo "Done. On-device run at ${LOCAL_OUT}/${LABEL} (metrics.tsv + wav/)."
echo "Diff against an x86_64 oracle:"
echo "  ./gradlew :apps:abrender:run --args=\"diff --a <x86_64-run> --b ${LOCAL_OUT}/${LABEL}\""
