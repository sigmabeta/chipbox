#!/usr/bin/env bash
#
# device-ui.sh — run the on-device UI-scroll benchmark and gate on a regression.
#
# The ui axis: frame timing for each core surface (BrowseByGame grid, Home, BrowseAllTracks, artist
# detail), measured with a macrobenchmark (per-scenario compilation reset). See
# device-common.sh for the shared flow.
#
# Runs on every attached device.
#
# Usage:
#   scripts/benchmark/device-ui.sh <corpus-dir> [--record] [--threshold <ratio>]
#
# Environment:
#   CHIPBOX_PERF_BASELINE   baseline root (default build/perfbench-baseline/device)
set -uo pipefail
AXIS="ui"
TEST_CLASS="net.sigmabeta.chipbox.benchmark.UiScrollBenchmark"
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/device-common.sh"
benchmark_device_main "$@"
