#!/usr/bin/env bash
#
# build-benchmark.sh — measure how fast THIS machine builds Chipbox.
#
# Chipbox is a ~200-module Kotlin Multiplatform graph with nine JNI emulator cores, so "can this box
# build it, and how fast" is dominated by CPU single/multi-thread throughput, RAM, and disk. This
# script times a small, fixed set of real Gradle builds and reduces them to one comparable number.
#
# WHAT IT MEASURES — each scenario runs the real build, unfiltered:
#   configure-cold   configuration of the whole JVM+Android task graph, fresh config cache (dry run:
#                    no compilation, so this isolates Gradle startup + configuration cost)
#   configure-warm   the same with the configuration cache warm (config-cache effective speed)
#   compile-jvm      :apps:jvm:classes from scratch — the shared Kotlin graph (the bulk of the work)
#   incremental-jvm  one-line edit in a low-level module, then recompile (delta-build ability)
#   static-analysis  ktlint + detekt from scratch (CPU-bound, parallel)
#   unit-test        jvmTest from scratch
#   android-debug    debug APK from scratch (native libs skipped by default)
#   screenshot       verifyPaparazziDebug from scratch
#   android-release  release APK from scratch: R8 + packaging, plus all nine CMake cores with --with-native
#
# WHAT IT DELIBERATELY DISABLES, so the number reflects the machine and not the network or history:
#   * the build cache (--no-build-cache): the remote cache at gradle.sebacloud.org is NOT read, so two
#     machines can't differ by cache luck. Local cache is off for the same reason.
#   * stale outputs: cold scenarios rm the affected build/ trees (and the CMake/native dirs) first and
#     then pass --rerun-tasks.
#   Dependencies in ~/.gradle/caches are kept — they're not build work. Warm up with `./gradlew help`
#   plus one full build before a serious comparison, and keep the machine otherwise idle.
#
# COMPARING TWO MACHINES: run this on both, then
#   scripts/build-benchmark.sh --baseline build/build-benchmark/results.json
# which prints per-scenario speedup and the overall geometric mean. There is no baked-in reference
# machine and no invented baseline — every ratio comes from a JSON run you actually produced.
# Read the ratios with the noise floor in mind: back-to-back runs on ONE machine typically differ by
# ~5-10% even with --repeat 3 (the reported min-of-N), so a difference under ~10% is not a difference.
#
# Usage:
#   scripts/build-benchmark.sh                      # tier 1+2 scenarios (default)
#   scripts/build-benchmark.sh --quick              # tier 1 only: configure + compile + incremental
#   scripts/build-benchmark.sh --full               # everything
#   scripts/build-benchmark.sh --with-native        # let android-release build the CMake natives too
#   scripts/build-benchmark.sh --only compile-jvm,unit-test
#   scripts/build-benchmark.sh --skip android-debug
#   scripts/build-benchmark.sh --repeat 3           # 3 samples per scenario, report min/mean/max
#   scripts/build-benchmark.sh --no-daemon          # CI-style: daemon startup counted in every run
#   scripts/build-benchmark.sh --max-workers 8
#   scripts/build-benchmark.sh --baseline other/results.json
#   scripts/build-benchmark.sh --list               # list scenarios
#   scripts/build-benchmark.sh --dry-run            # print the exact commands, run nothing
#   BENCH_OUT=/tmp/b scripts/build-benchmark.sh     # override the output folder
#
# Artifacts land in build/build-benchmark/: machine.txt + machine.kv (fingerprint), results.json,
# results.tsv, summary.txt, and logs/<scenario>-<n>.log (full Gradle output per sample).
#
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

OUT="${BENCH_OUT:-build/build-benchmark}"
GRADLE="./gradlew"

# name|label|weight|tier|purge|caps|gradle args
#   purge: none (warm — reuse current outputs) | config (clear the configuration cache) |
#          outputs (rm every module build/ tree outside sage/, plus the native build dirs)
#   caps:  android (needs the Android SDK) | native (needs the NDK; the CMake part is skipped unless
#          --with-native) — empty means "runs anywhere"
#   tier:  1 quick · 2 standard · 3 full
# Weight = how much a scenario counts toward the composite index, roughly its share of the real dev
# loop. Order is cheapest-feedback-first; incremental-jvm sits after compile-jvm on purpose so its
# priming build is already warm.
SCENARIOS=(
  "configure-cold|cold configuration of the JVM+Android graphs|2|1|config||:apps:jvm:classes :apps:android:assembleDebug --dry-run -Pchipbox.skipNative"
  "configure-warm|warm configuration (config-cache hit)|1|1|none||:apps:jvm:classes :apps:android:assembleDebug --dry-run -Pchipbox.skipNative"
  "compile-jvm|JVM shared graph compiled from scratch|4|1|outputs||:apps:jvm:classes"
  "incremental-jvm|incremental recompile after a 1-line edit|3|1|none||:apps:jvm:classes"
  "static-analysis|ktlint + detekt from scratch|2|2|outputs||ktlintCheck detekt --continue -Pchipbox.skipNative"
  "unit-test|JVM unit tests from scratch|3|2|outputs||jvmTest --continue"
  "android-debug|Android debug APK from scratch|3|2|outputs|android|:apps:android:assembleDebug -Pchipbox.skipNative"
  "screenshot|Paparazzi screenshots from scratch|2|3|outputs|android|verifyPaparazziDebug --continue"
  "android-release|Android release APK from scratch|4|3|outputs|android,native|:apps:android:assembleRelease"
)

# Preferred probe file for the incremental scenario — a low-level shared module with a wide fan-out.
# Falls back to the first commonMain source under cbox/common if it's ever renamed.
PROBE_PREF="cbox/common/entities/api/src/commonMain/kotlin/net/sigmabeta/chipbox/entities/ArtistEntity.kt"

SCENARIO_NAMES=()
for _e in "${SCENARIOS[@]}"; do SCENARIO_NAMES+=("${_e%%|*}"); done

# ---- colors ----------------------------------------------------------------
if [ -t 1 ]; then R=$'\e[31m'; G=$'\e[32m'; Y=$'\e[33m'; B=$'\e[1m'; Z=$'\e[0m'; else R= G= Y= B= Z=; fi

# ---- arg parsing -----------------------------------------------------------
tier_max=2; only=(); skip=(); repeat=1; no_daemon=0; max_workers=""; with_native=0
baseline=""; dry_run=0

die() { echo "${R:-}error:${Z:-} $*" >&2; exit 2; }

add_names() {  # split a comma list into the named array
  local list="$1" i; IFS=',' read -r -a parts <<<"$list"
  for i in "${parts[@]}"; do [ -n "$i" ] && eval "$2+=(\"\$i\")"; done
}

while [ $# -gt 0 ]; do
  arg="$1"
  case "$arg" in
    -h|--help) awk 'NR>1 && /^#/ {sub(/^# ?/, ""); print; next} NR>1 {exit}' "${BASH_SOURCE[0]}"; exit 0 ;;
    --list) for e in "${SCENARIOS[@]}"; do
              IFS='|' read -r n l w t p c a <<<"$e"; printf '  %-16s tier %s  %s\n' "$n" "$t" "$l"; done; exit 0 ;;
    --quick) tier_max=1 ;;
    --full) tier_max=3 ;;
    --no-daemon) no_daemon=1 ;;
    --with-native) with_native=1 ;;
    --dry-run) dry_run=1 ;;
    --only) [ $# -ge 2 ] || die "--only needs a value"; shift; add_names "$1" only ;;
    --only=*) add_names "${arg#--only=}" only ;;
    --skip) [ $# -ge 2 ] || die "--skip needs a value"; shift; add_names "$1" skip ;;
    --skip=*) add_names "${arg#--skip=}" skip ;;
    --repeat) [ $# -ge 2 ] || die "--repeat needs a value"; shift; repeat="$1" ;;
    --repeat=*) repeat="${arg#--repeat=}" ;;
    --max-workers) [ $# -ge 2 ] || die "--max-workers needs a value"; shift; max_workers="$1" ;;
    --max-workers=*) max_workers="${arg#--max-workers=}" ;;
    --baseline) [ $# -ge 2 ] || die "--baseline needs a path"; shift; baseline="$1" ;;
    --baseline=*) baseline="${arg#--baseline=}" ;;
    -*) die "unknown option: $arg (see --help)" ;;
    *) die "unexpected argument: $arg (see --help)" ;;
  esac
  shift
done

[[ "$repeat" =~ ^[0-9]+$ ]] && [ "$repeat" -ge 1 ] || die "--repeat needs a positive integer"
[ -z "$max_workers" ] || [[ "$max_workers" =~ ^[0-9]+$ ]] || die "--max-workers needs an integer"

valid=" ${SCENARIO_NAMES[*]} "
for n in ${only[@]+"${only[@]}"} ${skip[@]+"${skip[@]}"}; do
  [[ "$valid" == *" $n "* ]] || die "unknown scenario: $n (see --list)"
done

# ---- preflight -------------------------------------------------------------
[ -x "$GRADLE" ] || die "./gradlew not found or not executable — run this from the Chipbox root"
grep -q 'rootProject.name = "Chipbox"' settings.gradle.kts 2>/dev/null \
  || die "this doesn't look like the Chipbox root (settings.gradle.kts has no Chipbox rootProject.name)"
[ -f sage/settings.gradle.kts ] \
  || die "the sage/ submodule looks uninitialized — run: git submodule update --init --recursive"

case "$(realpath -m "$OUT" 2>/dev/null || echo "$OUT")" in
  / | "$ROOT" | "$HOME") die "refusing to use '$OUT' as BENCH_OUT (it gets rm -rf'd)" ;;
esac

sdk_dir="$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null)"
have_android=0
if [ -n "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}$sdk_dir" ]; then have_android=1; fi

ndk_root=""
for cand in "${ANDROID_HOME:-}/ndk" "${ANDROID_SDK_ROOT:-}/ndk" "$sdk_dir/ndk" "$HOME/Android/Sdk/ndk"; do
  [ -d "$cand" ] || continue
  if [ -n "$(ls -1 "$cand" 2>/dev/null | head -1)" ]; then ndk_root="$cand/$(ls -1 "$cand" | head -1)"; break; fi
done

# ---- helpers ---------------------------------------------------------------
now_ns() { date +%s%N; }
elapsed() { awk -v a="$1" -v b="$2" 'BEGIN{printf "%.3f", (b-a)/1e9}'; }

# The JDK gradlew will actually use: JAVA_HOME wins, else PATH.
if [ -n "${JAVA_HOME:-}" ]; then jdk_bin="$JAVA_HOME/bin/java"; jdk_src="JAVA_HOME"; else jdk_bin="$(command -v java)"; jdk_src="PATH"; fi
jdk_line="$({ "$jdk_bin" -version 2>&1 || echo 'java not found'; } | head -1)"

os_name() {
  if [ -r /etc/os-release ]; then
    # shellcheck disable=SC1091
    ( . /etc/os-release 2>/dev/null; printf '%s' "${PRETTY_NAME:-${NAME:-unknown}}" )
  else uname -s; fi
  if grep -qs Microsoft /proc/version 2>/dev/null; then printf ' (WSL)'; fi
}

# Physical block device backing the repo (resolve the partition/LUKS/LVM chain to the real disk).
phys_disk() {
  local dev; dev="$(readlink -f "$1" 2>/dev/null || echo "$1")"
  local parent; parent="$(lsblk -no PKNAME "$dev" 2>/dev/null | head -1)"
  [ -n "$parent" ] || parent="$(basename "$dev")"
  printf '%s' "$parent"
}

fingerprint() {
  local threads cpu_model mhz disk_dev fs_type rot parent
  threads="$(nproc 2>/dev/null || echo '?')"
  cpu_model="$(awk -F: '/^model name/ {sub(/^ /,"",$2); print $2; exit}' /proc/cpuinfo 2>/dev/null)"
  [ -n "$cpu_model" ] || cpu_model="$(uname -m)"
  mhz="$(awk -F: '/^cpu MHz/ {printf "%d", $2; exit}' /proc/cpuinfo 2>/dev/null)"

  # getconf on Linux gives the whole-repo device; resolve it to the physical disk for the SSD/HDD bit.
  disk_dev="$(df --output=source . 2>/dev/null | tail -1)"
  fs_type="$(df --output=fstype . 2>/dev/null | tail -1)"
  parent="$(phys_disk "$disk_dev")"
  rot="?"
  [ -r "/sys/block/$parent/queue/rotational" ] && rot="$(cat "/sys/block/$parent/queue/rotational")"

  {
    echo "hostname=$(hostname 2>/dev/null || echo '?')"
    echo "os=$(os_name)"
    echo "kernel=$(uname -sr)"
    echo "arch=$(uname -m)"
    echo "cpu_model=$cpu_model"
    echo "cpu_cores_per_socket=$(awk -F: '/^cpu cores/ {gsub(/ /,"",$2); print $2; exit}' /proc/cpuinfo 2>/dev/null || echo '?')"
    echo "cpu_sockets=$(awk -F: '/^physical id/ {print $2}' /proc/cpuinfo 2>/dev/null | sort -u | wc -l)"
    echo "cpu_threads=$threads"
    echo "cpu_mhz_at_start=${mhz:-?}"
    echo "ram_total_mib=$(awk '/^MemTotal/ {printf "%d", $2/1024}' /proc/meminfo 2>/dev/null)"
    echo "ram_available_mib=$(awk '/^MemAvailable/ {printf "%d", $2/1024}' /proc/meminfo 2>/dev/null)"
    echo "swap_total_mib=$(awk '/^SwapTotal/ {printf "%d", $2/1024}' /proc/meminfo 2>/dev/null)"
    echo "repo_mount=$(df --output=source . 2>/dev/null | tail -1)"
    echo "repo_physical_disk=$parent"
    echo "repo_disk_rotational=$rot"
    echo "repo_fs=$fs_type"
    echo "repo_free_gib=$(df -BG . 2>/dev/null | awk 'NR==2 {gsub(/G/,"",$4); print $4}')"
    echo "loadavg_start=$(cut -d' ' -f1-3 /proc/loadavg 2>/dev/null)"
    echo "jdk_source=$jdk_src"
    echo "jdk_java=$jdk_bin"
    echo "jdk_version=$jdk_line"
    echo "gradle_version=$(sed -n 's/^distributionUrl=.*gradle-\(.*\)-all\.zip/\1/p' gradle/wrapper/gradle-wrapper.properties 2>/dev/null)"
    echo "gradle_jvmargs=$(sed -n 's/^org\.gradle\.jvmargs=//p' gradle.properties 2>/dev/null)"
    echo "gradle_workers=$([ -n "$max_workers" ] && echo "$max_workers (--max-workers)" || echo "$threads (Gradle default = all cores)")"
    echo "daemon_mode=$([ "$no_daemon" = 1 ] && echo no-daemon || echo daemon)"
    echo "build_cache=disabled (--no-build-cache: no local or remote cache reads)"
    echo "android_sdk=$([ "$have_android" = 1 ] && echo "present${sdk_dir:+ ($sdk_dir)}" || echo MISSING)"
    echo "ndk=${ndk_root:-not found}"
    echo "benchmark_started=$(date -Iseconds)"
  } >"$OUT/machine.kv"
  sed 's/^/  /' "$OUT/machine.kv" >"$OUT/machine.txt"
}

# ---- purge -----------------------------------------------------------------
# rm every module build/ tree outside sage/ — never the repo-root build/, which holds our results —
# plus the native build dirs, so a cold scenario really starts from nothing.
purge_outputs() {
  while IFS= read -r d; do rm -rf "$d"; done < <(
    find . -path ./sage -prune -o -type d -name build -print 2>/dev/null | grep -vx './build'
  )
  rm -rf apps/jvm/native-build apps/android/.externalNativeBuild
  find apps -type d -name .cxx -prune -exec rm -rf {} + 2>/dev/null
}

purge_for() {
  case "$1" in
    outputs) purge_outputs ;;
    config) rm -rf .gradle/configuration-cache ;;
    none) ;;
  esac
}

# ---- probe file (incremental scenario) -------------------------------------
PROBE_FILE=""; PROBE_BAK=""
restore_probe() {
  if [ -n "$PROBE_FILE" ] && [ -n "$PROBE_BAK" ] && [ -f "$PROBE_BAK" ]; then
    cp -p "$PROBE_BAK" "$PROBE_FILE"
    rm -f "$PROBE_BAK"
  fi
  PROBE_FILE=""; PROBE_BAK=""
}
trap 'restore_probe; printf "\ninterrupted\n" >&2; exit 130' INT TERM

pick_probe() {
  if [ -f "$PROBE_PREF" ]; then printf '%s' "$PROBE_PREF"
  else find cbox/common -path '*/src/commonMain/kotlin/*' -name '*.kt' 2>/dev/null | sort | head -1
  fi
}

# ---- results + scenario runner ---------------------------------------------
RESULT_ROWS=()   # name|label|weight|sample|seconds|status|gradle_s|tasks|note
SELECTED=()

gradle_flags() {   # $1 = purge mode
  local purge="$1"
  local -a f=(--console=plain --no-build-cache)
  [ "$purge" != none ] && f+=(--rerun-tasks)
  [ "$no_daemon" = 1 ] && f+=(--no-daemon)
  [ -n "$max_workers" ] && f+=(--max-workers="$max_workers")
  printf '%s\n' "${f[@]}"
}

# "BUILD SUCCESSFUL in 3m 12s" -> 192. Parsed in bash so it works on any awk. Sub-second builds are
# reported as "803ms" — that is the common case for a warm configuration-cache run.
gradle_reported_seconds() {
  local log="$1" line t
  line="$(grep -a 'BUILD SUCCESSFUL in \|BUILD FAILED in ' "$log" 2>/dev/null | tail -1)"
  t="${line#*BUILD SUCCESSFUL in }"; t="${t#*BUILD FAILED in }"
  if [[ "$t" =~ ^([0-9]+)m\ ([0-9.]+)s ]]; then
    printf '%.1f' "$(awk -v m="${BASH_REMATCH[1]}" -v s="${BASH_REMATCH[2]}" 'BEGIN{print m*60+s}')"
  elif [[ "$t" =~ ^([0-9.]+)s ]]; then printf '%s' "${BASH_REMATCH[1]}"
  elif [[ "$t" =~ ^([0-9.]+)ms ]]; then printf '%.1f' "$(awk -v ms="${BASH_REMATCH[1]}" 'BEGIN{print ms/1000}')"
  fi
}

run_one() {
  local entry="$1" sample="$2"
  local name label weight tier purge caps args
  IFS='|' read -r name label weight tier purge caps args <<<"$entry"

  local note=""
  if [[ "$caps" == *native* ]]; then
    if [ "$with_native" = 1 ]; then note="native included"
    else args="$args -Pchipbox.skipNative"; note="native skipped (use --with-native)"; fi
  fi

  local log="$LOGS/$name-$sample.log"
  local -a flags; mapfile -t flags < <(gradle_flags "$purge")

  # Incremental scenario: prime a warm graph, then make a real one-line change — Gradle hashes file
  # content, so a bare `touch` would NOT invalidate the up-to-date check — time the recompile, then
  # restore the file byte-for-byte.
  if [ "$name" = incremental-jvm ]; then
    # Always prime, untimed: this brings the graph up to date AND populates this scenario's own
    # configuration-cache entry (its task set differs from the -Pchipbox.skipNative dry-run ones), so
    # the timed sample is the delta compile alone and not one-off configuration-cache storage. On a
    # cold clone the prime is a full build — that cost is deliberately not scored.
    echo "    priming (untimed): bringing the graph + config cache up to date…"
    # shellcheck disable=SC2086
    "$GRADLE" $args "${flags[@]}" >"$LOGS/$name-$sample.prime.log" 2>&1 \
      || { echo "    ${R}prime build failed${Z} — see $LOGS/$name-$sample.prime.log"
           RESULT_ROWS+=("$name|$label|$weight|$sample|0|PRIME-FAIL|||prime build failed"); return; }
    PROBE_FILE="$(pick_probe)"
    if [ -z "$PROBE_FILE" ] || [ ! -f "$PROBE_FILE" ]; then
      RESULT_ROWS+=("$name|$label|$weight|$sample|0|SKIPPED|||no probe source file found"); return
    fi
    PROBE_BAK="$(mktemp)"
    cp -p "$PROBE_FILE" "$PROBE_BAK"
    # Unique content per sample: Gradle hashes file content, and re-appending the same bytes would
    # leave the compile task UP-TO-DATE, so every sample but the first would measure nothing.
    printf '\n// chipbox build-benchmark probe %s-%s\n' "$name" "$sample" >>"$PROBE_FILE"
    note="${note:+$note; }probe=$PROBE_FILE"
    INCREMENTAL_RAN=1
  fi

  echo "    flags: ${flags[*]}"
  local start end rc dur
  start="$(now_ns)"
  # shellcheck disable=SC2086
  "$GRADLE" $args "${flags[@]}" >"$log" 2>&1
  rc=$?
  end="$(now_ns)"
  dur="$(elapsed "$start" "$end")"
  [ -n "$PROBE_FILE" ] && restore_probe

  local gradle_s tasks status extra=""
  gradle_s="$(gradle_reported_seconds "$log")"
  tasks="$(grep -a -oE '[0-9]+ actionable tasks?' "$log" 2>/dev/null | tail -1 | grep -oE '^[0-9]+')"
  grep -qa 'Reusing configuration cache\|Configuration cache entry reused' "$log" 2>/dev/null && extra="cc-reused"
  [ -n "$tasks" ] && extra="${extra:+$extra; }tasks=$tasks"
  [ -n "$note" ] && extra="${extra:+$extra; }$note"

  status="PASS"; [ "$rc" -ne 0 ] && status="FAIL"
  RESULT_ROWS+=("$name|$label|$weight|$sample|$dur|$status|$gradle_s|$tasks|$extra")

  if [ "$status" = PASS ]; then printf '    %sPASS%s %ss (gradle %ss)\n' "$G" "$Z" "$dur" "${gradle_s:-?}"
  else printf '    %sFAIL%s %ss — %s\n' "$R" "$Z" "$dur" "$log"; fi
}

# ---- selection -------------------------------------------------------------
for entry in "${SCENARIOS[@]}"; do
  IFS='|' read -r name label weight tier purge caps args <<<"$entry"
  if [ "${#only[@]}" -gt 0 ]; then
    [[ " ${only[*]} " == *" $name "* ]] || continue
  elif [ "$tier" -gt "$tier_max" ]; then
    continue
  fi
  [[ " ${skip[*]-} " == *" $name "* ]] && continue
  if [[ "$caps" == *android* ]] && [ "$have_android" = 0 ]; then
    echo "${Y:-}skipping $name${Z:-}: no Android SDK (ANDROID_HOME / ANDROID_SDK_ROOT / local.properties sdk.dir)"
    RESULT_ROWS+=("$name|$label|$weight|0|0|SKIPPED|||no Android SDK")
    continue
  fi
  SELECTED+=("$entry")
done

[ "${#SELECTED[@]}" -gt 0 ] || die "no scenarios selected (see --list)"

# ---- dry run ---------------------------------------------------------------
if [ "$dry_run" = 1 ]; then
  echo "${B}Chipbox build benchmark — dry run${Z} (nothing executed)"
  echo
  for entry in "${SELECTED[@]}"; do
    IFS='|' read -r name label weight tier purge caps args <<<"$entry"
    [[ "$caps" == *native* ]] && [ "$with_native" = 0 ] && args="$args -Pchipbox.skipNative"
    mapfile -t flags < <(gradle_flags "$purge")
    printf '  %-16s purge=%-7s %s %s %s\n' "$name" "$purge" "$GRADLE" "$args" "${flags[*]}"
  done
  echo
  echo "  ${#SELECTED[@]} scenario(s) × --repeat $repeat = $(( ${#SELECTED[@]} * repeat )) Gradle build(s)"
  [ "$with_native" = 1 ] && echo "  (--with-native: android-release runs WITHOUT -Pchipbox.skipNative)"
  exit 0
fi

# ---- go --------------------------------------------------------------------
rm -rf "$OUT"; mkdir -p "$OUT/logs"
LOGS="$OUT/logs"

echo "${B}Chipbox build benchmark${Z} — ${#SELECTED[@]} scenario(s) × $repeat sample(s)"
if [ -n "$(git status --porcelain --ignore-submodules=dirty 2>/dev/null)" ]; then
  echo "${Y}warning:${Z} working tree is dirty — the incremental probe is restored byte-for-byte, but"
  echo "         unrelated local edits can shift compile times."
fi
awk '{ if ($1+0 > 1.0) printf "warning: load average is %s — a busy machine skews these numbers\n", $1 }' /proc/loadavg >&2
echo

fingerprint
echo "${B}Machine${Z}"
cat "$OUT/machine.txt"
echo

for entry in "${SELECTED[@]}"; do
  IFS='|' read -r name label weight tier purge caps args <<<"$entry"
  printf '%s──▶ %-16s%s %s\n' "$B" "$name" "$Z" "$label"
  for s in $(seq 1 "$repeat"); do
    [ "$repeat" -eq 1 ] || printf '  %ssample %d/%d%s\n' "$B" "$s" "$repeat" "$Z"
    p_start="$(now_ns)"; purge_for "$purge"; p_end="$(now_ns)"
    p_dur="$(elapsed "$p_start" "$p_end")"
    if awk -v d="$p_dur" 'BEGIN{exit !(d>0.5)}'; then printf '    purge(%s) %.1fs\n' "$purge" "$p_dur"; fi
    run_one "$entry" "$s"
    # fold the purge cost into the last row's note so the summary is honest about it
    if awk -v d="$p_dur" 'BEGIN{exit !(d>0.5)}' && [ "${#RESULT_ROWS[@]}" -gt 0 ]; then
      last=$(( ${#RESULT_ROWS[@]} - 1 ))
      RESULT_ROWS[$last]="${RESULT_ROWS[$last]}; purge=${p_dur}s"
    fi
  done
  echo
done

# The incremental probe restored the source, but Gradle's execution history now points at a file state
# that no longer exists, so the next real build would recompile that chain once for nothing. Put the
# graph back on the current sources here — untimed, and not scored.
if [ "${INCREMENTAL_RAN:-0}" = 1 ]; then
  echo "restoring incremental state after the probe (untimed)…"
  if "$GRADLE" :apps:jvm:classes --console=plain --no-build-cache >"$LOGS/post-run-restore.log" 2>&1; then
    echo "  graph is up to date again"
  else
    echo "  ${Y}restore build failed${Z} — see $LOGS/post-run-restore.log (its cost is not in the numbers)"
  fi
  echo
fi

overall=0
for r in "${RESULT_ROWS[@]}"; do
  st="$(cut -d'|' -f6 <<<"$r")"
  case "$st" in FAIL|PRIME-FAIL) overall=1 ;; esac
done

# ---- report ----------------------------------------------------------------
for r in "${RESULT_ROWS[@]}"; do printf '%s\n' "$r"; done >"$OUT/results.tsv"

python3 - "$OUT" "$repeat" "$baseline" <<'PY'
import json, math, os, subprocess, sys
from datetime import datetime, timezone

out, repeat, baseline_path = sys.argv[1], int(sys.argv[2]), sys.argv[3]

machine = {}
with open(os.path.join(out, "machine.kv")) as fh:
    for line in fh:
        if "=" in line:
            k, v = line.rstrip("\n").split("=", 1)
            machine[k] = v

rows = []
with open(os.path.join(out, "results.tsv")) as fh:
    for line in fh:
        line = line.rstrip("\n")
        if not line:
            continue
        f = line.split("|") + [""] * 9
        rows.append(dict(name=f[0], label=f[1], weight=int(f[2] or 1), sample=f[3],
                         seconds=float(f[4] or 0), status=f[5], gradle_s=f[6], tasks=f[7], note=f[8]))

order, scen = [], {}
for r in rows:
    s = scen.get(r["name"])
    if s is None:
        s = scen[r["name"]] = dict(name=r["name"], label=r["label"], weight=r["weight"],
                                   status=r["status"], samples=[], notes=r["note"])
        order.append(r["name"])
    if r["seconds"] > 0:
        s["samples"].append(r["seconds"])
    if r["status"] in ("FAIL", "PRIME-FAIL"):
        s["status"] = "FAIL"
    elif s["status"] == "PASS" and r["status"] == "SKIPPED":
        s["status"] = "SKIPPED"
    if r["gradle_s"]:
        s["gradle_reported_s"] = round(float(r["gradle_s"]), 1)
    if r["tasks"]:
        s["tasks"] = int(r["tasks"])
    if r["note"] and not s["notes"]:   # notes are per-scenario, and identical across samples
        s["notes"] = r["note"]

for s in scen.values():
    xs = sorted(s["samples"])
    s["samples"] = [round(x, 2) for x in s["samples"]]
    if xs:
        n = len(xs)
        s.update(min=round(xs[0], 2), max=round(xs[-1], 2), mean=round(sum(xs) / n, 2),
                 median=round(xs[n // 2] if n % 2 else (xs[n // 2 - 1] + xs[n // 2]) / 2, 2))
    else:
        s.pop("samples", None)
        s["samples"] = []

passed = [s for s in scen.values() if s["samples"] and s["status"] == "PASS"]
wsum = sum(s["weight"] for s in passed)
wmean = sum(s["weight"] * s["min"] for s in passed) / wsum if wsum else None
index = round(100000.0 / wmean, 1) if wmean else None

def has_min(d):
    return bool(d.get("samples")) and (d.get("min") or 0) > 0

doc = dict(
    generated=datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds"),
    chipbox_commit=subprocess.run(["git", "rev-parse", "--short", "HEAD"],
                                  capture_output=True, text=True).stdout.strip(),
    machine=machine,
    config=dict(repeat=repeat, scenarios=order,
                index_formula="100000 / weighted_mean(min seconds over PASS scenarios); higher = faster"),
    results=[scen[n] for n in order],
    score=dict(index=index, weighted_mean_min_s=round(wmean, 2) if wmean else None,
               scenarios_used=len(passed)),
)

comparison = None
if baseline_path:
    if not os.path.isfile(baseline_path):
        print(f"warning: baseline {baseline_path} not found — skipping comparison", file=sys.stderr)
    else:
        base = json.load(open(baseline_path))
        bmap = {r["name"]: r for r in base.get("results", [])}
        per = [dict(name=n, baseline_min=bmap[n]["min"], current_min=scen[n]["min"],
                    speedup=round(bmap[n]["min"] / scen[n]["min"], 3))
               for n in order if n in bmap and has_min(bmap[n]) and has_min(scen[n])]
        gmean = round(math.exp(sum(math.log(p["speedup"]) for p in per) / len(per)), 3) if per else None
        same_set = (base.get("config") or {}).get("scenarios") == doc["config"]["scenarios"]
        comparison = dict(baseline_path=baseline_path, baseline_generated=base.get("generated"),
                          baseline_index=(base.get("score") or {}).get("index"),
                          current_index=index, same_scenario_set=same_set,
                          per_scenario=per, geometric_mean_speedup=gmean)
        doc["comparison"] = comparison

with open(os.path.join(out, "results.json"), "w") as fh:
    json.dump(doc, fh, indent=2)

L = [f"Chipbox build benchmark — {doc['generated']}",
     f"  {machine.get('cpu_model','?')}  ·  {machine.get('cpu_threads','?')} threads  ·  "
     f"{machine.get('ram_total_mib','?')} MiB RAM  ·  {machine.get('repo_fs','?')} "
     f"(disk {machine.get('repo_physical_disk','?')}, rotational={machine.get('repo_disk_rotational','?')})",
     f"  {machine.get('jdk_version','?')}  (via {machine.get('jdk_source','?')})  ·  "
     f"Gradle {machine.get('gradle_version','?')}  ·  {machine.get('daemon_mode','?')}", ""]
L.append(f"  {'SCENARIO':<16} {'RESULT':<8} {'MIN':>8} {'MEAN':>8} {'GRADLE':>7} {'TASKS':>6}  NOTES")
L.append(f"  {'-'*16} {'-'*8} {'-'*8} {'-'*8} {'-'*7} {'-'*6}  {'-'*30}")
for n in order:
    s = scen[n]
    if not has_min(s):
        L.append(f"  {n:<16} {s['status']:<8} {'—':>8} {'—':>8} {'—':>7} {'—':>6}  {s.get('notes','')}")
        continue
    mean = f"{s['mean']:.1f}s" if len(s.get('samples') or []) > 1 else "—"
    gradle_t = f"{s['gradle_reported_s']:.1f}s" if s.get('gradle_reported_s') else "—"
    L.append(f"  {n:<16} {s['status']:<8} {s['min']:>7.1f}s {mean:>8} "
             f"{gradle_t:>7} {str(s.get('tasks','—')):>6}  {s.get('notes','')}")
L.append("")
if index is not None:
    L.append(f"BUILD INDEX: {index}   (100000 / {wmean:.1f}s weighted-mean minimum; higher is faster)")
    L.append("  Only compare indices whose scenario set, weights and --repeat match.")
else:
    L.append("BUILD INDEX: n/a (no scenario produced a time)")
if comparison:
    L += ["", f"vs baseline {comparison['baseline_path']} (run {comparison.get('baseline_generated','?')})"]
    for p in comparison["per_scenario"]:
        L.append(f"  {p['name']:<16} {p['baseline_min']:>7.1f}s -> {p['current_min']:>7.1f}s   {p['speedup']:>5.2f}x")
    L.append(f"  geometric-mean speedup: {comparison['geometric_mean_speedup']}x"
             + (f"  (index {comparison['baseline_index']} -> {index})"
                if comparison["same_scenario_set"] else ""))
    if not comparison["same_scenario_set"]:
        L.append("  note: the scenario sets differ — the per-scenario ratios still hold, the two indices don't")
    elif comparison["geometric_mean_speedup"] and 0.9 <= comparison["geometric_mean_speedup"] <= 1.1:
        L.append("  within run-to-run noise (~5-10% on one machine) — re-run with more --repeat before concluding")
L += ["", f"artifacts: {out}/  (machine.txt, results.json, results.tsv, summary.txt, logs/)"]
summary = "\n".join(L)
open(os.path.join(out, "summary.txt"), "w").write(summary + "\n")
print(summary)
PY
py_rc=$?
[ "$py_rc" -ne 0 ] && echo "${R}report generation failed (rc=$py_rc)${Z} — raw data is in $OUT/results.tsv" >&2

# ---- post-run integrity check ---------------------------------------------
if [ -f "$PROBE_PREF" ] && [ -n "$(git status --porcelain -- "$PROBE_PREF" 2>/dev/null)" ]; then
  echo "${R}warning:${Z} the incremental probe file did not restore cleanly — check: git diff $PROBE_PREF" >&2
  overall=1
fi

echo
if [ "$overall" -eq 0 ]; then echo "${G}${B}✔ all scenarios passed${Z} — see $OUT/summary.txt"
else echo "${R}${B}x some scenarios failed${Z} — see $OUT/logs/ and $OUT/results.tsv"; fi
exit "$overall"