#!/usr/bin/env bash
#
# perf-lock-test.sh — self-test for scripts/dev/perf-lock.sh recovery paths.
#
# The lock must not wedge a machine when its holder dies: a stale owner pid, and an
# ownerless lock left by a holder killed between mkdir and the owner write, are both
# reclaimed. Run by verify.sh's static-analysis task; cheap and dependency-free.
#
# Usage: scripts/dev/perf-lock-test.sh
#
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOCK="$ROOT/scripts/dev/perf-lock.sh"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/perf-lock-test.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

pass=0
fail=0
ok() { printf '  ok    %s\n' "$*"; pass=$((pass + 1)); }
bad() { printf '  FAIL  %s\n' "$*"; fail=$((fail + 1)); }

LOCK_DIR="$TMP/perf.lock.d"

# 1. Normal acquire runs the command and releases the lock.
if "$LOCK" --lock-dir "$LOCK_DIR" -- true 2>/dev/null && [ ! -d "$LOCK_DIR" ]; then
  ok "acquire/run/release"
else
  bad "acquire/run/release"
fi

# 2. An ownerless lock older than a minute (holder killed before writing owner) is reclaimed.
mkdir -p "$LOCK_DIR"
touch -t 202001010000 "$LOCK_DIR"
out="$("$LOCK" --wait 3 --lock-dir "$LOCK_DIR" -- true 2>&1)"
rc=$?
if [ "$rc" -eq 0 ] && printf '%s' "$out" | grep -q 'clearing stale ownerless lock'; then
  ok "ownerless stale lock reclaimed"
else
  bad "ownerless stale lock reclaimed (rc=$rc, out=$out)"
fi

# 3. A lock whose owner pid is gone is reclaimed.
mkdir -p "$LOCK_DIR"
printf 'pid=999999\n' > "$LOCK_DIR/owner"
out="$("$LOCK" --wait 3 --lock-dir "$LOCK_DIR" -- true 2>&1)"
rc=$?
if [ "$rc" -eq 0 ] && printf '%s' "$out" | grep -q 'clearing stale lock'; then
  ok "dead-owner lock reclaimed"
else
  bad "dead-owner lock reclaimed (rc=$rc, out=$out)"
fi

# 4. A live holder is respected: --wait 0 exits 75 without running the command.
sleep 30 &
holder=$!
mkdir -p "$LOCK_DIR"
printf 'pid=%s\n' "$holder" > "$LOCK_DIR/owner"
"$LOCK" --wait 0 --lock-dir "$LOCK_DIR" -- true >/dev/null 2>&1
rc=$?
kill "$holder" 2>/dev/null || true
wait "$holder" 2>/dev/null || true
rm -rf "$LOCK_DIR"
if [ "$rc" -eq 75 ]; then
  ok "live holder respected (exit 75)"
else
  bad "live holder respected (rc=$rc)"
fi

# 5. --status distinguishes free, held, and stale-ownerless.
if "$LOCK" --lock-dir "$LOCK_DIR" --status | grep -q 'free'; then
  ok "--status free"
else
  bad "--status free"
fi
mkdir -p "$LOCK_DIR"
printf 'pid=%s\n' "$$" > "$LOCK_DIR/owner"
if "$LOCK" --lock-dir "$LOCK_DIR" --status | grep -q 'held'; then
  ok "--status held"
else
  bad "--status held"
fi
rm -rf "$LOCK_DIR"
mkdir -p "$LOCK_DIR"
touch -t 202001010000 "$LOCK_DIR"
if "$LOCK" --lock-dir "$LOCK_DIR" --status | grep -q 'stale'; then
  ok "--status stale-ownerless"
else
  bad "--status stale-ownerless"
fi
rm -rf "$LOCK_DIR"

printf '\nperf-lock-test: %d passed, %d failed\n' "$pass" "$fail"
[ "$fail" -eq 0 ]
