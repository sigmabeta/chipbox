#!/usr/bin/env bash
#
# daemon-preflight.sh — report stale Gradle/Kotlin daemons and memory before a heavy build.
#
# Kotlin/JS test compilation OOMs on a busy machine with several daemons alive. This
# prints the daemons, available memory, and the configured Kotlin heap so a failed web
# gate is not mistaken for a code failure. `--stop` clears the daemons first; it is
# opt-in because it can disturb another checkout on the same machine.
#
# Usage:
#   scripts/dev/daemon-preflight.sh [--min-free-gb N] [--strict] [--stop]
#
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MIN_FREE_GB=4
STRICT=0
STOP=0

usage() {
  cat <<'EOF'
daemon-preflight.sh — stale daemon and memory report for long Gradle builds.

Usage:
  daemon-preflight.sh [--min-free-gb N] [--strict] [--stop]

  --min-free-gb N  warn when available memory is below N GiB   (default: 4)
  --strict         exit 1 when memory is below the threshold
  --stop           stop Gradle daemons and kill Kotlin compile daemons first
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --min-free-gb) MIN_FREE_GB="$2"; shift 2 ;;
    --strict) STRICT=1; shift ;;
    --stop) STOP=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

case "$MIN_FREE_GB" in
  ''|*[!0-9]*) echo "error: --min-free-gb must be a non-negative integer" >&2; exit 2 ;;
esac

low=0

if [ "$STOP" -eq 1 ]; then
  echo "stopping Gradle daemons and Kotlin compile daemons..."
  (cd "$ROOT" && ./gradlew --stop >/dev/null 2>&1) || true
  pkill -f '[K]otlinCompileDaemon' 2>/dev/null || true
  sleep 1
fi

jps_bin="$(command -v jps 2>/dev/null || true)"
if [ -z "$jps_bin" ] && [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/jps" ]; then
  jps_bin="$JAVA_HOME/bin/jps"
fi
if [ -n "$jps_bin" ]; then
  daemons="$("$jps_bin" -l 2>/dev/null | grep -E 'GradleDaemon|KotlinCompileDaemon' || true)"
  count="$(printf '%s\n' "$daemons" | sed '/^$/d' | wc -l | tr -d ' ')"
  echo "daemons: ${count:-0}"
  [ -n "$daemons" ] && printf '%s\n' "$daemons" | sed 's/^/  /'
else
  echo "daemons: unknown (jps is not on PATH; set JAVA_HOME to a JDK)"
fi

if command -v free >/dev/null 2>&1; then
  avail="$(free -g 2>/dev/null | awk '/^Mem:/ {print $7; exit}')"
  if [ -n "$avail" ]; then
    echo "available memory: ${avail} GiB"
    if [ "$avail" -lt "$MIN_FREE_GB" ]; then
      low=1
      echo "warning: below ${MIN_FREE_GB} GiB; stop stale daemons or close other builds"
    fi
  fi
else
  echo "available memory: unknown (free is not available)"
fi

prop="$(sed -n 's/^kotlin\.daemon\.jvmargs=//p' "$ROOT/gradle.properties" 2>/dev/null | head -1)"
echo "kotlin.daemon.jvmargs: ${prop:-<unset>}"
case "$prop" in
  *-Xmx[0-9]*[gG]*) : ;;
  '') echo "warning: no kotlin.daemon.jvmargs; Kotlin/JS test compilation may OOM" ;;
  *) echo "note: raise kotlin.daemon.jvmargs to -Xmx6144m if jsNodeTest OOMs" ;;
esac

if [ "$STRICT" -eq 1 ] && [ "$low" -eq 1 ]; then
  exit 1
fi
exit 0
