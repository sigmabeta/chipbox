#!/usr/bin/env bash
# Parallel A/B render: run one abrender JVM per logical core, each over a disjoint --shard of the
# corpus, then merge the shards into a single run dir for `diff`. A track that wedges in an
# uninterruptible native call only stalls its own shard; abrender's watchdog records it WEDGED and
# halts that shard, and the relaunch loop below resumes it past the offender.
#
# Usage:
#   apps/abrender/render-parallel.sh <label> <corpus-dir> [ext] [seconds] [shards] [max-wall-seconds]
#
# [shards] defaults to the machine's logical-core count (nproc) — one render JVM per core, since a
# single JVM can't render concurrently (singleton native state per backend).
#
# Native libs are read live from apps/jvm/libs (rebuild just the one under test between passes, e.g.
# `./gradlew :apps:jvm:nativeEmulatorUsf`); only app-code changes need a fresh `installDist`.
set -u

# Logical-core count, portably: nproc (Linux/coreutils), else sysctl (macOS/BSD), else getconf, else 1.
detect_cores() {
  if command -v nproc >/dev/null 2>&1; then nproc
  elif command -v sysctl >/dev/null 2>&1 && sysctl -n hw.logicalcpu >/dev/null 2>&1; then sysctl -n hw.logicalcpu
  else getconf _NPROCESSORS_ONLN 2>/dev/null || echo 1
  fi
}

LABEL="${1:?usage: render-parallel.sh <label> <corpus-dir> [ext] [seconds] [shards] [max-wall-seconds]}"
CORPUS="${2:?need corpus dir}"
EXT="${3:-usf,miniusf}"
SECONDS_PER="${4:-30}"
SHARDS="${5:-$(detect_cores)}"
MAXWALL="${6:-60}"
RETRY_CAP=50

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$REPO/apps/abrender/ab-runs"
LIBDIR="$REPO/apps/jvm/libs"
DIST="$REPO/apps/abrender/build/install/chipbox-abrender"
LOGDIR="$OUT/.logs"
mkdir -p "$OUT" "$LOGDIR"

if [ ! -d "$DIST/lib" ]; then
  echo "Installed dist not found at $DIST. Build it first:" >&2
  echo "  ./gradlew :apps:abrender:installDist" >&2
  exit 1
fi
CP="$DIST/lib/*"
MAIN="net.sigmabeta.chipbox.abrender.MainKt"

run_shard() {
  local i="$1" log="$LOGDIR/${LABEL}-s${i}.log" tries=0
  while :; do
    tries=$((tries + 1))
    java -Djava.library.path="$LIBDIR" -cp "$CP" "$MAIN" \
      render --dir "$CORPUS" --label "${LABEL}-s${i}" --out "$OUT" \
      --ext "$EXT" --seconds "$SECONDS_PER" --shards "$SHARDS" --shard "$i" \
      --max-wall-seconds "$MAXWALL" >"$log" 2>&1
    local rc=$?
    if [ $rc -eq 0 ]; then return 0; fi
    if grep -q "^Done\." "$log"; then return 0; fi
    if [ $tries -ge $RETRY_CAP ]; then
      echo "shard $i: gave up after $tries tries (last rc=$rc)" >&2
      return 1
    fi
    echo "shard $i: exited rc=$rc (likely wedge-halt), relaunching (try $tries)…" >&2
  done
}

echo "Launching $SHARDS shards over $CORPUS (label=$LABEL, ${SECONDS_PER}s, ext=$EXT, wall=${MAXWALL}s)…"
pids=()
for i in $(seq 0 $((SHARDS - 1))); do
  run_shard "$i" &
  pids+=($!)
done
fail=0
for p in "${pids[@]}"; do wait "$p" || fail=1; done

# Merge shard runs into a single canonical run dir for `diff`. Wavs are hardlinked (instant, same
# filesystem) so each shard dir stays intact for resume; metrics rows are concatenated under one header.
MERGED="$OUT/$LABEL"
rm -rf "$MERGED"
mkdir -p "$MERGED/wav"
head -1 "$OUT/${LABEL}-s0/metrics.tsv" >"$MERGED/metrics.tsv"
total=0
for i in $(seq 0 $((SHARDS - 1))); do
  sd="$OUT/${LABEL}-s${i}"
  [ -f "$sd/metrics.tsv" ] && { tail -n +2 "$sd/metrics.tsv" >>"$MERGED/metrics.tsv"; }
  [ -d "$sd/wav" ] && ln -f "$sd"/wav/* "$MERGED/wav/" 2>/dev/null
done
total=$(($(wc -l <"$MERGED/metrics.tsv") - 1))
echo "Merged $SHARDS shards → $MERGED  ($total rows, $(ls "$MERGED/wav" | wc -l) wavs)"
[ $fail -eq 0 ] || echo "WARNING: at least one shard gave up before finishing." >&2
exit $fail
