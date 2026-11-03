#!/usr/bin/env bash
#
# check-worktree-hygiene.sh — catch the two cross-worktree footguns.
#
# 1. `git stash` is shared across every linked worktree. A stash left by one agent can
#    be popped by another and silently transplant a foreign change. Forbid it during a
#    multi-worktree run; keep temporary reverts in /tmp patches or `git checkout --`.
# 2. The issue tracker lives only in the primary checkout (`.scratch/` is gitignored).
#    A `.scratch` directory inside a worktree means an agent wrote to the wrong copy,
#    so the real ticket stayed stale.
#
# Usage:
#   scripts/dev/check-worktree-hygiene.sh [--worktree <path>] [--allow-stash]
#
# Default checks every worktree plus the shared stash. `--worktree` narrows the stray
# `.scratch` check to one directory (the stash is always global). Exit 1 on findings.
#
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

ALLOW_STASH=0
ONLY=""

usage() {
  cat <<'EOF'
check-worktree-hygiene.sh — fail on a shared stash or a tracker copy in a worktree.

Usage:
  check-worktree-hygiene.sh [--worktree <path>] [--allow-stash]

Exit 1 if the shared stash is non-empty (unless --allow-stash) or a worktree
contains a .scratch directory.
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --worktree) ONLY="$2"; shift 2 ;;
    --allow-stash) ALLOW_STASH=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

findings=0

if [ "$ALLOW_STASH" -eq 0 ]; then
  stashes="$(git stash list 2>/dev/null || true)"
  if [ -n "$stashes" ]; then
    echo "FAIL shared git stash is not empty (it is shared across linked worktrees):"
    printf '%s\n' "$stashes" | sed 's/^/  /'
    echo "     use a /tmp patch or 'git checkout -- <path>' for temporary reverts"
    findings=$((findings + 1))
  fi
fi

primary="$(git worktree list --porcelain 2>/dev/null | awk '/^worktree / {print $2; exit}')"
worktrees="$(git worktree list --porcelain 2>/dev/null | awk '/^worktree / {print $2}')"

if [ -n "$ONLY" ]; then
  if [ -d "$ONLY/.scratch" ]; then
    echo "FAIL stray tracker copy in worktree: $ONLY/.scratch"
    echo "     edit .scratch only by absolute path into $primary"
    findings=$((findings + 1))
  fi
else
  for wt in $worktrees; do
    [ "$wt" = "$primary" ] && continue
    if [ -d "$wt/.scratch" ]; then
      echo "FAIL stray tracker copy in worktree: $wt/.scratch"
      echo "     edit .scratch only by absolute path into $primary"
      findings=$((findings + 1))
    fi
  done
fi

if [ "$findings" -gt 0 ]; then
  echo "check-worktree-hygiene: $findings finding(s)"
  exit 1
fi
echo "check-worktree-hygiene: OK"
