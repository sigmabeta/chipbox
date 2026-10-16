#!/usr/bin/env bash
#
# verify.sh — render a corpus again and fail unless it matches a recorded run.
#
# The compare-and-gate half of the A/B harness. It renders the corpus fresh (on the host or a
# connected device, via record.sh) and diffs the result against a run you recorded earlier with
# record.sh. Divergence beyond the tolerance exits non-zero, so it works as a `if`/CI step:
#
#   record.sh --corpus C                                  # golden/baseline reference on the host
#   verify.sh --where device --corpus C                   # arm64 must match the x86_64 reference
#
#   record.sh --corpus C --label baseline                 # before a native change …
#   verify.sh --corpus C --against baseline               # … after it, on the same machine (strict)
#
# Tolerance is `auto` by default: cross-isa (a ±1 LSB allowance) when the reference and this render
# came from different places, strict bit-identity otherwise. A run's place is stamped by record.sh.
#
set -euo pipefail

usage() {
  cat <<'EOF'
verify.sh — render a corpus and fail unless it matches a recorded run.

Usage:
  verify.sh --where host|device --corpus DIR [--against NAME] [options]

Required:
  --where <host|device>    where to render this candidate          (default: host)
  --corpus <path>          corpus directory                        (required)

Reference:
  --against <name|path>    run to compare against: a label under    (default: reference)
                           --out, or a path to a run dir
  --tolerance <t>          auto | strict | cross-isa                (default: auto)
                             auto       cross-isa if the reference and this render
                                        differ in place, strict otherwise
                             strict     bit-identical (same-machine before/after)
                             cross-isa  allow ±1 LSB (arm64 vs x86_64)

Run store:
  --out <dir>              runs root                               (default: <repo>/ab-runs)

Render knobs (must match the ones the reference was recorded with):
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
CANDIDATE_LABEL=".verify"

WHERE="host"
CORPUS=""
AGAINST="reference"
OUT="${REPO_ROOT}/ab-runs"
TOLERANCE="auto"
PARALLEL=1
SERIAL=""
SKIP_BUILD=0
declare -a FORWARD=()   # render knobs forwarded verbatim to record.sh

while [[ $# -gt 0 ]]; do
  case "$1" in
    --where)            WHERE="$2"; shift 2 ;;
    --corpus)           CORPUS="$2"; shift 2 ;;
    --against)          AGAINST="$2"; shift 2 ;;
    --out)              OUT="$2"; shift 2 ;;
    --tolerance)        TOLERANCE="$2"; shift 2 ;;
    --parallel)         PARALLEL="$2"; shift 2 ;;
    --serial)           SERIAL="$2"; shift 2 ;;
    --skip-build)       SKIP_BUILD=1; shift ;;
    --seconds|--ext|--game|--subsong|--limit|--max-wall-seconds)
                        FORWARD+=("$1" "$2"); shift 2 ;;
    --every-song)       FORWARD+=("--every-song"); shift ;;
    -h|--help)          usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

[[ "${WHERE}" == host || "${WHERE}" == device ]] || { echo "error: --where must be host or device" >&2; exit 2; }
[[ -n "${CORPUS}" ]] || { echo "error: --corpus <dir> is required" >&2; exit 2; }
[[ -d "${CORPUS}" ]] || { echo "error: corpus dir not found: ${CORPUS}" >&2; exit 2; }
[[ "${TOLERANCE}" == auto || "${TOLERANCE}" == strict || "${TOLERANCE}" == cross-isa ]] ||
  { echo "error: --tolerance must be auto, strict, or cross-isa" >&2; exit 2; }

OUT="$(mkdir -p "${OUT}" && cd "${OUT}" && pwd)"
if [[ -d "${AGAINST}" ]]; then
  REF_DIR="$(cd "${AGAINST}" && pwd)"
else
  REF_DIR="${OUT}/${AGAINST}"
fi
[[ -f "${REF_DIR}/metrics.tsv" ]] || {
  echo "error: no recorded run at ${REF_DIR} (expected metrics.tsv)." >&2
  echo "       Record one first: ${SCRIPT_DIR}/record.sh --where ${WHERE} --corpus \"${CORPUS}\"" >&2
  exit 2
}

# Resolve `auto`: compare where each side was rendered. Missing provenance falls back to "device
# renders are cross-ISA against a host reference", the common golden case.
ref_where=""
if [[ "${TOLERANCE}" == auto ]]; then
  ref_where="$(sed -n 's/^where=//p' "${REF_DIR}/.provenance" 2>/dev/null || true)"
  if [[ -z "${ref_where}" ]]; then
    if [[ "${WHERE}" == device ]]; then TOLERANCE=cross-isa; else TOLERANCE=strict; fi
  elif [[ "${ref_where}" != "${WHERE}" ]]; then
    TOLERANCE=cross-isa
  else
    TOLERANCE=strict
  fi
fi
echo "==> verifying ${WHERE} render of ${CORPUS}"
echo "    reference: ${REF_DIR}${ref_where:+ (recorded on ${ref_where})}"
echo "    tolerance: ${TOLERANCE}"

gq() { printf '"%s"' "$1"; }

# Render the candidate fresh via record.sh (same place + knobs), stamped so it can be a reference too.
record_args=(--where "${WHERE}" --corpus "${CORPUS}" --label "${CANDIDATE_LABEL}" --out "${OUT}" --overwrite)
if [[ "${WHERE}" == device ]]; then
  if [[ -n "${SERIAL}" ]]; then record_args+=(--serial "${SERIAL}"); fi
  if [[ "${SKIP_BUILD}" -eq 1 ]]; then record_args+=(--skip-build); fi
fi
if (( PARALLEL > 1 )); then record_args+=(--parallel "${PARALLEL}"); fi
record_args+=("${FORWARD[@]}")
ABRENDER_QUIET_HINT=1 "${SCRIPT_DIR}/record.sh" "${record_args[@]}"

CANDIDATE_DIR="${OUT}/${CANDIDATE_LABEL}"
gate="diff --out $(gq "${OUT}") --a $(gq "${REF_DIR}") --b $(gq "${CANDIDATE_DIR}") --tolerance ${TOLERANCE} --check"

set +e
"${GRADLEW}" -q :testing:abrender:run --args="${gate}"
rc=$?
set -e

if (( rc == 0 )); then
  echo "verify: PASS (candidate at ${CANDIDATE_DIR})"
else
  echo "verify: FAIL (candidate at ${CANDIDATE_DIR}; listen under its wav/)" >&2
fi
exit "${rc}"
