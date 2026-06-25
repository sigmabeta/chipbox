# `:cbox:common:player:generator:api`

> The producer stage of the playback pipeline — interface, events, and the production base class.

The `:api` module for the pipeline's **Generator** stage: it resolves a track id
to bytes, decodes it to PCM, and pushes `AudioBuffer`s into the downstream buffer
manager for the speaker to consume. Being an `:api`, depend on it to consume the
generator service or to subclass `BaseGenerator`. The production implementation
lives in `:cbox:common:player:generator:real`; in-memory doubles in
`:cbox:common:player:generator:fake`.

## Contents

| File | What it is |
| --- | --- |
| `Generator.kt` | The producer interface the `Director` drives: `events()`/`debugInfo()` hot flows plus the transport surface `startTrack`/`play`/`pause`/`stop`/`seek`/`release`. |
| `BaseGenerator.kt` | Production base class implementing `Generator`. Owns the single-coroutine decode loop, the 1-slot next-track `Channel`, leading-silence trimming, render-ahead wait (`Rendering` heartbeats), buffer fill/recycle, and source teardown. Subclasses supply only a `PcmTrackSource.Factory`. |
| `GeneratorEvent.kt` | Sealed event type the loop emits: `Loading`, `Emitting(producedMs, trackId, cachedMs)`, `Rendering(cachedMs)`, `TrackChange` (a `data object`), `Error`. |
| `GeneratorDebugInfo.kt` | Observational snapshot (current track, sample rate, `producedMs`, `looping`, last event/error, source diagnostics) for the debug PlaybackStatus screen. |
| `BaseGeneratorTeardownTest.kt` | `commonTest` regression guard: `stop()` must await the source close (so the next track's load can't race the singleton emulator's teardown) and a cold start resets the buffer pool. |

## Why depend on this module

Depend on `:generator:api` when your code drives or observes the producer stage:
the `Director` holds a `Generator` and consumes `events()`. Subclass
`BaseGenerator` to add a decoding strategy without re-implementing the loop — the
production `RealGenerator` (`:real`) and the dev `SynthGenerator` (`:fake`) both
do exactly that. Tests can implement `Generator` directly (see `FakeGenerator` in
`:fake`) to drive the director's reducer without dragging a `Repository` /
`ContentSourceRegistry` / `ProducerBufferManager` into the fixture. The app wires
`:real`.

## Using it

```kotlin
// The Director observes events and drives transport:
generator.events()
    .onEach { event ->
        when (event) {
            is GeneratorEvent.Emitting -> /* BUFFERING -> PLAYING */ Unit
            is GeneratorEvent.Rendering -> /* render-ahead liveness */ Unit
            GeneratorEvent.TrackChange -> generator.startTrack(nextTrackId)
            is GeneratorEvent.Error -> /* surface error */ Unit
            is GeneratorEvent.Loading -> Unit
        }
    }
    .launchIn(scope)

generator.startTrack(trackId)   // queue + start the loop
generator.seek(positionMs)      // reposition; director drains/flushes the speaker first
generator.stop()                // tears down current track (awaits source close)

// To add a decode strategy, subclass BaseGenerator and supply a factory:
class MyGenerator(/* deps */) : BaseGenerator(/* repo, registry, buffer, hatchet */) {
    override val pcmSourceFactory: PcmTrackSource.Factory = MyFactory()
}
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `chipbox.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain` (+ `commonTest`)
- **SAGE/module dependencies:** `:cbox:common:player:common:api`, `:cbox:common:player:buffer:api`, `:cbox:common:player:cache:api`, `:cbox:common:repository:api`, `:cbox:common:contentsource:api`, `kotlinx.coroutines.core`, `sage.common.logging` (all `api`); `:cbox:common:utils:api` (`implementation`); `:cbox:common:repository:fake` (`commonTest`)
