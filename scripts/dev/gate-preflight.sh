#!/usr/bin/env bash
#
# gate-preflight.sh — report whether the manual gates in a feature's tickets can run here.
#
# LEGACY: reads `.scratch/<feature>/issues/*.md` only; current work does not use
# these ticket files.
#
# Run this before dispatching implementers. If a ticket needs a device, a Windows JDK,
# a browser/WASM toolchain, or a sanitizer and the host cannot provide it, the honest
# outcome is host-only work plus `ready-for-human` tickets — decide that with the user
# up front instead of discovering it at merge time.
#
# This is diagnostic: unavailable gates are a planned hand-off, not a failure. Pass
# --strict to exit 1 when any gate is blocked.
#
# Usage:
#   scripts/dev/gate-preflight.sh --feature <slug> [--strict]
#   scripts/dev/gate-preflight.sh --all [--strict]
#
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TRACKER="${ROOT}/.scratch"
FEATURE=""
ALL=0
STRICT=0

usage() {
  cat <<'EOF'
gate-preflight.sh — which manual gates can this host run?

Usage:
  gate-preflight.sh --feature <slug> [--strict]
  gate-preflight.sh --all [--strict]
  gate-preflight.sh --tracker <dir> ...
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --tracker) TRACKER="$2"; shift 2 ;;
    --feature) FEATURE="$2"; shift 2 ;;
    --all) ALL=1; shift ;;
    --strict) STRICT=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

if [ "$ALL" -eq 0 ] && [ -z "$FEATURE" ]; then
  usage >&2
  echo "error: pass --feature <slug> or --all" >&2
  exit 2
fi

if [ ! -d "$TRACKER" ]; then
  echo "gate-preflight: no tracker at $TRACKER"
  exit 0
fi

if [ -n "$FEATURE" ]; then
  issue_dirs="$TRACKER/$FEATURE/issues"
  [ -d "$issue_dirs" ] || { echo "error: no issues for feature '$FEATURE' ($issue_dirs)" >&2; exit 2; }
else
  issue_dirs="$(find "$TRACKER" -mindepth 2 -maxdepth 2 -type d -name issues 2>/dev/null | sort)"
fi

blocked=0
gates=0
for dir in $issue_dirs; do
  for f in "$dir"/*.md; do
    [ -f "$f" ] || continue
    gate="$(sed -n 's/^\*\*Manual gates:\*\*[[:space:]]*//p' "$f" | head -1 | tr -d '\r')"
    [ -n "$gate" ] || continue
    gates=$((gates + 1))
    name="$(basename "$f" .md)"
    lower="$(printf '%s' "$gate" | tr '[:upper:]' '[:lower:]')"
    printf '%-34s ' "$name"
    case "$lower" in
      *mingw*|*windows*)
        if command -v x86_64-w64-mingw32-gcc >/dev/null 2>&1; then
          echo "MinGW compiler present (Windows JDK still required)"
        else
          echo "BLOCKED: no x86_64-w64-mingw32-gcc"
          blocked=$((blocked + 1))
        fi
        ;;
      *device*|*arm64*|*on-device*)
        if ! command -v adb >/dev/null 2>&1; then
          echo "BLOCKED: adb is not on PATH"
          blocked=$((blocked + 1))
        else
          count="$(adb devices 2>/dev/null | awk 'NR > 1 && $2 == "device" {n++} END {print n + 0}')"
          if [ "$count" -gt 0 ]; then
            echo "available: $count device(s)"
          else
            echo "BLOCKED: adb devices is empty"
            blocked=$((blocked + 1))
          fi
        fi
        ;;
      *web*|*browser*)
        missing=""
        for c in node yarn; do
          command -v "$c" >/dev/null 2>&1 || missing="$missing $c"
        done
        emsdk="${EMSDK:-$HOME/emsdk}"
        [ -x "$emsdk/upstream/emscripten/emcc" ] || missing="$missing emsdk"
        if [ -z "$missing" ]; then
          echo "available: node/yarn/emsdk"
        else
          echo "BLOCKED: missing$missing"
          blocked=$((blocked + 1))
        fi
        ;;
      *sanitizer*)
        tmp="$(mktemp -d 2>/dev/null || true)"
        if [ -n "$tmp" ]; then
          printf 'int main(void){return 0;}\n' > "$tmp/t.c"
          if "${CC:-cc}" -fsanitize=address,undefined "$tmp/t.c" -o "$tmp/t" >/dev/null 2>&1; then
            echo "available: ASan/UBSan link"
          else
            echo "BLOCKED: ASan/UBSan link fails"
            blocked=$((blocked + 1))
          fi
          rm -rf "$tmp"
        else
          echo "BLOCKED: cannot create a temp dir"
          blocked=$((blocked + 1))
        fi
        ;;
      *)
        echo "manual: $gate"
        ;;
    esac
  done
done

if [ "$gates" -eq 0 ]; then
  echo "gate-preflight: no Manual gates: lines in the selected tickets"
  exit 0
fi

echo
echo "gate-preflight: $gates gate(s), $blocked blocked on this host"
if [ "$blocked" -gt 0 ]; then
  echo "Plan for ready-for-human tickets with a Manual gates: line and ## Answer."
  [ "$STRICT" -eq 1 ] && exit 1
fi
exit 0
