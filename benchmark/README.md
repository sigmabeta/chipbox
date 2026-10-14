# `:benchmark`

> Macrobenchmark module — measures `apps/android`'s `GridImage` scroll performance
> and app startup on a real device, and writes one perfbench result per axis.

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
itself from a **library snapshot** pushed to a fixed shared external location, once at
startup, before the UI composes:

1. Generate the snapshot on the host from your corpus:
   ```sh
   ./gradlew :apps:perfbench:run --args="snapshot --dir /path/to/corpus --out build/perfbench/library-snapshot.json"
   ```
2. Push the snapshot and the corpus audio onto the device (the paths
   `:apps:perfbench-core`'s `DeviceBenchmarkLayout` defines). These live under
   **shared** external storage — `/sdcard/Music/chipbox-test/` — beside where a
   real library folder sits, **not** under the app's `Android/data/` (which the
   macrobenchmark's compilation-mode reset deletes when it uninstalls + reinstalls
   the app on API < 34). The app reads it via All Files Access
   (`MANAGE_EXTERNAL_STORAGE`), which each benchmark re-grants in its `setupBlock`
   after that reset:
   ```sh
   adb push build/perfbench/library-snapshot.json \
     /sdcard/Music/chipbox-test/
   adb push /path/to/corpus/. \
     /sdcard/Music/chipbox-test/corpus/
   ```
   The snapshot's corpus-relative paths rebase onto that `corpus/` directory.
   (A shared path also sidesteps `adb`'s inability to create nested dirs under
   `Android/data` on Android 11+.)
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
- **Per-axis results:** each test method writes its own artifact to the run's
  additional test output dir, named for the axis it measured —
  `results-ui.json` and `results-startup.json` — so no axis overwrites another.
  They're pulled to
  `benchmark/build/outputs/connected_android_test_additional_output/.../`
  alongside the per-iteration Perfetto traces (which open directly in
  <https://ui.perfetto.dev>). Each file carries the axis the test measured, plus
  the device fingerprint (manufacturer/model, Android build, ABI, thermal status
  at start, and the Chipbox + sage commits stamped in at build time).

## Running & debugging on a device

The suite fans out to **every** connected device. To run one device — and to keep
a stale Gradle daemon from hiding the selection — set `ANDROID_SERIAL` and use
`--no-daemon`; adb serials can contain spaces and a `(2)` suffix, so quote them:

```sh
ANDROID_SERIAL='<adb serial>' ./gradlew --no-daemon :benchmark:connectedBenchmarkAndroidTest
```

Run a single scenario with the instrumentation class filter:

```sh
ANDROID_SERIAL='<adb serial>' ./gradlew --no-daemon :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=net.sigmabeta.chipbox.benchmark.GridImageScrollBenchmark
```

When a run fails, **`adb logcat` is the ground truth** — a macrobenchmark failure
looks opaque from Gradle, but logcat shows the cause: the compilation-mode reset
(`Clearing ART profiles`, `deletePackageX`), a `ProfileInstallReceiver` broadcast
the app never answered, or an exception in the app's `Application.onCreate` (e.g.
the seed failing to read the shared corpus). Each test's result, its
`additionaltestoutput.benchmark.message.*.txt`, and the per-iteration Perfetto
traces land under
`benchmark/build/outputs/connected_android_test_additional_output/<device>/`.

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
| `DeviceBenchmark.kt` | shared device-side plumbing: target package, device fingerprint facts, build-stamped commits, fixed corpus path, and the per-axis result sink. |
| `GridImageScrollBenchmark.kt` | `browseByGameScroll` — WARM-startup benchmark that opens BrowseByGame, flings the grid up/down, then maps the macrobenchmark result to a perfbench `results-ui.json` (`ui` axis + device fingerprint). |
| `LibraryStartupBenchmark.kt` | `coldAndWarmStartup` — measures cold and warm launches up to first content and reports time-to-first-content percentiles (`startup` axis + device fingerprint), written to `results-startup.json`. |
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
