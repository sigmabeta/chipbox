#!/usr/bin/env bash
#
# verify.sh — run the same verification tasks CI runs, summarize pass/fail, and collate every
# task's artifacts (reports, JUnit XML, Paparazzi diffs, the APK, the desktop dist) into one
# folder.
#
# Mirrors the gradle commands in .circleci/config.yml. CI splits these across parallel jobs;
# locally they run sequentially with --continue-style independence: a failing task does NOT stop
# the others, so one run gives you the full picture. Differences from CI, on purpose: the Gradle
# daemon is left on (faster local reruns; CI uses --no-daemon in throwaway containers) and
# --max-workers is left at the machine default.
#
# Usage:
#   scripts/verify.sh                 # run everything
#   scripts/verify.sh static-analysis unit-test   # run only the named task(s)
#   scripts/verify.sh --skip-apps     # everything except the heavy app builds (debug APK + desktop dist)
#   scripts/verify.sh --rerun         # force every task to re-run (Gradle --rerun-tasks; ignores cache/up-to-date)
#   scripts/verify.sh --list          # list task names
#   VERIFY_OUT=/tmp/v scripts/verify.sh           # override the output folder
#
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

OUT="${VERIFY_OUT:-build/verification}"
LOGS="$OUT/logs"
GRADLE="./gradlew"
# Match CI's caching flags; plain console so logs are grep-friendly.
GRADLE_FLAGS=(--build-cache --configuration-cache --console=plain)

# name | gradle args.  Order = cheapest feedback first, then the heavy builds. Mirrors the CI jobs;
# `setup`'s `:dependencies` step is dependency resolution, not verification, so it's omitted.
# static-analysis and android-lint pass -Pchipbox.skipNative — neither needs the emulator .so libs
# (AGP's externalNativeBuild isn't cacheable, so building native in lint is pure duplicated cost).
# The apk/release-jvm tasks build native on purpose. All matches CI.
ALL_TASKS=(
  "static-analysis|ktlintCheck detekt --continue -Pchipbox.skipNative"
  "unit-test|jvmTest --continue"
  "screenshot|verifyPaparazziDebug --continue"
  "shared-build|:apps:jvm:classes"
  "android-lint|:apps:android:lintRelease -Pchipbox.skipNative"
  "release-jvm|:apps:jvm:installDist"
  "apk|:apps:android:assembleDebug"
)
# Release APK can't be built locally right now (signing), so this uses assembleDebug.
APP_BUILD_TASKS="release-jvm apk"

# ---- arg parsing -----------------------------------------------------------
filter=()
skip_apps=0
rerun=0
for arg in "$@"; do
  case "$arg" in
    -h|--help) awk 'NR>1 && /^#/ {sub(/^# ?/, ""); print; next} NR>1 {exit}' "${BASH_SOURCE[0]}"; exit 0 ;;
    --list) printf '%s\n' "${ALL_TASKS[@]%%|*}"; exit 0 ;;
    --skip-apps) skip_apps=1 ;;
    --rerun|--rerun-tasks) rerun=1 ;;
    -*) echo "unknown option: $arg" >&2; exit 2 ;;
    *) filter+=("$arg") ;;
  esac
done

# --rerun forces every task to re-execute via Gradle's --rerun-tasks (ignores up-to-date AND
# build-cache hits), so the run reflects a from-scratch build rather than cached/incremental results.
[ "$rerun" = 1 ] && GRADLE_FLAGS+=(--rerun-tasks)

selected=()
for entry in "${ALL_TASKS[@]}"; do
  name="${entry%%|*}"
  if [ "$skip_apps" = 1 ] && [[ " $APP_BUILD_TASKS " == *" $name "* ]]; then continue; fi
  if [ "${#filter[@]}" -gt 0 ] && [[ " ${filter[*]} " != *" $name "* ]]; then continue; fi
  selected+=("$entry")
done
if [ "${#selected[@]}" -eq 0 ]; then echo "no tasks selected" >&2; exit 2; fi

# ---- colors (only on a tty) ------------------------------------------------
if [ -t 1 ]; then R=$'\e[31m'; G=$'\e[32m'; B=$'\e[1m'; Z=$'\e[0m'; else R= G= B= Z=; fi

# ---- run -------------------------------------------------------------------
rm -rf "$OUT"
mkdir -p "$LOGS"
echo "${B}Running ${#selected[@]} verification task(s); logs -> $LOGS${Z}"

results=()   # "name|PASS/FAIL|seconds"
overall=0
for entry in "${selected[@]}"; do
  name="${entry%%|*}"; args="${entry#*|}"
  printf '%s──▶ %-16s%s ./gradlew %s\n' "$B" "$name" "$Z" "$args"
  start=$SECONDS
  # shellcheck disable=SC2086
  "$GRADLE" $args "${GRADLE_FLAGS[@]}" >"$LOGS/$name.log" 2>&1
  rc=$?
  dur=$((SECONDS - start))
  if [ "$rc" -eq 0 ]; then
    printf '    %sPASS%s in %ds\n' "$G" "$Z" "$dur"
    results+=("$name|PASS|$dur")
  else
    printf '    %sFAIL%s in %ds (see %s)\n' "$R" "$Z" "$dur" "$LOGS/$name.log"
    results+=("$name|FAIL|$dur")
    overall=1
  fi
done

# ---- collate artifacts -----------------------------------------------------
# Same sweeps CI uses (collect_reports / collect_test_results / collect_paparazzi), keyed by module
# path. Prune the vendored sage submodule (separate build) and our own output folder.
echo "${B}Collating artifacts -> $OUT${Z}"
prune=(-path ./sage -prune -o -path "./$OUT" -prune)
ran=" "; for r in "${results[@]}"; do ran+="${r%%|*} "; done   # " name1 name2 … " of tasks that ran

# build/reports + JUnit XML from every module — generic (any task can produce them). These reflect
# the current state of build/; on a partial run they may include reports from earlier full runs.
find . "${prune[@]}" -o -path '*/build/reports' -type d -print -prune | while read -r d; do
  mod=$(echo "$d" | sed 's|^\./||;s|/build/reports$||;s|/|-|g')
  mkdir -p "$OUT/reports/$mod" && cp -r "$d"/. "$OUT/reports/$mod"/
done
find . "${prune[@]}" -o -path '*/build/test-results/*' -name 'TEST-*.xml' -print | while read -r f; do
  mod=$(echo "$f" | sed 's|^\./||;s|/build/test-results/.*$||;s|/|-|g')
  mkdir -p "$OUT/test-results/$mod" && cp "$f" "$OUT/test-results/$mod"/
done

# Task-specific outputs — only collate when that task actually ran this invocation, so a partial
# run doesn't surface stale artifacts from a previous build.
if [[ "$ran" == *" screenshot "* ]]; then
  # Paparazzi failure / diff images live under build/paparazzi, not build/reports.
  find . "${prune[@]}" -o -path '*/build/paparazzi' -type d -print -prune | while read -r d; do
    mod=$(echo "$d" | sed 's|^\./||;s|/build/paparazzi$||;s|/|-|g')
    mkdir -p "$OUT/paparazzi/$mod" && cp -r "$d"/. "$OUT/paparazzi/$mod"/
  done
fi
if [[ "$ran" == *" apk "* ]] && [ -d apps/android/build/outputs/apk/debug ]; then
  mkdir -p "$OUT/apk"; cp -r apps/android/build/outputs/apk/debug/. "$OUT/apk/"
fi
if [[ "$ran" == *" release-jvm "* ]] && [ -d apps/jvm/build/install ]; then
  mkdir -p "$OUT/jvm-dist"; cp -r apps/jvm/build/install/. "$OUT/jvm-dist/"
fi

# ---- summary ---------------------------------------------------------------
summary="$OUT/summary.txt"
{
  echo "Verification summary — $(date '+%Y-%m-%d %H:%M:%S')"
  echo
  printf '  %-16s %-6s %8s\n' "TASK" "RESULT" "TIME"
  printf '  %-16s %-6s %8s\n' "----" "------" "----"
  for r in "${results[@]}"; do
    IFS='|' read -r n s d <<<"$r"
    printf '  %-16s %-6s %7ds\n' "$n" "$s" "$d"
  done
  echo
  if [ "$overall" -eq 0 ]; then echo "OVERALL: PASS"; else echo "OVERALL: FAIL"; fi
  echo
  echo "Artifacts under $OUT/:"
  printf '  %-14s %s\n' "logs/" "full gradle output per task (start here for any FAIL)"
  desc() { [ -d "$OUT/$1" ] && printf '  %-14s %s\n' "$1/" "$2"; }
  desc reports      "ktlint, detekt, android-lint, HTML test reports (per module)"
  desc test-results "JUnit XML (unit tests + Paparazzi)"
  desc paparazzi    "screenshot diff/failure images (per module)"
  desc apk          "debug APK (apps/android)"
  desc jvm-dist     "desktop distribution (apps/jvm)"
} >"$summary"

echo
cat "$summary"
echo
if [ "$overall" -eq 0 ]; then
  echo "${G}${B}✔ all verification tasks passed${Z} — artifacts in $OUT/"
else
  echo "${R}${B}x verification failed${Z} — see $OUT/logs/ and the summary above"
fi
exit "$overall"
