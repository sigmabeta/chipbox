#!/usr/bin/env bash
#
# integrate.sh — merge one ticket branch into the integration branch and gate it.
#
# A merger subagent should run this instead of restating the merge/audit/build/A-B
# commands. It audits the branch against the integration head, merges with --no-ff,
# runs the host and Android native builds, and (when given a corpus) the strict A/B
# gate. If a post-merge check fails, the merge commit is left in place and the script
# says so; fix the branch and merge again.
#
# Usage:
#   scripts/integrate.sh --branch ticket/<feature>-<nn> [options]
#
#   --base <ref>         integration branch (default: the current branch)
#   --feature <slug>     run scripts/dev/check-ticket-gates.sh first
#   --corpus <dir>       corpus for scripts/abrender/verify.sh
#   --against <ref>      recorded run label or path (default: reference)
#   --ab-out <dir>       abrender run store (default: <repo>/ab-runs)
#   --ext <a,b>          A/B extension filter
#   --seconds <n>        A/B seconds per track
#   --every-song         A/B every playable file
#   --host-task <task>   host build task (default: :apps:jvm:chipboxHostNativeLibs)
#   --android-task <t>   Android build task (default: :apps:android:assembleDebug)
#   --no-build           skip the Gradle builds
#   --no-ab              skip the A/B render
#   --dry-run            print what would run, change nothing
#
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BRANCH=""
BASE=""
FEATURE=""
CORPUS=""
AGAINST="reference"
AB_OUT="${ROOT}/ab-runs"
EXT=""
AB_SECONDS=""
EVERY_SONG=0
HOST_TASK=":apps:jvm:chipboxHostNativeLibs"
ANDROID_TASK=":apps:android:assembleDebug"
DO_BUILD=1
DO_AB=1
DRY_RUN=0

usage() {
  cat <<'EOF'
integrate.sh — merge one ticket branch into the integration branch and gate it.

Usage:
  scripts/integrate.sh --branch ticket/<feature>-<nn> [options]

Options:
  --base <ref>         integration branch (default: current branch)
  --feature <slug>     run scripts/dev/check-ticket-gates.sh first
  --corpus <dir>       corpus for scripts/abrender/verify.sh
  --against <ref>      recorded run label or path (default: reference)
  --ab-out <dir>       abrender run store (default: <repo>/ab-runs)
  --ext <a,b>          A/B extension filter
  --seconds <n>        A/B seconds per track
  --every-song         A/B every playable file
  --host-task <task>   host build task (default: :apps:jvm:chipboxHostNativeLibs)
  --android-task <t>   Android build task (default: :apps:android:assembleDebug)
  --no-build           skip the Gradle builds
  --no-ab              skip the A/B render
  --dry-run            print what would run, change nothing
EOF
}

fail() { echo "integrate: $*" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --branch) BRANCH="$2"; shift 2 ;;
    --base) BASE="$2"; shift 2 ;;
    --feature) FEATURE="$2"; shift 2 ;;
    --corpus) CORPUS="$2"; shift 2 ;;
    --against|--reference) AGAINST="$2"; shift 2 ;;
    --ab-out) AB_OUT="$2"; shift 2 ;;
    --ext) EXT="$2"; shift 2 ;;
    --seconds) AB_SECONDS="$2"; shift 2 ;;
    --every-song) EVERY_SONG=1; shift ;;
    --host-task) HOST_TASK="$2"; shift 2 ;;
    --android-task) ANDROID_TASK="$2"; shift 2 ;;
    --no-build) DO_BUILD=0; shift ;;
    --no-ab) DO_AB=0; shift ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

[ -n "$BRANCH" ] || { usage >&2; fail "--branch is required"; }
BASE="${BASE:-$(git branch --show-current)}"
[ -n "$BASE" ] || fail "detached HEAD: pass --base <integration-branch>"
[ "$BRANCH" != "$BASE" ] || fail "--branch and --base are the same"
git rev-parse --verify "${BRANCH}^{commit}" >/dev/null 2>&1 || fail "unknown branch: $BRANCH"
[ "$(git branch --show-current)" = "$BASE" ] || fail "checkout '$BASE' first (currently '$(git branch --show-current)')"

[ -z "$(git status --porcelain --ignore-submodules=dirty)" ] || fail "working tree is not clean"
[ -z "$(git -C sage status --porcelain 2>/dev/null)" ] || fail "sage submodule is not clean"

echo "──▶ audit $BRANCH against $BASE"
commits="$(git log --oneline "$BASE".."$BRANCH" 2>/dev/null || true)"
[ -n "$commits" ] || fail "no commits on $BRANCH ahead of $BASE"
printf '%s\n' "$commits" | sed 's/^/  /'
echo "  files:"
git diff --stat "$BASE"..."$BRANCH" | sed 's/^/  /'

if ! scripts/dev/check-worktree-hygiene.sh; then
  fail "worktree hygiene check failed (shared stash or stray .scratch)"
fi
if [ -n "$FEATURE" ]; then
  if ! scripts/dev/check-ticket-gates.sh --feature "$FEATURE"; then
    fail "ticket-gate check failed for '$FEATURE'"
  fi
else
  echo "note: pass --feature <slug> to enforce ticket status/gate closure before merging"
fi

if [ "$DO_BUILD" -eq 0 ] && [ "$DO_AB" -eq 0 ]; then
  echo "warning: --no-build and --no-ab: merging without a post-merge gate"
fi

if [ "$DRY_RUN" -eq 1 ]; then
  echo "──▶ dry run: would merge $BRANCH into $BASE with --no-ff"
  [ "$DO_BUILD" -eq 1 ] && echo "──▶ dry run: ./gradlew $HOST_TASK --rerun-tasks && ./gradlew $ANDROID_TASK --rerun-tasks"
  if [ "$DO_AB" -eq 1 ]; then
    if [ -n "$CORPUS" ]; then
      echo "──▶ dry run: scripts/abrender/verify.sh --where host --corpus $CORPUS --out $AB_OUT --against $AGAINST --tolerance strict"
    else
      echo "──▶ dry run: A/B skipped (pass --corpus <dir>)"
    fi
  fi
  exit 0
fi

git merge --no-ff "$BRANCH" -m "Merge $BRANCH" || fail "merge failed; resolve or abort before rerunning"
merge_sha="$(git rev-parse --short HEAD)"
echo "──▶ merged at $merge_sha"

run_check() {
  if ! "$@"; then
    echo "integrate: FAILED: $*" >&2
    echo "integrate: merge commit $merge_sha is kept; fix and rerun the failed check" >&2
    exit 1
  fi
}

if [ "$DO_BUILD" -eq 1 ]; then
  # Task lists may contain spaces; word splitting is intentional.
  # shellcheck disable=SC2086
  run_check "$ROOT/gradlew" $HOST_TASK --rerun-tasks --console=plain
  # shellcheck disable=SC2086
  run_check "$ROOT/gradlew" $ANDROID_TASK --rerun-tasks --console=plain
fi

if [ "$DO_AB" -eq 1 ]; then
  if [ -n "$CORPUS" ]; then
    ab_args=(--where host --corpus "$CORPUS" --out "$AB_OUT" --against "$AGAINST" --tolerance strict)
    [ -n "$EXT" ] && ab_args+=(--ext "$EXT")
    [ -n "$AB_SECONDS" ] && ab_args+=(--seconds "$AB_SECONDS")
    [ "$EVERY_SONG" -eq 1 ] && ab_args+=(--every-song)
    run_check "$ROOT/scripts/abrender/verify.sh" "${ab_args[@]}"
  else
    echo "integrate: A/B skipped (pass --corpus <dir> to gate audio)"
  fi
fi

echo "──▶ integrated $BRANCH into $BASE at $merge_sha"
