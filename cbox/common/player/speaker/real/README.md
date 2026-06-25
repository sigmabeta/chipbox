# `:cbox:common:player:speaker:real`

> The production speaker sinks — one per platform: Android `AudioTrack` and JVM `SourceDataLine`.

The `:real` implementation of `:cbox:common:player:speaker:api`. Two
`BaseSpeaker` subclasses, one per platform, each writing PCM to its native audio
sink. Depend on `:api` for the interface and `BaseSpeaker`; the app wires the
sink for the platform it's building.

## Contents

This module has **no `commonMain`** — each sink is platform-specific, so the two
implementations live in the platform source sets directly (no `expect`/`actual`;
each platform's DI picks its own).

| File | Source set | What it is |
| --- | --- | --- |
| `RealSpeaker.kt` | `src/androidMain` | `BaseSpeaker` over Android `AudioTrack`. Pins the consume loop to a dedicated `THREAD_PRIORITY_URGENT_AUDIO` thread; rebuilds the track on input-rate change; optional in-app `Resampler` (else OS resampling); position from `playbackHeadPosition`; pause/resume and seek `flushSink()`. |
| `SourceDataLineSpeaker.kt` | `src/jvmMain` | `BaseSpeaker` over `javax.sound.sampled.SourceDataLine`. Same resampler/rate-rebuild model; little-endian byte encode for the line; pause/resume map to `stop()`/`start()`; position from `microsecondPosition`; skips `drain()` on teardown (Linux backends can hang). |

The shared consume-loop, fade, normalization, and recycling logic all live in
`BaseSpeaker` (`:api`) — these classes implement only the sink seam. There is **no
`:di` module** for the speaker stage in this set; each platform's app graph
constructs its own sink directly.

## Why depend on this module

The platform app graph depends on `:real` to construct the appropriate sink
(`RealSpeaker` on Android, `SourceDataLineSpeaker` on JVM) and bind it as the
`Speaker`. Pipeline/UI code depends on `:api`, not here. WAV/stdout/test sinks live
in `:fake`.

## Using it

```kotlin
// Android graph:
val speaker: Speaker = RealSpeaker(
    bufferManager = consumerBufferManager,
    hatchet = hatchet,
    resampler = resampler,          // null = OS resampling
    outputSampleRateHz = deviceRate,
)

// JVM graph:
val speaker: Speaker = SourceDataLineSpeaker(
    bufferManager = consumerBufferManager,
    hatchet = hatchet,
    resampler = resampler,
    outputSampleRateHz = deviceRate,
)
```

## Module facts

- **Plugin:** `sage.kmp` (no `sage.kmp.js` — no web sink here)
- **Targets:** Android + JVM
- **Source set:** `src/androidMain` (`RealSpeaker`) + `src/jvmMain` (`SourceDataLineSpeaker`); no `commonMain`
- **SAGE/module dependencies:** `:cbox:common:player:speaker:api`, `:cbox:common:player:buffer:api` (`api`); `:cbox:common:player:resampler:api`, `:cbox:common:settings:api`, `kotlinx.coroutines.core`, `sage.common.logging` (`implementation`)
