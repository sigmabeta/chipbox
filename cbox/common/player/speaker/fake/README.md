# `:cbox:common:player:speaker:fake`

> Non-hardware speaker sinks: a recording test stub, a WAV-file exporter, and a stdout level meter.

In-memory / headless doubles for `:cbox:common:player:speaker:api`. Holds the
call-recording test stub (`FakeSpeaker`) plus two production-quality headless
sinks (`FileSpeaker`, `TextSpeaker`) that run the real consume loop without audio
hardware. Being a `:fake`, depend on it from tests, exporters, and dev tooling.

## Contents

| File | What it is |
| --- | --- |
| `FakeSpeaker.kt` | Test stub implementing `Speaker` directly: tests push `SpeakerEvent`s via `emit(event)`, set `currentPositionMsValue`, and inspect recorded calls (`playCalls`, `switchToCalls`, `setDuckedCalls`, …). No real sink — drives the `Director` in isolation. |
| `FileSpeaker.kt` | `BaseSpeaker` writing incoming PCM to a WAV file (okio). Writes a placeholder RIFF header up front and patches the real chunk sizes in `teardown()`; reopens the file on any sample-rate change. For exporting an emulator's output. |
| `TextSpeaker.kt` | Debug `BaseSpeaker` that logs each buffer as a `Frame \| Left \| Right` table via `Hatchet`. Verifies the producer makes sensible samples without audio hardware. |

Tests for these (`BaseSpeakerPositionTest`, `FileSpeakerTest`) live under
`src/test/java` and run on the `jvmSharedTest` source set.

## Why depend on this module

Depend on `:speaker:fake` from tests that drive the `Director` over a controllable
`Speaker` (`FakeSpeaker`), from WAV-export tooling (`FileSpeaker`), or from
headless/diagnostic runs that want to inspect produced samples (`TextSpeaker`).
Production playback depends on `:api` and wires the platform sink in `:real`.

## Using it

```kotlin
// Test stub: push events, set position, assert on recorded calls.
val speaker = FakeSpeaker()
speaker.currentPositionMsValue = 1_500L
speaker.emit(SpeakerEvent.Playing(positionMs = 1_500L))
assertEquals(listOf(42L), speaker.switchToCalls)

// Headless WAV export (real consume loop, file sink):
val exporter: Speaker = FileSpeaker(
    externalStorageDir = outputDir,   // okio Path
    fileSystem = FileSystem.SYSTEM,
    hatchet = hatchet,
    bufferManager = consumerBufferManager,
)
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (+ `jvmSharedTest`, tests under `src/test/java`)
- **SAGE/module dependencies:** `:cbox:common:player:speaker:api`, `okio` (`api`); `:cbox:common:utils:api`, `sage.common.logging` (`implementation`)
