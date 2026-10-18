#!/usr/bin/env bash
#
# verify.sh — run the same verification tasks CI runs, summarize pass/fail, and collate every
# task's artifacts (reports, JUnit XML, Paparazzi diffs, the APKs, the desktop emulator natives)
# into one folder.
#
# It verifies the WORKING TREE, not the commit you're about to push: it warns (without failing) when
# there are uncommitted/untracked changes, or when the sage submodule points at a commit no remote
# branch contains — CI checks submodules out recursively, so that push would fail at checkout.
#
# Mirrors the gradle commands in .github/workflows/ci.yml. CI splits these across parallel jobs;
# locally they run sequentially with --continue-style independence: a failing task does NOT stop
# the others, so one run gives you the full picture. Differences from CI, on purpose: the Gradle
# daemon is left on (faster local reruns; CI uses --no-daemon in throwaway containers) and
# --max-workers is left at the machine default.
#
# The `device-*` tasks are the ones CI does NOT mirror: each runs ONE on-device benchmark axis
# (scripts/benchmark/device-<axis>.sh) and gates it on a performance regression against a
# locally-recorded baseline. They are opt-in, each takes the host corpus to push
# (`--device-<axis> <corpus-dir>`) — the audio axis renders every file, so use a small corpus. A
# missing device is a flag, never a failure; a regressed axis or a failed test DOES fail. Run the axes
# you care about; they are independent (one axis per process, so one can't disturb or delete another).
#
# The `web` task mirrors CI's web job (scripts/ci/web.sh): WASM + bundle + bundle assertion +
# jsNodeTest + server installDist. It SKIPs (exit 3) when emsdk/node/yarn are unavailable, so
# machines without the web toolchain stay green. `--web-image` also builds and smokes the Docker
# image (skipped without a running daemon).
#
# The `docker-release` task is the cheap static check (scripts/ci/verify-docker-release.sh) for the
# multi-arch Dockerfile + release workflow: platform stages, both arches, no push, pinned toolchain,
# digest-pinned base images.
#
# Usage:
#   scripts/ci/verify.sh                 # run everything
#   scripts/ci/verify.sh static-analysis unit-test   # run only the named task(s)
#   scripts/ci/verify.sh --skip-apps     # everything except the heavy app builds (release APK + desktop natives)
#   scripts/ci/verify.sh --device-audio <corpus-dir>    # also run the on-device audio gate over that corpus
#   scripts/ci/verify.sh --device-ui <corpus-dir>       # also run the on-device UI-scroll gate
#   scripts/ci/verify.sh --device-startup <corpus-dir>  # also run the on-device startup gate
#   scripts/ci/verify.sh --web-image     # also build + smoke the Docker image (needs a daemon)
#   scripts/ci/verify.sh --rerun         # force every task to re-run (Gradle --rerun-tasks; ignores cache/up-to-date)
#   scripts/ci/verify.sh --list          # list task names
#   VERIFY_OUT=/tmp/v scripts/ci/verify.sh           # override the output folder
#
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
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
# The apk/release-jvm tasks build native on purpose. apk builds the RELEASE variant, as CI and
# release.yml do, so release-only config (the appVersioning versionCode logic) is exercised; without
# the CHIPBOX_KEY_* signing env it falls back to debug signing, so it builds on any machine. release-jvm builds the desktop emulator natives
# (chipboxHostNativeLibs); the full jpackage packaging (createDistributable / .deb / .rpm) is CI-only,
# since jpackage needs a full JDK a dev JBR may lack. Otherwise this matches CI.
ALL_TASKS=(
  "static-analysis|ktlintCheck detekt --continue -Pchipbox.skipNative"
  "docker-release|"
  "benchmark|:benchmark:compileBenchmarkKotlin -Pchipbox.skipNative"
  "unit-test|jvmTest --continue"
  "screenshot|verifyPaparazziDebug --continue"
  "shared-build|:apps:jvm:classes"
  "web|"
  "web-image|"
  "android-lint|:apps:android:lintRelease -Pchipbox.skipNative"
  "release-jvm|:apps:jvm:chipboxHostNativeLibs"
  "apk|:apps:android:assembleRelease"
)
# `web-image` is opt-in (--web-image): it builds and smokes the Docker image, which needs a running
# daemon and is much slower than the bundle/tests above. `web` itself SKIPs (exit 3) when the
# emsdk/node/yarn toolchain is unavailable, so a machine without the web toolchain stays green.
WEB_OPT_IN_TASKS=(web-image)
APP_BUILD_TASKS="release-jvm apk web web-image"

# The opt-in on-device tasks (run by scripts/benchmark/device-<axis>.sh, not Gradle). Each axis is
# selected by its own flag, which also carries the host corpus to push; the axis script exits 3 to
# mean "skipped — a flag, not a failure" (no device), which the runner maps to SKIP instead of FAIL.
DEVICE_AXES=(audio ui startup)
DEVICE_TASK_NAMES=()
for _a in "${DEVICE_AXES[@]}"; do DEVICE_TASK_NAMES+=("device-$_a"); done
# axis -> corpus dir, set by --device-<axis>. One variable per axis rather than `declare -A`:
# macOS still ships bash 3.2, which has no associative arrays (the script otherwise dies at the
# selection step before running anything).
device_corpus_audio=""
device_corpus_ui=""
device_corpus_startup=""
device_corpus_set() {
  case "$1" in
    audio) device_corpus_audio="$2" ;;
    ui) device_corpus_ui="$2" ;;
    startup) device_corpus_startup="$2" ;;
  esac
}
device_corpus_get() {
  case "$1" in
    audio) printf '%s' "$device_corpus_audio" ;;
    ui) printf '%s' "$device_corpus_ui" ;;
    startup) printf '%s' "$device_corpus_startup" ;;
  esac
}

# ---- arg parsing -----------------------------------------------------------
filter=()
skip_apps=0
rerun=0
web_image=0
while [ $# -gt 0 ]; do
  arg="$1"
  case "$arg" in
    -h|--help) awk 'NR>1 && /^#/ {sub(/^# ?/, ""); print; next} NR>1 {exit}' "${BASH_SOURCE[0]}"; exit 0 ;;
    --list) printf '%s\n' "${ALL_TASKS[@]%%|*}" "${DEVICE_TASK_NAMES[@]}"; exit 0 ;;
    --skip-apps) skip_apps=1 ;;
    --web-image) web_image=1 ;;
    --rerun|--rerun-tasks) rerun=1 ;;
    --device-audio|--device-ui|--device-startup)
      [ $# -ge 2 ] || { echo "$arg needs a corpus dir: $arg <corpus-dir>" >&2; exit 2; }
      device_corpus_set "${arg#--device-}" "$2"; shift ;;
    --device-audio=*|--device-ui=*|--device-startup=*)
      _axis="${arg#--device-}"; device_corpus_set "${_axis%%=*}" "${arg#*=}" ;;
    -*) echo "unknown option: $arg" >&2; exit 2 ;;
    *) filter+=("$arg") ;;
  esac
  shift
done

# --rerun forces every task to re-execute via Gradle's --rerun-tasks (ignores up-to-date AND
# build-cache hits), so the run reflects a from-scratch build rather than cached/incremental results.
[ "$rerun" = 1 ] && GRADLE_FLAGS+=(--rerun-tasks)

valid_names=" ${ALL_TASKS[*]%%|*} ${DEVICE_TASK_NAMES[*]} ${WEB_OPT_IN_TASKS[*]} "
# bash 3.2 (macOS) treats "${empty[@]}" and "${empty[*]}" as unbound under `set -u`, so build one
# guarded text form and only expand the array inside a length check.
filter_text=""
if [ "${#filter[@]}" -gt 0 ]; then
  filter_text=" ${filter[*]} "
  for name in "${filter[@]}"; do
    if [[ "$valid_names" != *" $name "* ]]; then
      echo "unknown task: $name (see --list)" >&2; exit 2
    fi
  done
fi

# Naming a device task as a filter can't supply its required corpus, so steer it to its flag.
for axis in "${DEVICE_AXES[@]}"; do
  if [[ "$filter_text" == *" device-$axis "* ]] && [ -z "$(device_corpus_get "$axis")" ]; then
    echo "the device-$axis task needs a corpus dir: scripts/ci/verify.sh --device-$axis <corpus-dir>" >&2; exit 2
  fi
done

selected=()
for entry in "${ALL_TASKS[@]}"; do
  name="${entry%%|*}"
  if [ "$skip_apps" = 1 ] && [[ " $APP_BUILD_TASKS " == *" $name "* ]]; then continue; fi
  # web-image is opt-in via --web-image; naming it in the filter also selects it.
  if [ "$name" = "web-image" ] && [ "$web_image" != 1 ] \
     && [[ "$filter_text" != *" web-image "* ]]; then continue; fi
  if [ "${#filter[@]}" -gt 0 ] && [[ "$filter_text" != *" $name "* ]]; then continue; fi
  selected+=("$entry")
done
# Each device task is opt-in via its own `--device-<axis> <corpus>`; honor a name filter like any task.
for axis in "${DEVICE_AXES[@]}"; do
  [ -n "$(device_corpus_get "$axis")" ] || continue
  if [ "${#filter[@]}" -eq 0 ] || [[ "$filter_text" == *" device-$axis "* ]]; then
    selected+=("device-$axis")
  fi
done
if [ "${#selected[@]}" -eq 0 ]; then echo "no tasks selected" >&2; exit 2; fi

# ---- colors (only on a tty) ------------------------------------------------
if [ -t 1 ]; then R=$'\e[31m'; G=$'\e[32m'; Y=$'\e[33m'; B=$'\e[1m'; Z=$'\e[0m'; else R= G= Y= B= Z=; fi

# ---- run -------------------------------------------------------------------
# OUT is wiped below, so refuse anything that would take the repo (or worse) with it.
case "$(realpath -m "$OUT" 2>/dev/null || echo "$OUT")" in
  / | "$ROOT" | "$HOME" | "$ROOT"/build) echo "refusing to use '$OUT' as VERIFY_OUT (it gets rm -rf'd)" >&2; exit 2 ;;
esac

# (2) The run checks the working tree; flag the ways that can diverge from what CI will see.
warnings=()
if [ -n "$(git status --porcelain --ignore-submodules=dirty)" ]; then
  warnings+=("working tree has uncommitted or untracked changes — this run may not match what you push")
fi
sage_head=$(git -C sage rev-parse HEAD 2>/dev/null)
if [ -n "$sage_head" ] && [ -z "$(git -C sage branch -r --contains "$sage_head" 2>/dev/null)" ]; then
  warnings+=("sage submodule is at ${sage_head:0:8}, which no remote branch contains — push sage first or CI checkout fails")
fi
for w in ${warnings[@]+"${warnings[@]}"}; do echo "${R}warning:${Z} $w" >&2; done

rm -rf "$OUT"
mkdir -p "$LOGS"
echo "${B}Running ${#selected[@]} verification task(s); logs -> $LOGS${Z}"

results=()   # "name|PASS/FAIL/SKIP|seconds"
overall=0
for entry in "${selected[@]}"; do
  name="${entry%%|*}"; args="${entry#*|}"
  if [[ "$name" == device-* ]]; then
    axis="${name#device-}"
    printf '%s──▶ %-16s%s scripts/benchmark/device-%s.sh %s\n' "$B" "$name" "$Z" "$axis" "$(device_corpus_get "$axis")"
    start=$SECONDS
    "scripts/benchmark/device-$axis.sh" "$(device_corpus_get "$axis")" >"$LOGS/$name.log" 2>&1
    rc=$?
  elif [[ "$name" == web* ]]; then
    mode="${name#web-}"; [ "$name" = "web" ] && mode=""
    printf '%s──▶ %-16s%s scripts/ci/web.sh %s\n' "$B" "$name" "$Z" "$mode"
    start=$SECONDS
    "scripts/ci/web.sh" "$mode" >"$LOGS/$name.log" 2>&1
    rc=$?
  elif [ "$name" = "docker-release" ]; then
    # Static invariants of the multi-arch image/release wiring (no Docker daemon or toolchain).
    printf '%s──▶ %-16s%s scripts/ci/verify-docker-release.sh\n' "$B" "$name" "$Z"
    start=$SECONDS
    scripts/ci/verify-docker-release.sh >"$LOGS/$name.log" 2>&1
    rc=$?
  else
    printf '%s──▶ %-16s%s ./gradlew %s\n' "$B" "$name" "$Z" "$args"
    start=$SECONDS
    # shellcheck disable=SC2086
    "$GRADLE" $args "${GRADLE_FLAGS[@]}" >"$LOGS/$name.log" 2>&1
    rc=$?
  fi
  dur=$((SECONDS - start))
  if [ "$rc" -eq 0 ]; then
    printf '    %sPASS%s in %ds\n' "$G" "$Z" "$dur"
    results+=("$name|PASS|$dur")
  elif [ "$rc" -eq 3 ]; then
    # A task-opted skip (e.g. no device): report it as a flag, never as a failure.
    printf '    %sSKIP%s in %ds (see %s)\n' "$Y" "$Z" "$dur" "$LOGS/$name.log"
    results+=("$name|SKIP|$dur")
    warnings+=("$name was skipped — see $LOGS/$name.log")
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
if [[ "$ran" == *" apk "* ]] && [ -d apps/android/build/outputs/apk/release ]; then
  mkdir -p "$OUT/apk"; cp -r apps/android/build/outputs/apk/release/. "$OUT/apk/"
fi
if [[ "$ran" == *" release-jvm "* ]] && [ -d apps/jvm/build/jvm-native/libs ]; then
  mkdir -p "$OUT/jvm-native"; cp -r apps/jvm/build/jvm-native/libs/. "$OUT/jvm-native/"
fi
for axis in "${DEVICE_AXES[@]}"; do
  if [[ "$ran" == *" device-$axis "* ]] && [ -d "build/perfbench-device/$axis/current" ]; then
    mkdir -p "$OUT/device-$axis"
    cp -r "build/perfbench-device/$axis/current/." "$OUT/device-$axis/"
  fi
done

# ---- summary ---------------------------------------------------------------
summary="$OUT/summary.txt"
{
  echo "Verification summary — $(date '+%Y-%m-%d %H:%M:%S')"
  echo
  printf '  %-16s %-6s %8s\n' "TASK" "RESULT" "TIME"
  printf '  %-16s %-6s %8s\n' "----" "------" "----"
  for r in ${results[@]+"${results[@]}"}; do
    IFS='|' read -r n s d <<<"$r"
    printf '  %-16s %-6s %7ds\n' "$n" "$s" "$d"
  done
  echo
  if [ "$overall" -eq 0 ]; then echo "OVERALL: PASS"; else echo "OVERALL: FAIL"; fi
  for w in ${warnings[@]+"${warnings[@]}"}; do echo "WARNING: $w"; done
  echo
  echo "Artifacts under $OUT/:"
  printf '  %-14s %s\n' "logs/" "full gradle output per task (start here for any FAIL)"
  desc() { [ -d "$OUT/$1" ] && printf '  %-14s %s\n' "$1/" "$2"; }
  desc reports      "ktlint, detekt, android-lint, HTML test reports (per module)"
  desc test-results "JUnit XML (unit tests + Paparazzi)"
  desc paparazzi    "screenshot diff/failure images (per module)"
  desc apk          "release APKs, debug-signed unless CHIPBOX_KEY_* is set (apps/android)"
  desc jvm-native   "desktop emulator native libs (apps/jvm)"
  desc device-audio   "on-device audio-axis result (only when --device-audio ran)"
  desc device-ui      "on-device ui-axis result (only when --device-ui ran)"
  desc device-startup "on-device startup-axis result (only when --device-startup ran)"
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
