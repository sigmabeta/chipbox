# `:cbox:common:player:speaker:api`

> The consumer stage of the playback pipeline — interface, events, and the production base class with all the sink-agnostic logic.

The `:api` module for the pipeline's **Speaker** stage: it pulls `AudioBuffer`s
from the buffer manager, applies fade + loudness normalization, and writes them to
a platform sink. Being an `:api`, depend on it to drive the speaker or to subclass
`BaseSpeaker`. Platform sinks live in `:cbox:common:player:speaker:real`
(Android `AudioTrack` / JVM `SourceDataLine`); WAV/stdout/test doubles in
`:cbox:common:player:speaker:fake`.

## Contents

| File | What it is |
| --- | --- |
| `Speaker.kt` | The consumer interface the `Director` drives: `events()`/`debugInfo()`/`currentPositionMs()` plus transport (`play`/`pause`/`stop`/`seek`/`switchTo`/`release`) and audio controls (`setDucked`, `setVolume`, `setNormalizationEnabled`, `setFadeInEnabled`, `setVolumeModification`/`clearVolumeModification`). |
| `BaseSpeaker.kt` | Production base class implementing `Speaker`. Owns the single-coroutine consume loop, the `VolumeProcessor` (fade + normalization gain re-derived from each buffer's live BS.1770 figures), `switchTo` filtering of stale-track buffers, drain-and-restart for seek, position publishing, and buffer recycling. Subclasses implement only `onAudioReceived`/`teardown` (plus optional `flushSink`/`readSinkPositionMs`/`awaitSinkCapacity`/`onPaused`/`onResumed`). |
| `SpeakerEvent.kt` | Sealed event type observed by the `Director`: `Buffering(positionMs)`, `Playing(positionMs)`, `TrackChange(trackId)`, `Error`. |
| `SpeakerDebugInfo.kt` | Observational snapshot (playing track, position, loop state, underrun count, volume + resampler diagnostics) for the debug PlaybackStatus screen. |

## Why depend on this module

Depend on `:speaker:api` when your code drives or observes the consumer stage: the
`Director` holds a `Speaker` and consumes `events()`. Subclass `BaseSpeaker` to
add a sink without re-implementing the consume loop — every real sink
(`RealSpeaker`, `SourceDataLineSpeaker`, `FileSpeaker`, `TextSpeaker`) does exactly
that. Tests can implement `Speaker` directly (see `FakeSpeaker` in `:fake`). The
app wires the platform `:real` sink.

## Using it

```kotlin
// The Director observes events and drives transport:
speaker.events()
    .onEach { event ->
        when (event) {
            is SpeakerEvent.Playing -> /* position update */ Unit
            is SpeakerEvent.Buffering -> /* underrun / pre-roll */ Unit
            is SpeakerEvent.TrackChange -> /* refresh now-playing metadata */ Unit
            is SpeakerEvent.Error -> /* surface error */ Unit
        }
    }
    .launchIn(scope)

speaker.play()                 // start the consume loop
speaker.switchTo(trackId)      // forced track change: drop stale-track buffers, announce
speaker.seek()                 // drain queued buffers, flush sink, restart
speaker.setDucked(true)        // 50% on transient audio-focus loss

// To add a sink, subclass BaseSpeaker:
class MySpeaker(/* deps */) : BaseSpeaker(/* bufferManager, hatchet */) {
    override fun onAudioReceived(audio: AudioBuffer) { /* write audio.data */ }
    override fun teardown() { /* release sink */ }
}
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `:cbox:common:player:common:api`, `:cbox:common:player:resampler:api`, `:cbox:common:player:buffer:api`, `kotlinx.coroutines.core`, `sage.common.logging` (all `api`)
