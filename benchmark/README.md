# `:benchmark`

> Macrobenchmark module — measures `apps/android`'s `GridImage` scroll performance
> and app startup on a real device, and writes a perfbench `results.json`.

An Android Test (`com.android.test`) module that macrobenchmarks the installed
`:apps:android` app. It instruments the real APK from outside (self-instrumenting,
`targetProjectPath = :apps:android`): one scenario scrolls the image grid and
records frame timing + Compose trace sections (`ui` axis); another measures cold
and warm startup up to first content (`startup` axis). Both assemble a perfbench
result (device fingerprint + axis) with
[`:apps:perfbench-core`](../apps/perfbench-core/README.md). It's a leaf — nothing
depends on it.

## Deterministic library

A scroll benchmark needs a library, and a scan inside the measured loop would
swamp the frames. Instead the `:apps:android` **`benchmark` build type** seeds
itself from a **library snapshot** pushed to a fixed external location, once at
startup, before the UI composes:

1. Generate the snapshot on the host from your corpus:
   ```sh
   ./gradlew :apps:perfbench:run --args="snapshot --dir /path/to/corpus --out build/perfbench/library-snapshot.json"
   ```
2. Push the snapshot and the corpus audio onto the device (the paths
   `:apps:perfbench-core`'s `DeviceBenchmarkLayout` defines; the app's external
   files dir needs no runtime storage permission):
   ```sh
   adb push build/perfbench/library-snapshot.json \
     /sdcard/Android/data/net.sigmabeta.chipbox/files/perfbench/
   adb push /path/to/corpus/. \
     /sdcard/Android/data/net.sigmabeta.chipbox/files/perfbench/corpus/
   ```
   The snapshot's corpus-relative paths rebase onto that `corpus/` directory.
3. The app ingests the snapshot into the library at startup (`BenchmarkLibrarySeed`,
   gated on `BuildConfig.BENCHMARK`), caching its hash so a later launch skips
   the (idempotent) merge. Other build types never seed.

## What this builds / how to run it

- **Target:** a Macrobenchmark instrumentation test against `net.sigmabeta.chipbox`,
  built into the `benchmark` build variant (matching `:apps:android`'s
  `benchmark` variant: `release`-based, non-debuggable, profileable).
- **Run:** `./gradlew :benchmark:connectedBenchmarkAndroidTest` on a **physical
  device** — numbers aren't representative on an emulator
  (`androidx.benchmark.suppressErrors = EMULATOR` lets it run there anyway, but
  flip that off for real measurements).
- **`results.json`:** each test method writes its own `results.json` to the run's
  additional test output dir, pulled to
  `benchmark/build/outputs/connected_android_test_additional_output/.../`
  alongside the per-iteration Perfetto traces (which open directly in
  <https://ui.perfetto.dev>). The file carries the axis the test measured, plus
  the device fingerprint (manufacturer/model, Android build, ABI, thermal status
  at start, and the Chipbox + sage commits stamped in at build time).

## Startup scenarios

`LibraryStartupBenchmark` measures app startup in both a **cold** launch (no
process) and a **warm** launch (a new Activity in a live process), and reports
time-to-first-content percentiles in the `startup` axis. "First content" is the
library grid showing its first items: in the `benchmark` build type the app calls
`Activity.reportFullyDrawn()` the first time a `GridImage` cell composes (via
`LocalFirstContentReporter`), which `StartupTimingMetric` records as
`timeToFullDisplayMs`. The same snapshot seeding described below must have run, so
the grid has items to show.

## Contents

| File | What it is |
| --- | --- |
| `DeviceBenchmark.kt` | shared device-side plumbing: target package, device fingerprint facts, build-stamped commits, fixed corpus path, and the `results.json` sink. |
| `GridImageScrollBenchmark.kt` | `browseByGameScroll` — WARM-startup benchmark that opens BrowseByGame, flings the grid up/down, then maps the macrobenchmark result to a perfbench `results.json` (`ui` axis + device fingerprint). |
| `LibraryStartupBenchmark.kt` | `coldAndWarmStartup` — measures cold and warm launches up to first content and reports time-to-first-content percentiles (`startup` axis + device fingerprint). |
| `src/main/AndroidManifest.xml` | Test manifest. |

## Trace sections

`GridImage` and `CrossfadeImage` each open a Perfetto/systrace section (via
[`:cbox:common:perf:api`](../cbox/common/perf/api/README.md)) around their
composition, so `TraceSectionMetric`s can sum the labelled work next to frame
timing. The app also links `androidx.compose.runtime:runtime-tracing`, but the
explicit sections are what make the metric non-zero and stable.

## Module facts

- **Plugin:** `android.test`.
- **Targets:** Android only (instrumentation test, `minSdk 26` / `targetSdk 36`).
- **Source set:** `src/main/java`.
- **SAGE/module dependencies:** [`apps:perfbench-core`](../apps/perfbench-core/README.md)
  (the result schema / math). It instruments `:apps:android` via
  `targetProjectPath` rather than depending on it; test libs only otherwise:
  `androidx.test.ext.junit`, `androidx.test.runner`, `androidx.test.uiautomator`,
  `androidx.benchmark.macro.junit4`.
