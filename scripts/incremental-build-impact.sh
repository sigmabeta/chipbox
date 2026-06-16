#!/usr/bin/env bash
#
# incremental-build-impact.sh — measure each module's incremental-build "blast radius".
#
# For a chosen app, this walks every module that the app actually compiles, and for each
# one it makes a single ABI-breaking source change (adds a public top-level function),
# runs an incremental build, and counts how many Kotlin/Java *compile* tasks that one
# change forced to re-run. The change is then reverted and the tree returned to a steady
# state before the next module. At the end it prints a table sorted by blast radius.
#
# A module near the top of that table is an architectural hot spot: editing it recompiles
# a large fraction of the app, so it's a prime candidate for splitting (api/impl), pushing
# an `api` dependency down to `implementation`, or otherwise narrowing its public surface.
#
# It edits tracked source files in place but always restores them; it never commits.
# Ctrl-C is trapped so an interrupted run still restores the file it was probing.
#
# Usage:
#   scripts/incremental-build-impact.sh [options]
#
# Options:
#   --app <gradle-path>    App project to analyse.       (default: :apps:android)
#   --task <gradle-task>   Build task to run each round.  (default: assembleDebug for
#                          Android apps, assemble otherwise)
#   --filter <regex>       Only probe modules whose path matches this egrep regex.
#   --limit <N>            Probe at most N modules (after filtering).
#   --out <file>           CSV output path.                (default: build/incremental-build-impact.csv)
#   --churn-days <N>       Window for the git-churn column.            (default: 90)
#   --list                 Print the module universe for the app and exit (no builds).
#   -h | --help            Show this help.
#
# Examples:
#   scripts/incremental-build-impact.sh
#   scripts/incremental-build-impact.sh --app :apps:jvm
#   scripts/incremental-build-impact.sh --filter '^:cbox:common:player' --limit 10
#   scripts/incremental-build-impact.sh --list
#
set -uo pipefail

# --- repo root -------------------------------------------------------------------------
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
    echo "error: not inside a git repository" >&2; exit 1
}
cd "$REPO_ROOT"

GRADLEW="./gradlew"
[[ -x "$GRADLEW" ]] || { echo "error: $GRADLEW not found/executable in $REPO_ROOT" >&2; exit 1; }

# --- defaults / arg parsing ------------------------------------------------------------
APP=":apps:android"
TASK=""
FILTER=""
LIMIT=0
OUT="build/incremental-build-impact.csv"
CHURN_DAYS=90
LIST_ONLY=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        --app)        APP="$2"; shift 2 ;;
        --task)       TASK="$2"; shift 2 ;;
        --filter)     FILTER="$2"; shift 2 ;;
        --limit)      LIMIT="$2"; shift 2 ;;
        --out)        OUT="$2"; shift 2 ;;
        --churn-days) CHURN_DAYS="$2"; shift 2 ;;
        --list)       LIST_ONLY=1; shift ;;
        -h|--help)
            # Print the leading comment block (after the shebang), stopping at the first
            # non-comment line — robust to the header growing or shrinking.
            awk 'NR==1{next} /^#/{sub(/^# ?/,""); print; next} {exit}' "$0"; exit 0 ;;
        *) echo "error: unknown option '$1' (try --help)" >&2; exit 1 ;;
    esac
done

# Default build task is app-aware: Android apps have assembleDebug; other targets
# (jvm/cli/server/...) use the universal `assemble` lifecycle task. Both compile the
# whole dependency graph, which is what the blast-radius walk needs. Override with --task.
if [[ -z "$TASK" ]]; then
    case "$APP" in
        *android*) TASK="${APP}:assembleDebug" ;;
        *)         TASK="${APP}:assemble" ;;
    esac
fi

# Build flags shared by every gradle invocation in this script.
#   --console=plain  : stable, parseable "> Task ..." lines.
#   --no-build-cache : critical — otherwise recompiles get served FROM-CACHE and the
#                      blast-radius count reads as ~0. We want real compile execution.
# (Kotlin compile-avoidance / incremental compilation still apply; those are local ABI
#  snapshots, not the build cache, and are exactly what we're measuring.)
GRADLE_FLAGS=(--console=plain --no-build-cache)

# --- helpers ---------------------------------------------------------------------------

# Summarise executed work in a captured build log.
#   A "> Task :path:name" line with no trailing UP-TO-DATE/FROM-CACHE/NO-SOURCE/SKIPPED
#   ran for real. Of those, we care about compile tasks (name begins with "compile").
#   A single module can emit several compile tasks (e.g. Android emits both
#   compileKotlinJvm and compileAndroidMain), so the *module* count is the app-agnostic
#   blast-radius metric and the *task* count is kept for context.
#   Prints "<distinctModules> <compileTasks> <totalTasks>".
count_executed() {
    local log="$1"
    awk '
        /^> Task :/ {
            # Line shape: "> Task :a:b:c:taskName [STATUS]"  -> $1=">" $2="Task" $3=path $4=status
            status = $4                      # "", UP-TO-DATE, FROM-CACHE, NO-SOURCE, SKIPPED
            if (status == "UP-TO-DATE" || status == "FROM-CACHE" ||
                status == "NO-SOURCE"  || status == "SKIPPED") next
            total++
            path = $3                        # :a:b:c:taskName
            n = split(path, seg, ":")
            if (seg[n] ~ /^compile/) {
                compiles++
                modpath = path               # strip the trailing :taskName -> module path
                sub(/:[^:]+$/, "", modpath)
                mods[modpath] = 1
            }
        }
        END { nm = 0; for (m in mods) nm++; printf "%d %d %d", nm, compiles+0, total+0 }
    ' "$log"
}

# Commits touching a module directory within the churn window. Run from inside the dir so
# git resolves the right repo automatically — important because sage/* are a submodule and
# their history lives in the submodule, not the parent repo. Prints an integer.
module_churn() {
    local dir="$1"
    [[ -d "$dir" ]] || { echo 0; return; }
    git -C "$dir" log --oneline --since="${CHURN_DAYS} days ago" -- . 2>/dev/null | wc -l | tr -d ' '
}

# Run the build task, streaming a one-line spinner-free heartbeat, capturing full log.
# Sets globals: RUN_RC (exit code), RUN_LOG (path), RUN_SECS.
run_build() {
    local log="$1"
    local start end
    start=$(date +%s)
    $GRADLEW "$TASK" "${GRADLE_FLAGS[@]}" >"$log" 2>&1
    RUN_RC=$?
    end=$(date +%s)
    RUN_LOG="$log"
    RUN_SECS=$((end - start))
}

# --- discover the module universe via --dry-run ---------------------------------------
# A dry-run lists every task that *would* run for the app's build, without executing it.
# Any project that owns a `compile*` task is a module the app compiles — that's precisely
# the set we want to walk, automatically scoped to this app's dependency graph.
echo ">> Discovering modules the app compiles ($TASK --dry-run) ..."
DRY_LOG="$(mktemp)"
$GRADLEW "$TASK" --dry-run "${GRADLE_FLAGS[@]}" >"$DRY_LOG" 2>&1 || {
    echo "error: dry-run failed; see $DRY_LOG" >&2
    tail -30 "$DRY_LOG" >&2
    exit 1
}

# Extract distinct project paths that have a compile task.
mapfile -t MODULES < <(
    grep -E '^:.+:compile[A-Za-z]+' "$DRY_LOG" \
        | awk '{print $1}' \
        | sed -E 's/:[^:]+$//' \
        | sort -u
)
rm -f "$DRY_LOG"

if [[ ${#MODULES[@]} -eq 0 ]]; then
    echo "error: found no compiling modules for $TASK — is the task name correct?" >&2
    exit 1
fi

# --- map project path -> directory, honoring settings.gradle.kts overrides -------------
declare -A DIR_OVERRIDE
while IFS=$'\t' read -r path dir; do
    DIR_OVERRIDE["$path"]="$dir"
done < <(
    grep -E 'project\("[^"]+"\)\.projectDir *= *file\("[^"]+"\)' settings.gradle.kts \
        | sed -E 's/.*project\("([^"]+)"\)\.projectDir *= *file\("([^"]+)"\).*/\1\t\2/'
)

module_dir() {
    local path="$1"
    if [[ -n "${DIR_OVERRIDE[$path]:-}" ]]; then
        echo "${DIR_OVERRIDE[$path]}"
    else
        echo "${path#:}" | tr ':' '/'
    fi
}

# Platform-specific source root to probe as a last resort, matched to the target app:
# androidMain feeds an Android build, jvmMain feeds a JVM build. Using the wrong one would
# probe a source set the app never compiles and falsely report a zero blast radius.
case "$APP" in
    *android*) PLATFORM_ROOT="src/androidMain/kotlin" ;;
    *)         PLATFORM_ROOT="src/jvmMain/kotlin" ;;
esac

# Pick a source file in a module that feeds the app build, preferring shared source sets.
# Order: commonMain (all targets) > src/main/java (jvmSharedMain) > src/main/kotlin (app) >
# the app's platform source set. Returns "" if none.
pick_source_file() {
    local dir="$1" root f
    for root in src/commonMain/kotlin src/main/java src/main/kotlin "$PLATFORM_ROOT"; do
        [[ -d "$dir/$root" ]] || continue
        f="$(find "$dir/$root" -name '*.kt' -not -path '*/build/*' 2>/dev/null | sort | head -1)"
        [[ -n "$f" ]] && { echo "$f"; return; }
    done
    echo ""
}

# --- apply optional filter / limit -----------------------------------------------------
if [[ -n "$FILTER" ]]; then
    mapfile -t MODULES < <(printf '%s\n' "${MODULES[@]}" | grep -E "$FILTER")
fi
if [[ "$LIMIT" -gt 0 && "${#MODULES[@]}" -gt "$LIMIT" ]]; then
    MODULES=("${MODULES[@]:0:$LIMIT}")
fi

echo ">> ${#MODULES[@]} module(s) selected for app $APP"

if [[ "$LIST_ONLY" -eq 1 ]]; then
    for m in "${MODULES[@]}"; do
        src="$(pick_source_file "$(module_dir "$m")")"
        if [[ -n "$src" ]]; then echo "  $m   ->  $src"
        else echo "  $m   ->  (no probe-able source; will skip)"; fi
    done
    exit 0
fi

# --- restore trap ----------------------------------------------------------------------
# If we die mid-probe, put the file we touched back.
CURRENT_SRC=""
CURRENT_BAK=""
restore_current() {
    if [[ -n "$CURRENT_SRC" && -n "$CURRENT_BAK" && -f "$CURRENT_BAK" ]]; then
        cp -f "$CURRENT_BAK" "$CURRENT_SRC"
        rm -f "$CURRENT_BAK"
        echo "   (restored $CURRENT_SRC)"
    fi
    CURRENT_SRC=""; CURRENT_BAK=""
}
trap 'echo; echo ">> interrupted — restoring"; restore_current; exit 130' INT TERM

# --- warm + baseline -------------------------------------------------------------------
mkdir -p "$(dirname "$OUT")"
LOGDIR="$(mktemp -d)"

echo ">> Warm build (reach steady state) ..."
run_build "$LOGDIR/warm.log"
if [[ "$RUN_RC" -ne 0 ]]; then
    echo "error: warm build failed; see $LOGDIR/warm.log" >&2
    tail -30 "$LOGDIR/warm.log" >&2
    exit 1
fi
echo "   warm build: ${RUN_SECS}s"

# Baseline noise: a no-op rebuild should recompile nothing. Whatever it *does* recompile
# is per-build noise (non-cacheable / always-stale tasks) and is reported for context.
echo ">> Baseline (no-op) build to measure steady-state noise ..."
run_build "$LOGDIR/baseline.log"
read -r BASE_MODULES BASE_COMPILES BASE_TOTAL <<<"$(count_executed "$LOGDIR/baseline.log")"
BASE_SECS="$RUN_SECS"     # fixed per-build overhead (config + up-to-date checks); subtracted from probe times
echo "   baseline noise: ${BASE_MODULES} module(s), ${BASE_COMPILES} compile task(s), ${BASE_SECS}s overhead"
[[ "$BASE_MODULES" -gt 0 ]] && \
    echo "   note: nonzero baseline — per-module 'net' columns subtract this."

# --- CSV header ------------------------------------------------------------------------
# recompiled_modules : distinct modules whose compile task ran (app-agnostic blast radius)
# net_*              : with the baseline-noise floor subtracted
# net_build_seconds  : build_seconds - baseline overhead = real incremental compile time
# churn_commits      : commits touching the module dir in the churn window
# pain               : net_recompiled_modules * churn_commits  (expected recompile cost)
echo "module,status,recompiled_modules,net_recompiled_modules,compile_tasks,build_seconds,net_build_seconds,churn_commits,pain,probed_file" >"$OUT"

# --- walk modules ----------------------------------------------------------------------
declare -a RESULT_LINES=()
i=0
for module in "${MODULES[@]}"; do
    i=$((i + 1))
    dir="$(module_dir "$module")"
    src="$(pick_source_file "$dir")"
    churn="$(module_churn "$dir")"

    if [[ -z "$src" ]]; then
        printf '[%d/%d] %-55s SKIP (no source)\n' "$i" "${#MODULES[@]}" "$module"
        echo "$module,skipped,,,,,,${churn},,(no probe-able source)" >>"$OUT"
        continue
    fi

    printf '[%d/%d] %-55s probing %s\n' "$i" "${#MODULES[@]}" "$module" "${src#"$dir"/}"

    # Back up and apply an ABI-breaking probe: a unique public top-level function.
    CURRENT_SRC="$src"
    CURRENT_BAK="$(mktemp)"
    cp -f "$src" "$CURRENT_BAK"
    nonce="probe_$(date +%s)_${i}"
    {
        printf '\n\n'
        printf '// __chipbox_build_probe__ (auto-added by incremental-build-impact.sh — safe to delete)\n'
        printf 'fun __chipboxBuildProbe_%s(): Int = %d\n' "$nonce" "$i"
    } >>"$src"

    # Incremental build triggered by that one change.
    run_build "$LOGDIR/probe_${i}.log"
    read -r MODULES_HIT COMPILES TOTAL <<<"$(count_executed "$LOGDIR/probe_${i}.log")"
    secs="$RUN_SECS"

    # Restore the source.
    restore_current

    if [[ "$RUN_RC" -ne 0 ]]; then
        printf '        -> BUILD FAILED (see %s)\n' "$LOGDIR/probe_${i}.log"
        echo "$module,build_failed,,,,${secs},,${churn},,${src}" >>"$OUT"
        # Re-warm so the next module starts from steady state.
        run_build "$LOGDIR/rewarm_${i}.log"
        continue
    fi

    # Axis 1: fan-out — distinct downstream modules recompiled (net of baseline noise).
    net_mods=$((MODULES_HIT - BASE_MODULES)); [[ "$net_mods" -lt 0 ]] && net_mods=0
    # Axis 2: self-cost — real compile time, with the fixed per-build overhead removed.
    net_secs=$((secs - BASE_SECS)); [[ "$net_secs" -lt 0 ]] && net_secs=0
    # Expected pain = how much recompiles × how often this module actually changes.
    pain=$((net_mods * churn))
    printf '        -> %d module(s) recompiled (net %d), %d compile task(s), %ds (net %ds), churn %d, pain %d\n' \
        "$MODULES_HIT" "$net_mods" "$COMPILES" "$secs" "$net_secs" "$churn" "$pain"
    echo "$module,ok,${MODULES_HIT},${net_mods},${COMPILES},${secs},${net_secs},${churn},${pain},${src}" >>"$OUT"
    RESULT_LINES+=("$(printf '%d\t%d\t%d\t%d\t%d\t%s' "$net_mods" "$COMPILES" "$net_secs" "$churn" "$pain" "$module")")

    # Return to steady state: reverting the file makes this module stale again, so build
    # once more so the next module's measurement isn't polluted by this revert.
    run_build "$LOGDIR/restore_${i}.log"
done

trap - INT TERM

# --- summary ---------------------------------------------------------------------------
echo
echo "==================== blast-radius summary (app $APP) ===================="
echo "Baseline: ${BASE_MODULES} module(s) / ${BASE_SECS}s overhead per no-op build (subtracted as 'net')."
echo "Columns: MODS=downstream modules recompiled (fan-out)  TASKS=raw compile tasks"
echo "         NET-S=compile seconds minus overhead (self-cost)  CHURN=commits in ${CHURN_DAYS}d"
echo "         PAIN=MODS*CHURN (expected recompile cost).  Sorted by PAIN, then fan-out."
echo
if [[ "${#RESULT_LINES[@]}" -eq 0 ]]; then
    echo "(no modules were successfully probed)"
else
    # RESULT_LINES fields: net_mods \t tasks \t net_secs \t churn \t pain \t module
    printf '%6s  %6s  %6s  %6s  %6s  %s\n' "PAIN" "MODS" "TASKS" "NET-S" "CHURN" "MODULE"
    printf '%s\n' "${RESULT_LINES[@]}" \
        | sort -t$'\t' -k5,5nr -k1,1nr \
        | awk -F'\t' '{ printf "%6d  %6d  %6d  %6d  %6d  %s\n", $5, $1, $2, $3, $4, $6 }'
fi
echo
echo "Full CSV: $OUT"
echo "Build logs: $LOGDIR (probe_<n>.log)"
