# `:benchmark`

> Macrobenchmark module — measures `apps/android`'s `GridImage` scroll performance
> and app startup, and the raw-core audio real-time factor, all on a real device,
> writing one perfbench result per axis.

An Android Test (`com.android.test`) module that macrobenchmarks the installed
`:apps:android` app. It instruments the real APK from outside (self-instrumenting,
`targetProjectPath = :apps:android`): one scenario scrolls the image grid and
records frame timing + Compose trace sections (`ui` axis); another measures cold
and warm startup up to first content (`startup` axis); a third renders **every
supported file in the pushed corpus** through the raw emulator core and reports
real-time factor (`audio` axis). Each assembles a perfbench
result (device fingerprint + axis) with
[`:apps:perfbench-core`](../apps/perfbench-core/README.md); the audio axis reuses
the render engine from
[`:apps:abrender-core`](../apps/abrender-core/README.md). It's a leaf — nothing
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

Each benchmark also grants **itself** All Files Access (the same `appops` grant,
on its own package) so it can hash the pushed corpus + snapshot and record them
in every result's **corpus block** — the ordered `{path, sha256, bytes}` manifest
and the snapshot hash. That makes a device run *corpus-pinned*: the `diff` guard
refuses a baseline measured over a different corpus. The device-side read of
shared storage is only verifiable on hardware.

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
  `results-ui.json`, `results-startup.json`, and `results-audio.json` — so no
  axis overwrites another.
  They're pulled to
  `benchmark/build/outputs/connected_android_test_additional_output/.../`
  alongside the per-iteration Perfetto traces (which open directly in
  <https://ui.perfetto.dev>). Each file carries the axis the test measured, plus
  the device fingerprint (manufacturer/model, Android build, ABI, thermal status
  at start, and the Chipbox + sage commits stamped in at build time), and the
  **corpus block** — the manifest of the pushed corpus and the hash of the
  snapshot the library was seeded from.

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

Run a single axis — the audio axis is a plain instrumentation test (no macrobenchmark
compilation reset), so it is the fast way to exercise the corpus pin and the render
cores without running the whole suite:

```sh
ANDROID_SERIAL='<adb serial>' ./gradlew --no-daemon :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=net.sigmabeta.chipbox.benchmark.DeviceAudioBenchmark
```

**A device can appear twice in `adb devices`** — once by mDNS
(`<id>._adb-tls-connect._tcp`) and once by IP (`192.168.x.y:port`). Gradle then fans
out to both serials and runs two instrumentation sessions on the one phone, which
collides and reports a misleading `Process crashed` /
`DELETE_FAILED_INTERNAL_ERROR` (with nothing incriminating in logcat). Pin
`ANDROID_SERIAL` to one serial, or `adb disconnect` the duplicate, before running.

**Known unrelated failure:** the `ui` and `startup` axes can abort at macrobenchmark's
`dropShaderCache` with `The DROP_SHADER_CACHE broadcast was not received … androidx.profileinstaller
… is old`. That is the `:apps:android` benchmark variant's profileinstaller version,
not a regression in the axis under test — don't chase it as a code fault.

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

## Audio axis

`DeviceAudioBenchmark` measures the **audio** axis: real-time factor for **every
supported file in the pushed corpus**, one result per file keyed by its
corpus-relative path. For each file it warms up once, then times N renders
through the reused raw emulator core (`EmulatorBank.render` from
`:apps:abrender-core`) and reduces them with the same
[`:apps:perfbench-core`](../apps/perfbench-core/README.md) math the host audio axis
uses. A file that fails to render is recorded as an errored result rather than
dropped, so a run never silently shrinks. The only difference from the host axis
is the backend: this APK carries the **arm64** emulator `.so`s, so the numbers are
the phone's ground truth. The pushed corpus + snapshot must be in place (both are
read and hashed into the result's corpus block, so a run is corpus-pinned). RTF is
inherently machine-independent, so this is the one device axis directly comparable
across machines.

## Contents

| File | What it is |
| --- | --- |
| `DeviceBenchmark.kt` | shared device-side plumbing: target package, device fingerprint facts, build-stamped commits, the corpus pin (manifest + snapshot hash) read from shared storage, the corpus/snapshot paths, and the per-axis result sink. |
| `DeviceAudioBenchmark.kt` | `audioRealTimeFactor` — walks the pushed corpus and times raw-core renders file by file, reporting real-time factor (`audio` axis + device fingerprint), written to `results-audio.json`. |
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
- **Packaging:** the audio axis's arm64/x86_64 emulator `.so`s are packaged into
  the test APK (extracted at install). Stray core-less ABIs are excluded so the
  install resolves an ABI that actually has the cores.
- **SAGE/module dependencies:** [`apps:perfbench-core`](../apps/perfbench-core/README.md)
  (the result schema / math) and [`apps:abrender-core`](../apps/abrender-core/README.md)
  (the reused raw-core render engine for the audio axis). It instruments
  `:apps:android` via `targetProjectPath` rather than depending on it; test libs
  only otherwise: `androidx.test.ext.junit`, `androidx.test.runner`,
  `androidx.test.uiautomator`, `androidx.benchmark.macro.junit4`.
