# `:cbox:common:player:generator:fake`

> Two non-production `Generator`s: a recording test stub and an in-process synth runtime swap.

In-memory doubles for `:cbox:common:player:generator:api`. Holds the
call-recording test stub (`FakeGenerator`) and the JNI-free synth runtime
(`SynthGenerator`). Being a `:fake`, depend on it from tests/dev tooling to drive
the pipeline without the real native emulators.

## Contents

| File | What it is |
| --- | --- |
| `FakeGenerator.kt` | Test stub implementing `Generator` directly: tests push `GeneratorEvent`s via `emit(event)` and inspect recorded transport calls (`startTrackCalls`, `playCalls`, `seekCalls`, …). No production loop — just enough to drive the `Director`'s reducer in isolation. Its event-sink shape matches `BaseGenerator` so flooding events without a collector won't deadlock. |
| `SynthGenerator.kt` | Development runtime `Generator`: a `BaseGenerator` subclass whose factory bypasses every native emulator in favor of the in-process `FakeEmulator` (sine/square synth driven by procedural tracks). Skips the PCM cache entirely. Also contains the private `SynthPcmTrackSourceFactory`/`SynthPcmTrackSource`. |

The `Fake*` prefix is reserved for the test stub; the runtime synth is named
`Synth*` because it is a production swap, not a test double.

## Why depend on this module

Depend on `:generator:fake` from tests that need to drive the `Director` over a
controllable `Generator` (`FakeGenerator`), or from dev/headless builds that want
the pipeline running without JNI dependencies (`SynthGenerator`). Production code
depends on `:api` and wires `:real`.

## Using it

```kotlin
// Test stub: push events and assert on recorded calls.
val generator = FakeGenerator()
generator.emit(GeneratorEvent.Emitting(producedMs = 0, trackId = 1L))
assertEquals(listOf(1L), generator.startTrackCalls)

// Runtime synth: a real BaseGenerator backed by the in-process FakeEmulator.
val synth: Generator = SynthGenerator(
    repository = repository,
    contentSourceRegistry = registry,
    bufferManager = producerBufferManager,
    hatchet = hatchet,
)
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `:cbox:common:player:generator:api` (`api`); `:cbox:common:player:emulators:fake`, `:cbox:common:utils:api` (`implementation`)
