# `:cbox:common:player:director:api`

> The `Director` contract — the top-of-pipeline playback seam the rest of the app talks to.

The interface and value types for Chipbox's playback orchestrator. This is the
`:api` module: it holds only the `Director` interface, the reified request type,
and the observable state types — depend on it to consume playback. The production
implementation lives in `:cbox:common:player:director:real`, is wired via
`:cbox:common:player:director:di`, and a test double lives in
`:cbox:common:player:director:fake`.

## Contents

| File | What it is |
| --- | --- |
| `Director.kt` | The interface. One imperative entry point `request(SessionRequest)`, plus five hot `SharedFlow`s: `metadataState()` (current `Track?`), `playbackState()` (`ChipboxPlaybackState`), `sessionState()` (`Session?`), `setlistState()` (`List<SetlistEntry>`), `errorEvents()` (`PlayerErrorEvent`). |
| `SessionRequest.kt` | Sealed interface reifying every control the app can submit — session lifecycle (`Start`, `StartSetlist`, `Restore`), transport (`Play`/`Pause`/`Stop`/`Seek`/`SkipForward`/`SkipBack`/`PlayPosition`), queue edits (`Reorder`, `RemoveTrack`), modes (`SetShuffled`, `SetRepeatMode`), audio focus (`PauseTemporarily`, `Duck`, `ResumeFocus`), and `SetVolume`. |
| `ChipboxPlaybackState.kt` | Reduced playback snapshot: `state`, `position`, `generatorProducedMs`, `cachedMs`, `playbackSpeed`, `skipForwardAllowed`, `errorMessage`. |
| `PlayerState.kt` | Lifecycle enum: `IDLE`, `STOPPED`, `BUFFERING`, `PLAYING`, `PAUSED`, `ENDING`, `ERROR`. |
| `PlayerErrorEvent.kt` | A human-readable `message` paired with the `Track?` the error is attributed to. |
| `SetlistEntry.kt` | One play-queue slot: a stable `slotId` (survives reorder/removal, distinguishes duplicate `trackId`s) plus an `active` flag for the playing slot. |

## Why depend on this module

Depend on `:cbox:common:player:director:api` whenever you need to drive or observe
playback — ViewModels (the mini-player, now-playing, and queue screens), the
Android media-session service, and the session persister all consume these types
without touching the generator/speaker pipeline. It is the only seam other modules
should know about: submit a `SessionRequest`, observe the flows. The app wires the
`:real` impl through `:di`; tests substitute `:fake`.

## Using it

```kotlin
class NowPlayingViewModel(private val director: Director) {
    val playback: SharedFlow<ChipboxPlaybackState> = director.playbackState()

    fun onPlayPause(isPlaying: Boolean) =
        director.request(if (isPlaying) SessionRequest.Pause else SessionRequest.Play)

    fun onPlayQueueItem(position: Int) =
        director.request(SessionRequest.PlayPosition(position))
}
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `:cbox:common:models:api`, `:cbox:common:player:common:api`, `kotlinx.coroutines.core` (all `api`)
