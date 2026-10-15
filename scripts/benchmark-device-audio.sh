#!/usr/bin/env bash
#
# benchmark-device-audio.sh — run the on-device AUDIO benchmark and gate on a regression.
#
# The audio axis: real-time factor for every file in the corpus, through the raw emulator core. Runs
# `:benchmark:connectedBenchmarkAndroidTest` filtered to DeviceAudioBenchmark — a plain instrumentation
# test, so no macrobenchmark compilation reset. See benchmark-device-common.sh for the shared flow.
#
# Runs on every attached device.
#
# Usage:
#   scripts/benchmark-device-audio.sh <corpus-dir> [--record] [--threshold <ratio>]
#
# Environment:
#   CHIPBOX_PERF_BASELINE   baseline root (default build/perfbench-baseline/device)
set -uo pipefail
AXIS="audio"
TEST_CLASS="net.sigmabeta.chipbox.benchmark.DeviceAudioBenchmark"
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/benchmark-device-common.sh"
benchmark_device_main "$@"
