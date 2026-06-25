# `:benchmark`

> Macrobenchmark module — measures `apps/android`'s `GridImage` scroll performance on a real device.

An Android Test (`com.android.test`) module that macrobenchmarks the installed
`:apps:android` app. It instruments the real APK from outside (self-instrumenting,
`targetProjectPath = :apps:android`) and records frame timing + Compose trace
sections while scrolling the image grid. It's a leaf — nothing depends on it.

## What this builds / how to run it

- **Target:** a Macrobenchmark instrumentation test against `net.sigmabeta.chipbox`,
  built into the `benchmark` build variant (matching `:apps:android`'s
  `benchmark` variant: `release`-based, non-debuggable, profileable).
- **Run:** `./gradlew :benchmark:connectedBenchmarkAndroidTest` on a **physical
  device** — numbers aren't representative on an emulator
  (`androidx.benchmark.suppressErrors = EMULATOR` lets it run there anyway, but
  flip that off for real measurements).
- Per-iteration Perfetto traces land under
  `benchmark/build/outputs/connected_android_test_additional_output/...` and open
  directly in <https://ui.perfetto.dev>.

## Contents

| File | What it is |
| --- | --- |
| `GridImageScrollBenchmark.kt` | `browseByGameScroll` — WARM-startup macrobenchmark that opens BrowseByGame and flings the grid up/down, capturing `FrameTimingMetric` plus `GridImage` and `CrossfadeImage` `TraceSectionMetric`s (5 iterations). |
| `src/main/AndroidManifest.xml` | Test manifest. |

## Module facts

- **Plugin:** `android.test`.
- **Targets:** Android only (instrumentation test, `minSdk 26` / `targetSdk 36`).
- **Source set:** `src/main/java`.
- **SAGE/module dependencies:** none from the project graph — it instruments
  `:apps:android` via `targetProjectPath` rather than depending on it. Test libs
  only: `androidx.test.ext.junit`, `androidx.test.runner`,
  `androidx.test.uiautomator`, `androidx.benchmark.macro.junit4`.
