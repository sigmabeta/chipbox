#!/usr/bin/env bash
#
# record.sh — render a corpus into a run, on the host or a connected Android device.
#
# The render engine is shared by both places (:testing:abrender-core): the host JVM exercises the
# x86_64 emulator cores, the device's instrumentation exercises the arm64 ones. Both write the same
# shape — <out>/<label>/{metrics.tsv,wav/} — plus a `.provenance` stamp so verify.sh can tell whether
# a reference and a candidate came from the same place. Pair it with verify.sh:
#
#   record.sh --corpus C && verify.sh --where device --corpus C
#
set -euo pipefail

usage() {
  cat <<'EOF'
record.sh — render a corpus into a run (host or device).

Usage:
  record.sh --where host|device --corpus DIR [options]

Required:
  --where <host|device>    where to render                         (default: host)
  --corpus <path>          corpus directory                        (required)

Run store (shared with verify.sh):
  --label <name>           run name -> <out>/<name>                (default: reference)
  --out <dir>              runs root                               (default: <repo>/ab-runs)

Render knobs (pass the SAME ones to verify.sh):
  --seconds <n>            seconds of audio per track              (default: 30)
  --ext <a,b>              only these extensions, e.g. usf,miniusf  (default: all)
  --game <substr>          only files whose parent folder contains <substr>
  --every-song             every playable file (default: one per folder)
  --subsong <n>            subsong index for multi-song formats     (default: 0)
  --limit <n>              cap the number of tracks
  --max-wall-seconds <n>   per-track wall-clock cap                 (default: 120)

Host only:
  --parallel <n>           render with n JVMs over disjoint shards  (default: 1)

Device only:
  --serial <id>            target a specific device (adb -s)        (default: the only one)
  --skip-build             don't rebuild/reinstall the test APK

Other:
  -h, --help               show this help
EOF
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
GRADLEW="${REPO_ROOT}/gradlew"

PKG="net.sigmabeta.chipbox.abrender.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
REMOTE_BASE="/sdcard/Android/data/${PKG}/files/abrender"
REMOTE_IN="${REMOTE_BASE}/in"
REMOTE_OUT="${REMOTE_BASE}/out"

WHERE="host"
CORPUS=""
LABEL="reference"
OUT="${REPO_ROOT}/ab-runs"
PARALLEL=1
SERIAL=""
SKIP_BUILD=0
declare -a FLAGS=()   # CLI render flags (--key value / bare --flag)
declare -a EARGS=()   # device instrumentation -e key value

while [[ $# -gt 0 ]]; do
  case "$1" in
    --where)            WHERE="$2"; shift 2 ;;
    --corpus)           CORPUS="$2"; shift 2 ;;
    --label)            LABEL="$2"; shift 2 ;;
    --out)              OUT="$2"; shift 2 ;;
    --parallel)         PARALLEL="$2"; shift 2 ;;
    --serial)           SERIAL="$2"; shift 2 ;;
    --skip-build)       SKIP_BUILD=1; shift ;;
    --seconds|--ext|--game|--subsong|--limit|--max-wall-seconds)
                        FLAGS+=("$1" "$2"); EARGS+=(-e "${1#--}" "$2"); shift 2 ;;
    --every-song)       FLAGS+=("--every-song"); EARGS+=(-e every-song true); shift ;;
    --overwrite)        FLAGS+=("--overwrite"); EARGS+=(-e overwrite true); shift ;;
    -h|--help)          usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

[[ "${WHERE}" == host || "${WHERE}" == device ]] || { echo "error: --where must be host or device" >&2; exit 2; }
[[ -n "${CORPUS}" ]] || { echo "error: --corpus <dir> is required" >&2; exit 2; }
[[ -d "${CORPUS}" ]] || { echo "error: corpus dir not found: ${CORPUS}" >&2; exit 2; }
CORPUS="$(cd "${CORPUS}" && pwd)"
(( PARALLEL >= 1 )) || { echo "error: --parallel must be >= 1" >&2; exit 2; }
if [[ "${WHERE}" == device && "${PARALLEL}" -gt 1 ]]; then
  echo "error: --parallel applies to --where host only" >&2; exit 2
fi

OUT="$(mkdir -p "${OUT}" && cd "${OUT}" && pwd)"
RUN_DIR="${OUT}/${LABEL}"

# gq: quote a token for Gradle's --args (ArgumentsSplitter honours double quotes). shq: quote a value
# for a device shell command line (single quotes, apostrophe-safe) so adb shell doesn't re-split it.
gq()  { printf '"%s"' "$1"; }
shq() { local s=${1//\'/\'\\\'\'}; printf "'%s'" "$s"; }

render_host_serial() {
  local a="render --dir $(gq "${CORPUS}") --out $(gq "${OUT}") --label $(gq "${LABEL}")"
  local t
  for t in "${FLAGS[@]}"; do a+=" $(gq "${t}")"; done
  "${GRADLEW}" :testing:abrender:run --args="${a}"
}

render_host_parallel() {
  local shards="${PARALLEL}"
  "${GRADLEW}" :testing:abrender:abrenderRuntime
  local runtime="${REPO_ROOT}/testing/abrender/build/abrender-runtime"
  local cp="${runtime}/lib/*" libdir="${runtime}/native" logdir="${OUT}/.logs"
  local main="net.sigmabeta.chipbox.abrender.MainKt"
  mkdir -p "${logdir}"

  run_shard() {
    local i="$1" log="${logdir}/${LABEL}-s${i}.log" tries=0
    while :; do
      tries=$((tries + 1))
      if java -Djava.library.path="${libdir}" -cp "${cp}" "${main}" render \
          --dir "${CORPUS}" --out "${OUT}" --label "${LABEL}-s${i}" \
          --shards "${shards}" --shard "${i}" "${FLAGS[@]}" >"${log}" 2>&1; then
        return 0
      fi
      if grep -q '^Done\.' "${log}"; then return 0; fi
      if ! grep -q '^Rendering [0-9]' "${log}"; then
        echo "shard ${i}: render did not start (see ${log}) — giving up." >&2
        return 1
      fi
      if (( tries >= 50 )); then echo "shard ${i}: gave up after ${tries} tries" >&2; return 1; fi
      echo "shard ${i}: relaunching after a wedge/crash (try ${tries})…" >&2
    done
  }

  echo "Rendering with ${shards} shards → ${RUN_DIR}"
  local pids=() fail=0 p
  local i
  for (( i=0; i<shards; i++ )); do run_shard "${i}" & pids+=($!); done
  for p in "${pids[@]}"; do wait "${p}" || fail=1; done

  # Merge the shards into one run dir: hardlink WAVs (instant, same fs), concatenate metrics rows.
  rm -rf "${RUN_DIR}"
  mkdir -p "${RUN_DIR}/wav"
  head -1 "${OUT}/${LABEL}-s0/metrics.tsv" >"${RUN_DIR}/metrics.tsv"
  for (( i=0; i<shards; i++ )); do
    local sd="${OUT}/${LABEL}-s${i}"
    if [[ -f "${sd}/metrics.tsv" ]]; then
      tail -n +2 "${sd}/metrics.tsv" >>"${RUN_DIR}/metrics.tsv"
    fi
    if [[ -d "${sd}/wav" ]]; then
      ln -f "${sd}"/wav/* "${RUN_DIR}/wav/" 2>/dev/null || true
    fi
  done
  local total=$(( $(wc -l <"${RUN_DIR}/metrics.tsv") - 1 ))
  echo "Merged ${shards} shards → ${RUN_DIR} (${total} tracks)"
  (( fail == 0 )) || echo "WARNING: at least one shard gave up before finishing." >&2
  return "${fail}"
}

render_device() {
  local -a adb=(adb)
  if [[ -n "${SERIAL}" ]]; then adb=(adb -s "${SERIAL}"); fi
  if ! "${adb[@]}" get-state >/dev/null 2>&1; then
    echo "error: no device. Connect one (adb devices) or pass --serial <id>." >&2
    exit 1
  fi
  if (( SKIP_BUILD == 0 )); then
    echo "==> Building + installing the device-test APK"
    ( cd "${REPO_ROOT}" && "${GRADLEW}" :testing:abrender-core:installAndroidDeviceTest )
  fi

  echo "==> Pushing corpus → ${REMOTE_IN}"
  # Fresh stage each run: pushing over files a prior chmod left world-readable makes adb's fchown fail
  # on the FUSE mount; then open it up, or the test app (not in adb's group) sees an empty corpus.
  "${adb[@]}" shell rm -rf "${REMOTE_IN}" 2>/dev/null || true
  "${adb[@]}" shell mkdir -p "${REMOTE_IN}"
  "${adb[@]}" push "${CORPUS}/." "${REMOTE_IN}" >/dev/null
  "${adb[@]}" shell chmod -R a+rX "${REMOTE_IN}"

  echo "==> Rendering on device (arm64)"
  local dev_cmd="am instrument -w -r -e dir $(shq "${REMOTE_IN}") -e out $(shq "${REMOTE_OUT}") -e label $(shq "${LABEL}")"
  local j
  for (( j=0; j<${#EARGS[@]}; j+=3 )); do dev_cmd+=" -e ${EARGS[j + 1]} $(shq "${EARGS[j + 2]}")"; done
  dev_cmd+=" ${PKG}/${RUNNER}"
  local out
  out="$("${adb[@]}" shell "${dev_cmd}" 2>&1)" || true
  printf '%s\n' "${out}"
  if grep -q 'FAILURES!!!' <<<"${out}"; then
    echo "error: device render failed (see FAILURES above)." >&2; exit 1
  fi

  echo "==> Pulling run → ${RUN_DIR}"
  "${adb[@]}" pull "${REMOTE_OUT}/${LABEL}" "${OUT}/" >/dev/null
}

case "${WHERE}" in
  host)   if (( PARALLEL > 1 )); then render_host_parallel; else render_host_serial; fi ;;
  device) render_device ;;
esac

[[ -f "${RUN_DIR}/metrics.tsv" ]] || {
  echo "error: no metrics.tsv at ${RUN_DIR} — the render produced nothing." >&2; exit 1; }

# Provenance (host-side, after the pull for device runs): lets verify.sh pick the right tolerance.
printf 'where=%s\n' "${WHERE}" >"${RUN_DIR}/.provenance"

echo "Done. Run at ${RUN_DIR} (metrics.tsv + wav/)."
if [[ -z "${ABRENDER_QUIET_HINT:-}" ]]; then
  echo "Verify it:  ${SCRIPT_DIR}/verify.sh --where ${WHERE} --corpus \"${CORPUS}\" --against \"${LABEL}\""
fi
