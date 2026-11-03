#!/usr/bin/env bash
#
# check-vendored-changes.sh — a functional Core/ change must update VENDORING.md.
#
# Every vendored emulator core has a VENDORING.md whose "Local changes kept" list is
# what survives an upstream resync. A change under cbox/native/<emu>/Core/ without a
# matching cbox/native/<emu>/VENDORING.md edit is a resync landmine, so fail the PR
# mechanically instead of relying on the reviewer to notice.
#
# Base resolution (first that resolves):
#   $CHIPBOX_VENDOR_BASE, origin/$GITHUB_BASE_REF, origin/HEAD, <head>~1
# Override the head with $CHIPBOX_VENDOR_HEAD (default HEAD).
#
# Usage:
#   scripts/ci/check-vendored-changes.sh [--base <ref>] [--head <ref>] [--repo <path>]
#
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BASE="${CHIPBOX_VENDOR_BASE:-}"
HEAD_REF="${CHIPBOX_VENDOR_HEAD:-HEAD}"
REPO="$ROOT"

usage() {
  cat <<'EOF'
check-vendored-changes.sh — fail when Core/ changes do not update VENDORING.md.

Usage:
  check-vendored-changes.sh [--base <ref>] [--head <ref>] [--repo <path>]
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --base) BASE="$2"; shift 2 ;;
    --head) HEAD_REF="$2"; shift 2 ;;
    --repo) REPO="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

cd "$REPO" || { echo "error: repo not found: $REPO" >&2; exit 2; }

if [ -z "$BASE" ] && [ -n "${GITHUB_BASE_REF:-}" ]; then
  BASE="origin/${GITHUB_BASE_REF}"
fi
if [ -z "$BASE" ] && git rev-parse --verify --quiet origin/HEAD >/dev/null 2>&1; then
  BASE="origin/HEAD"
fi
if [ -z "$BASE" ]; then
  BASE="${HEAD_REF}~1"
fi

if ! git rev-parse --verify --quiet "${BASE}^{commit}" >/dev/null 2>&1; then
  echo "check-vendored-changes: base '$BASE' is not a commit; skipping"
  exit 0
fi
if ! git rev-parse --verify --quiet "${HEAD_REF}^{commit}" >/dev/null 2>&1; then
  echo "check-vendored-changes: head '$HEAD_REF' is not a commit; skipping"
  exit 0
fi

changed="$(git diff --name-only "$BASE"..."$HEAD_REF" 2>/dev/null || true)"
if [ -z "$changed" ]; then
  echo "check-vendored-changes: no changes between $BASE and $HEAD_REF"
  exit 0
fi

core_files="$(printf '%s\n' "$changed" | grep -E '^cbox/native/[^/]+/Core/' || true)"
if [ -z "$core_files" ]; then
  echo "check-vendored-changes: no vendored Core/ changes"
  exit 0
fi

modules="$(printf '%s\n' "$core_files" | sed -E 's#^cbox/native/([^/]+)/Core/.*#\1#' | sort -u)"
missing=0
for module in $modules; do
  if ! printf '%s\n' "$changed" | grep -qx "cbox/native/${module}/VENDORING.md"; then
    echo "FAIL cbox/native/${module}/Core/ changed without cbox/native/${module}/VENDORING.md"
    printf '%s\n' "$core_files" | grep "^cbox/native/${module}/Core/" | sed 's/^/  /'
    missing=$((missing + 1))
  fi
done

if [ "$missing" -gt 0 ]; then
  echo "check-vendored-changes: add the divergence to each VENDORING.md 'Local changes kept' list"
  exit 1
fi
echo "check-vendored-changes: OK"
