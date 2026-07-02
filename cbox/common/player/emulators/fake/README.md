# `:cbox:common:player:emulators:fake`

> An in-process `Emulator` that synthesizes deterministic chiptune music — no native core.

The `:fake` module: a test double / headless backend that implements the
`Emulator` contract entirely in Kotlin. Instead of emulating sound hardware it
procedurally generates a melody (seeded by the track id, so output is
deterministic) and renders it with a software synth. Used in tests, on the JVM
classpath, and anywhere a real native core is unavailable.

## Contents

- **Emulator** — `FakeEmulator` (an `object` extending `Emulator`): claims every
  extension (`isFileExtensionSupported` always true), generates a `GeneratedTrack`
  on `loadTrack`, then fills buffers note-by-note with a sine synth and an ADSR
  envelope. Sample rate is fixed at 44.1 kHz.
- **Generation** — `TrackRandomizer` (seeds a `Random` from the track id; picks
  tempo, key/`Scale`, `TimeSignature`, and loop length, then fills measures with
  notes) and `AdsrProcessor` (`PercentAdsrProcessor` / `TimeAdsrProcessor` —
  attack/decay/sustain/release amplitude shaping).
- **Synths** — `Synth` interface with `SineSynth` and `SquareSynth`
  implementations (`generate(timeMillis, frequency, amplitude): Short`).
- **Music models** (`models/`) — `GeneratedTrack`, `Measure`, `Note`, `Pitch`,
  `PitchClass`, `Interval`, `Scale`, `ScaleMode`, `Duration`, `TimeSignature` —
  the small music-theory value types the randomizer composes.

## Why depend on this module

Depend on `:fake` when you need a working `Emulator` without a native library —
unit tests, the JVM/desktop classpath, or a "something always plays" fallback.
It's contributed into the app graph by `:cbox:common:player:emulators:di`.
Depend on `:api` for the `Emulator` type itself; for real chiptune playback use
the per-format `:real` modules.

## Using it

```kotlin
FakeEmulator.loadTrack(track)            // builds a deterministic GeneratedTrack from track.id
val buffer = ShortArray(framesPerBuffer * SHORTS_PER_FRAME)
val frames = FakeEmulator.generateBuffer(buffer)   // fills interleaved L/R PCM
// ... repeat until FakeEmulator.trackOver, then:
FakeEmulator.teardown()
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (+ `commonTest`)
- **SAGE/module dependencies:** `:cbox:common:player:emulators:api` (`api`), `kotlinx-coroutines-core` (`api`), `:cbox:common:repository:api` (`implementation`)
