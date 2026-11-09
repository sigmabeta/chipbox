# `:features:tracks-for-artist:api`

> The Voyager route key for the "all songs by an artist" screen.

The public surface of the tracks-for-artist feature: the navigation route key and
its argument types. As an `:api` module it carries the route key and the screen's State/Action typess other features
need to navigate here — the screen implementation lives in `:real`.

## Contents

| File | What it is |
| --- | --- |
| `TracksForArtist.kt` | `@Serializable data class TracksForArtist(val artistId: Long, val artistName: String)` — the Voyager route key. Carries the artist's display name so the pushed screen can title itself without a second load. |
| `TracksForArtistState.kt` | `TracksForArtistState : ListState()` — `artistName`, an `LCE<List<Track>>`, and `playingTrackId`. `columnType` is `One` (a plain lazy `ListScreen`, so length is unbounded). Shows play-all + shuffle-all CTAs up front, then `NameCaptionValueListModel` rows (song title / game caption / length), or an `EmptyStateListModel`. Title is the artist name. |
| `TracksForArtistAction.kt` | `sealed TracksForArtistAction : ChipboxAction()` — `PlayAllClicked`, `ShuffleAllClicked`, `TrackClicked(position: Int)`. |

## Why depend on this module

Depend on `:api` when you need to navigate to this screen without pulling in its
implementation. The artist-detail screen depends on `:api` so its "View all N
songs" CTA can `emit(NavigateTo(TracksForArtist(artistId, artistName)))`. The app
wires the actual screen by including `:real`; `:api` stays small and multiplatform
so any feature can reference the route key cheaply.

## Using it

```kotlin
// From another feature's ViewModel, open the full song list for an artist:
emit(NavigateTo(TracksForArtist(artistId = 42L, artistName = "Nobuo Uematsu")))
```

## Module facts

- **Plugin:** `chipbox.plugins.feature.api`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** the public dependencies of the State/Action types, declared in `build.gradle.kts`; `kotlinx.serialization` comes from the convention plugin
