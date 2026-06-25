# `:cbox:common:player:resampler:real`

> The interpolating resampler kernels — linear and cubic — behind the `Resampler` contract.

The `:real` module for the playback pipeline's **resampler**: it implements the
`Resampler` interface from `:cbox:common:player:resampler:api` with two single-
technique kernels and the shared streaming machinery they sit on. As a `:real`
module it holds the production implementation; the app never depends on it
directly — `:cbox:common:player:resampler:di` binds these into the `AppScope`
graph.

## Contents

| File | What it is |
| --- | --- |
| `StreamingResampler.kt` | Abstract base owning the streaming machinery: exact-rational phase advance (integer `inPos` + `phaseNum/outputRate`, so no float drift over a long track), per-channel history carried across `process` calls, output-frame bounds, and the rate-change-as-discontinuity `reset`. Subclasses supply only the `kernel` + its tap geometry. |
| `LinearResampler.kt` | 2-point linear interpolation (`taps = 2`). The cheapest kernel; sufficient because the source is already band-limited by the emulator's DAC model and the ratios in play are mild. |
| `CubicResampler.kt` | 4-point Catmull-Rom cubic (`taps = 4`, one left tap). A quality step up from linear for a few extra multiplies per output sample. |

## Why depend on this module

You usually don't depend on `:real` directly — depend on
`:cbox:common:player:resampler:api` for the `Resampler` type. The app graph
includes `:di`, which constructs `LinearResampler`/`CubicResampler` here and
exposes them keyed by the user's `ResamplerMode`. Reach in directly only when you
need to instantiate a specific kernel outside DI (e.g. a test or an A/B render
harness).

## Using it

```kotlin
// Instantiate a kernel directly (DI normally does this for you):
val resampler: Resampler = CubicResampler()   // or LinearResampler()

val out = ShortArray(resampler.maxOutputFrames(inFrames, 32_006, 48_000) * 2)
val outFrames = resampler.process(input, inFrames, 32_006, 48_000, out)
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `:cbox:common:player:resampler:api` (`api`)
