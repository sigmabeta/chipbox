#!/usr/bin/env bash
#
# perf-lock.sh — serialize timing-sensitive work on one machine.
#
# Concurrent Gradle/native renders make wall time and real-time factor unusable even
# when the code is unchanged. Wrap a recording or benchmark in this lock so a second
# run waits (or fails fast) instead of contaminating the first.
#
# The lock is a portable atomic mkdir, so it works on Linux and macOS. A lock whose
# owner pid is gone is cleared automatically, and so is an ownerless lock older than
# a minute (the holder died between mkdir and writing `owner`). The default location
# is ~/.cache/chipbox/perf.lock.d, shared by every checkout on the machine; override
# with CHIPBOX_PERF_LOCK_DIR or --lock-dir.
#
# Usage:
#   perf-lock.sh [--wait <seconds>] [--lock-dir <path>] -- <command> [args...]
#   perf-lock.sh --status
#
# Exit 75 (EX_TEMPFAIL) when --wait is 0 and the lock is held.
#
set -uo pipefail

WAIT="${CHIPBOX_PERF_LOCK_WAIT:-1800}"
LOCK_DIR="${CHIPBOX_PERF_LOCK_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/chipbox/perf.lock.d}"

usage() {
  cat <<'EOF'
perf-lock.sh — hold a machine-wide lock while a timing run executes.

Usage:
  perf-lock.sh [--wait <seconds>] [--lock-dir <path>] -- <command> [args...]
  perf-lock.sh --status

Default --wait: 1800 seconds (CHIPBOX_PERF_LOCK_WAIT). A held lock exits 75 when
the wait is 0; otherwise the runner waits, then reports the holder.
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --wait) WAIT="$2"; shift 2 ;;
    --lock-dir) LOCK_DIR="$2"; shift 2 ;;
    --status)
      if [ -d "$LOCK_DIR" ]; then
        echo "perf-lock: held"
        if [ -f "$LOCK_DIR/owner" ]; then
          sed 's/^/  /' "$LOCK_DIR/owner"
        elif find "$LOCK_DIR" -maxdepth 0 -mmin +0 -print -quit 2>/dev/null | grep -q .; then
          echo "  (stale: ownerless for over a minute; the next run reclaims it)"
        else
          echo "  (owner metadata not written yet)"
        fi
        exit 0
      fi
      echo "perf-lock: free"
      exit 0
      ;;
    --) shift; break ;;
    -h|--help) usage; exit 0 ;;
    *) break ;;
  esac
done

[ $# -ge 1 ] || { usage >&2; exit 2; }
case "$WAIT" in
  ''|*[!0-9]*) echo "error: --wait must be a non-negative integer" >&2; exit 2 ;;
esac

mkdir -p "$(dirname "$LOCK_DIR")" || { echo "perf-lock: cannot create $(dirname "$LOCK_DIR")" >&2; exit 2; }

acquire() {
  mkdir "$LOCK_DIR" 2>/dev/null || return 1
  # Write the metadata aside and rename it into place: a reader never sees a
  # half-written owner file, and a holder killed before the rename leaves only
  # `.owner.<pid>` behind (an ownerless lock, recovered by the grace period below).
  local tmp="$LOCK_DIR/.owner.$$"
  if ! {
    printf 'pid=%s\n' "$$"
    printf 'started=%s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    printf 'cmd=%s\n' "$*"
  } > "$tmp" 2>/dev/null || ! mv -f "$tmp" "$LOCK_DIR/owner" 2>/dev/null; then
    rm -rf "$LOCK_DIR"
    return 1
  fi
  return 0
}

# True when the lock directory exists and is older than a full minute. `-mmin +0`
# is POSIX find and works on both GNU and BSD, unlike `stat -c`/`-f`.
ownerless_lock_is_stale() {
  find "$LOCK_DIR" -maxdepth 0 -mmin +0 -print -quit 2>/dev/null | grep -q .
}

deadline=$((SECONDS + WAIT))
last_report=-30
if ! acquire; then
  while :; do
    owner_pid="$(sed -n 's/^pid=//p' "$LOCK_DIR/owner" 2>/dev/null | head -1)"
    if [ -n "$owner_pid" ] && ! kill -0 "$owner_pid" 2>/dev/null; then
      echo "perf-lock: clearing stale lock (pid $owner_pid is gone)" >&2
      rm -rf "$LOCK_DIR"
      acquire && break
      continue
    fi
    if [ -z "$owner_pid" ] && ownerless_lock_is_stale; then
      echo "perf-lock: clearing stale ownerless lock (holder died before writing owner)" >&2
      rm -rf "$LOCK_DIR"
      acquire && break
      continue
    fi
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "perf-lock: timed out after ${WAIT}s; holder:" >&2
      if [ -f "$LOCK_DIR/owner" ]; then
        sed 's/^/  /' "$LOCK_DIR/owner" >&2
      else
        echo "  (owner metadata not written yet)" >&2
      fi
      exit 75
    fi
    if [ $((SECONDS - last_report)) -ge 30 ]; then
      echo "perf-lock: waiting for holder (pid ${owner_pid:-unknown})" >&2
      last_report=$SECONDS
    fi
    sleep 1
  done
fi

trap 'rm -rf "$LOCK_DIR"' EXIT
trap 'exit 130' INT TERM
"$@"
