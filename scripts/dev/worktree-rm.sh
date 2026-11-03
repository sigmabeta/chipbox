#!/usr/bin/env bash
#
# worktree-rm.sh — remove ticket worktrees that contain the sage submodule.
#
# `git worktree remove` refuses any worktree containing a submodule ("working trees
# containing submodules cannot be moved or removed"), which is every ticket worktree
# in this repo. This checks for tracked changes first, then removes with --force and
# retries after a submodule deinit if the first attempt fails.
#
# Usage:
#   scripts/dev/worktree-rm.sh <path> [<path> ...]
#   scripts/dev/worktree-rm.sh --force <path> [...]   # discard tracked changes too
#
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

FORCE=0
paths=()

usage() {
  cat <<'EOF'
worktree-rm.sh — remove ticket worktrees that contain the sage submodule.

Usage:
  worktree-rm.sh <path> [<path> ...]
  worktree-rm.sh --force <path> [...]   # discard tracked changes

Refuses a worktree with tracked changes unless --force is given.
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --force) FORCE=1; shift ;;
    -h|--help) usage; exit 0 ;;
    -*) echo "unknown option: $1" >&2; exit 2 ;;
    *) paths+=("$1"); shift ;;
  esac
done

if [ "${#paths[@]}" -eq 0 ]; then
  usage >&2
  exit 2
fi

rc=0
for wt in "${paths[@]}"; do
  if [ ! -d "$wt" ]; then
    echo "skip (missing): $wt"
    continue
  fi
  dirty="$(git -C "$wt" status --porcelain --untracked-files=no 2>/dev/null || true)"
  if [ -n "$dirty" ] && [ "$FORCE" -ne 1 ]; then
    echo "refuse $wt: tracked changes (pass --force to discard)"
    printf '%s\n' "$dirty" | sed 's/^/  /'
    rc=1
    continue
  fi
  if git worktree remove --force "$wt" >/dev/null 2>&1; then
    echo "removed $wt"
  else
    # A worktree whose submodule is still registered can need the deinit first.
    git -C "$wt" submodule deinit -f sage >/dev/null 2>&1 || true
    if git worktree remove --force "$wt" >/dev/null 2>&1; then
      echo "removed $wt (after submodule deinit)"
    else
      echo "FAILED $wt"
      rc=1
    fi
  fi
done
exit "$rc"
