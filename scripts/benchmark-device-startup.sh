#!/usr/bin/env bash
#
# benchmark-device-startup.sh — run the on-device startup benchmark and gate on a regression.
#
# The startup axis: cold and warm launch time to first content, measured with a macrobenchmark. See
# benchmark-device-common.sh for the shared flow.
#
# Runs on every attached device.
#
# Usage:
#   scripts/benchmark-device-startup.sh <corpus-dir> [--record] [--threshold <ratio>]
#
# Environment:
#   CHIPBOX_PERF_BASELINE   baseline root (default build/perfbench-baseline/device)
set -uo pipefail
AXIS="startup"
TEST_CLASS="net.sigmabeta.chipbox.benchmark.LibraryStartupBenchmark"
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/benchmark-device-common.sh"
benchmark_device_main "$@"
