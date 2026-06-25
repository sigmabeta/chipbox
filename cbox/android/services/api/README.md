# `:cbox:android:services:api`

> The Media3 `MediaLibraryService` — Chipbox's foreground playback service and media-browse tree.

Android system glue (`:api`): the Android playback service built on Media3 (`MediaLibraryService`
/ `MediaLibrarySession`). It bridges the cross-platform `Director` playback pipeline to the
Android media framework (notification, lock screen, Android Auto, system media controls) and
exposes the library as a browsable media tree. Android-only — the `:api` role here is "platform
service + its session/browse glue", not a KMP interface.

## Contents

**Service + session:**
- `ChipboxPlaybackService.kt` — the `MediaLibraryService`. Resolves deps from
  `application as ChipboxServiceGraph` in `onCreate`, builds the `MediaLibrarySession` over a
  `DirectorPlayer` + `ChipboxLibrarySessionCallback`, wires the becoming-noisy receiver, and
  starts session persistence + history recording. Defines the media-root IDs.
- `ChipboxServiceGraph.kt` — narrow interface the app's `Application` implements so the service
  can pull `libraryBrowser`/`director`/`hatchet`/`playbackSessionPersister`/
  `playbackHistoryRecorder` off the Metro graph without a cycle back into `apps/android`.
- `ChipboxLibrarySessionCallback.kt` — `MediaLibrarySession.Callback`; serves the library root
  and children via `LibraryBrowser`.

**Player adapter:**
- `DirectorPlayer.kt` — a `SimpleBasePlayer` that maps Media3 player commands ↔ the `Director`
  pipeline and observes `ChipboxPlaybackState`.
- `AudioFocusHelper.kt` — audio-focus request/abandon with a `Callbacks` interface.
- `BecomingNoisyReceiver.kt` — pauses on `ACTION_AUDIO_BECOMING_NOISY` (e.g. headphones unplugged).

**Browse tree:**
- `LibraryBrowser.kt` — builds the browsable media items (Games / Artists / Platforms / All
  Tracks) from `Repository`; `@Inject`-constructed.
- `IdToCommandParser.kt` — parses a played media ID into a `Director` `SessionRequest`.
- `transformers/Transformers.kt` — `Game`/`Artist`/`Track`/`Platform` → `MediaItem`/`MediaMetadata`,
  attaching artwork URIs via `ArtworkUris` and content-style extras.

`AndroidManifest.xml` registers `ChipboxPlaybackService` (foreground type `mediaPlayback`) with
the `MediaLibraryService`, `MediaBrowserService`, and media-button intent filters.

## Why depend on this module

The app depends on this module to host playback as a foreground service and expose the media
library to Android Auto / system browse clients. The app's `Application` implements
`ChipboxServiceGraph` so the service can reach the Metro graph. Re-exports `androidx.media3.session`
via `api`.

## Using it

```kotlin
// The app's Application satisfies the service's dependencies off the Metro graph:
class ChipboxApplication : Application(), ChipboxServiceGraph {
    override fun director(): Director = appGraph.director
    override fun libraryBrowser(): LibraryBrowser = appGraph.libraryBrowser
    // ...hatchet(), playbackSessionPersister(), playbackHistoryRecorder()
}
```

## Module facts

- **Plugin:** `sage.android` + `sage.di`
- **Targets:** Android only
- **Source set:** `src/main/java`
- **SAGE/module dependencies:** `androidx.media3.session`, `:cbox:android:artworkprovider:api`, `:cbox:common:history:api`, `:cbox:common:player:common:api`, `:cbox:common:player:director:api`, `:cbox:common:player:persistence:api`, `:cbox:common:repository:api`, `:cbox:common:strings:api`
