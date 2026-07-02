#!/usr/bin/env bash
#
# incremental-build-impact.sh — thin wrapper.
#
# The real script was promoted into the SAGE submodule (sage/scripts/) because it's a
# framework-general, app-agnostic tool: it anchors to whatever git repo it's invoked from
# (git rev-parse --show-toplevel) and takes the app project via --app, so it works for any
# SAGE consumer, not just chipbox. This wrapper keeps the familiar `scripts/…` entry point
# and forwards every argument to the canonical copy. Run `scripts/incremental-build-impact.sh
# --help` for the full option list.
#
# CWD is deliberately left untouched (we exec, no cd) so the real script resolves the repo
# root from where you invoked it — i.e. the chipbox root — and analyses this app's modules.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REAL="$HERE/../sage/scripts/incremental-build-impact.sh"

if [[ ! -x "$REAL" ]]; then
    echo "error: $REAL not found — is the sage submodule checked out?" >&2
    echo "       run: git submodule update --init sage" >&2
    exit 1
fi

exec "$REAL" "$@"
