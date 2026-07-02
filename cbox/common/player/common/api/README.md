# `:cbox:common:player:common:api`

> Shared player vocabulary and audio math — the types every playback stage agrees on.

The foundational `:api` for the playback pipeline: the session/transport types
(`Session`, `SessionType`, `RepeatMode`), the audio-math conversions and silence
helpers the stages share, and the loudness/volume building blocks (`EbuR128`,
`normalizationGain`, `VolumeProcessor`). As an `:api` it carries types and
small, dependency-free utilities only — depend on it to speak the pipeline's
common language. It is a pure types/utilities module: there is **no** matching
`:real` or `:di` sibling.

## Contents

- **Session & transport types** — `Session.kt` (`Session`: what to play, where
  to start, shuffle/repeat/modified flags, random `id`), `SessionType.kt`
  (`GAME`/`ARTIST`/`PLAYLIST`/`ALL_TRACKS`/`PLATFORM`/`SETLIST`/`SINGLE_TRACK`/
  `FAVORITES` — how the director resolves a setlist from `contentId`),
  `RepeatMode.kt` (`OFF`/`ALL`/`ONE` + `next()` round-robin).
- **Audio math** — `Utils.kt` (frame/sample/byte/millis conversions plus the
  `CHANNELS_STEREO`/`BYTES_PER_FRAME`/`SHORTS_PER_FRAME` constants and small
  `ShortArray`/`Long`/`Int` extensions). "Frame" = one stereo time slice;
  "sample" = one channel value.
- **Silence detection** — `SilenceDetection.kt` (`SILENCE_THRESHOLD_AMPLITUDE`,
  `isBufferSilent`, `firstAudibleFrame`, `maxAmplitude`) for trimming the run of
  silence many rips open with.
- **Loudness measurement** — `EbuR128.kt` (BS.1770-4 / EBU R 128 integrated
  loudness in LUFS and 4× oversampled true peak in dBTP; internal `Biquad` and
  `TruePeak` helpers). One instance per measurement, not thread-safe.
- **Normalization math** — `Normalization.kt` (`NORMALIZATION_TARGET_LUFS`,
  `NORMALIZATION_TRUE_PEAK_CEILING_DBTP`, `normalizationGain` — the single
  source of truth for loudness gain, shared by the report log and the speaker).
- **Volume application** — `VolumeProcessor.kt` (in-place fade-out + keyed,
  smoothed gain registry for duck/master/normalization) and `VolumeDebugInfo.kt`
  (its observational snapshot).
- **Timeouts** — `PlaybackTimeouts.kt` (`STALL_TIMEOUT_MS` — the one patience
  knob shared by the director's stall guard and the generator's silence abort).

## Why depend on this module

Depend on `:player:common:api` whenever your stage needs the shared player
vocabulary: the director consumes `Session`/`SessionType`/`RepeatMode` and
`STALL_TIMEOUT_MS`, the generator and speaker use the frame/millis conversions
and silence helpers, the cache integrates `EbuR128` during render-ahead, and the
speaker applies `VolumeProcessor` + `normalizationGain`. It is a leaf `:api` with
no `:real`/`:di` of its own — the types and utilities are used directly, so
there's nothing to wire. Its only dependency is `sage.common.logging`
(`VolumeProcessor` takes a `Hatchet`).

## Using it

```kotlin
// Audio math: convert a buffer's frame count to a position in milliseconds.
val positionMs = frameIndex.framesToMillis(sampleRate)

// Loudness: measure a render-ahead stream, then derive the speaker's gain.
val measurer = EbuR128(sampleRate = 44_100)
measurer.process(buffer, frameCount)
val gain = normalizationGain(
    loudnessLufs = measurer.integratedLoudness(),  // NaN until the first 400 ms
    truePeakDbtp = measurer.truePeakDbtp(),
)

// Volume: apply fade-out + smoothed modifications in place on the speaker side.
val volume = VolumeProcessor(hatchet)
volume.setNormalization(measurer.integratedLoudness(), measurer.truePeakDbtp())
volume.process(buffer, sampleRate, inputStartMillis, fadeStartMillis, fadeLengthMillis)
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `sage.common.logging` (`api`); none on other
  project modules (leaf within the pipeline)
