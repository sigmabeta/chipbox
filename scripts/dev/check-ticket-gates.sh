#!/usr/bin/env bash
#
# check-ticket-gates.sh — fail a ticket whose status lies about its gates.
#
# LEGACY: reads `.scratch/<feature>/issues/*.md` only; current work does not use
# these ticket files.
#
# CODING_STANDARDS.md: a device/browser/release gate is done when it has run. A ticket
# that could not run one must stay `ready-for-human` and carry a `Manual gates:` line
# plus a `## Answer` recording what ran and what a human still has to run.
#
# This checks the mechanical half — status vs unchecked boxes and manual gates — so a
# merger does not have to read the prose. It does not judge whether a recorded gate is
# convincing; a human does that.
#
# Usage:
#   scripts/dev/check-ticket-gates.sh --feature <slug>
#   scripts/dev/check-ticket-gates.sh --all
#
# Exit 0 when clean, 1 on findings, 2 on bad usage. `--all` may flag historical
# tickets that predate this rule; the merge gate always names one feature.
#
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TRACKER="${ROOT}/.scratch"
FEATURE=""
ALL=0

usage() {
  cat <<'EOF'
check-ticket-gates.sh — fail a resolved ticket with unrun gates.

Usage:
  check-ticket-gates.sh --feature <slug>   check .scratch/<slug>/issues
  check-ticket-gates.sh --all              check every feature (may flag history)
  check-ticket-gates.sh --tracker <dir>    override the tracker root

Rules:
  resolved          -> every checkbox ticked and no Manual gates: line
  ready-for-human   -> a Manual gates: line and a ## Answer section
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --tracker) TRACKER="$2"; shift 2 ;;
    --feature) FEATURE="$2"; shift 2 ;;
    --all) ALL=1; shift ;;
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
  echo "check-ticket-gates: no tracker at $TRACKER (nothing to check)"
  exit 0
fi

if [ -n "$FEATURE" ]; then
  issue_dirs="$TRACKER/$FEATURE/issues"
  if [ ! -d "$issue_dirs" ]; then
    echo "error: no issues for feature '$FEATURE' ($issue_dirs)" >&2
    exit 2
  fi
else
  issue_dirs="$(find "$TRACKER" -mindepth 2 -maxdepth 2 -type d -name issues 2>/dev/null | sort)"
fi

findings=0
for dir in $issue_dirs; do
  for f in "$dir"/*.md; do
    [ -f "$f" ] || continue
    status="$(sed -n 's/^\*\*Status:\*\*[[:space:]]*//p' "$f" | head -1 | tr -d '\r')"
    if [ -z "$status" ]; then
      echo "FAIL ${f#"$ROOT"/}: no **Status:** line"
      findings=$((findings + 1))
      continue
    fi
    manual=0; grep -q '^\*\*Manual gates:\*\*' "$f" && manual=1
    answer=0; grep -q '^## Answer' "$f" && answer=1
    unchecked="$(grep -c '^- \[ \]' "$f" 2>/dev/null || true)"
    [ -n "$unchecked" ] || unchecked=0

    case "$status" in
      resolved*)
        if [ "$unchecked" -gt 0 ]; then
          echo "FAIL ${f#"$ROOT"/}: Status resolved but $unchecked gate box(es) unchecked"
          findings=$((findings + 1))
        fi
        if [ "$manual" -eq 1 ]; then
          echo "FAIL ${f#"$ROOT"/}: Status resolved but still carries a Manual gates: line"
          findings=$((findings + 1))
        fi
        ;;
      ready-for-human*)
        if [ "$manual" -eq 0 ]; then
          echo "FAIL ${f#"$ROOT"/}: ready-for-human without a Manual gates: line"
          findings=$((findings + 1))
        fi
        if [ "$answer" -eq 0 ]; then
          echo "FAIL ${f#"$ROOT"/}: ready-for-human without a ## Answer section"
          findings=$((findings + 1))
        fi
        ;;
      ready-for-agent*|needs-triage*|needs-info*|wontfix*|done*)
        ;;
      *)
        echo "FAIL ${f#"$ROOT"/}: unknown Status '$status'"
        findings=$((findings + 1))
        ;;
    esac
  done
done

if [ "$findings" -gt 0 ]; then
  echo "check-ticket-gates: $findings finding(s)"
  exit 1
fi
echo "check-ticket-gates: OK"
