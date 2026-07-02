# `:cbox:common:player:resampler:api`

> The streaming sample-rate-converter contract a sink injects — types only.

The `:api` module for the playback pipeline's **resampler**: it defines the
`Resampler` interface a speaker sink uses to convert a track's native rate to the
device's output rate, plus the observational `ResamplerDebugInfo`. Being an
`:api`, it carries the interface and types only — depend on it to consume a
resampler; the production kernels live in `:cbox:common:player:resampler:real`
and are wired by `:cbox:common:player:resampler:di`.

## Contents

| File | What it is |
| --- | --- |
| `Resampler.kt` | The contract: `process(input, inputFrames, inputRate, outputRate, output)` resamples interleaved stereo 16-bit PCM and returns output frames written; `maxOutputFrames(...)` sizes the output buffer; `reset()` drops carried phase/history at a discontinuity. Stateful, single-threaded; a rate change between calls is treated as a stream discontinuity. |
| `ResamplerDebugInfo.kt` | Observational snapshot for the debug PlaybackStatus screen (`mode`, `active`, `inputRateHz`, `outputRateHz`); surfaced via the speaker's `SpeakerDebugInfo`. Reports `active = false` when bypassed (OS mode, or input == output). |

## Why depend on this module

Depend on `:resampler:api` when your code converts a track's native sample rate
to the sink's fixed output rate — handing the OS sink a non-standard rate (e.g.
an N64 rip's 32006 Hz) drives the platform mixer onto its arbitrary-ratio path,
which underruns a minimal sink buffer. The `Resampler` is emulator-agnostic (it
sees only PCM and rates), so every fixed-rate sink — Android `AudioTrack`, the
JVM `SourceDataLine`, the web `AudioWorklet` — shares one tested kernel. Depend
on `:api` for the interface; the app gets the concrete `LinearResampler`/
`CubicResampler` kernels from `:real` wired through `:di`.

## Using it

```kotlin
// A sink injects a Resampler and drives one continuous stream from a single thread.
val out = ShortArray(resampler.maxOutputFrames(inFrames, inputRate, outputRate) * 2)
val outFrames = resampler.process(input, inFrames, inputRate, outputRate, out)
// ... write the first outFrames of `out` to the fixed-rate sink ...
resampler.reset()   // on seek / track change / flush
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** none (leaf)
