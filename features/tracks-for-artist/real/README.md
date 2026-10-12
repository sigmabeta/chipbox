# `:features:tracks-for-artist:real`

> The "all songs by an artist" screen — a flat, lazily-scrolling track list.

The implementation of the tracks-for-artist feature: a `ChipboxListViewModel`,
its `ListState`, the action vocabulary, and the composable route entry point. This
is the "View all songs" destination that a prolific composer's artist-detail screen
links to, because the artist-detail song section renders as a single non-lazy
column (bounded to a preview) and cannot hold hundreds of rows without freezing the
main thread.

## Contents

| File | What it is |
| --- | --- |
| `TracksForArtistState.kt` | `TracksForArtistState : ListState()` — `artistName`, an `LCE<List<Track>>`, and `playingTrackId`. `columnType` is `One` (a plain lazy `ListScreen`, so length is unbounded). Shows play-all + shuffle-all CTAs up front, then `NameCaptionValueListModel` rows (song title / game caption / length), or an `EmptyStateListModel`. Title is the artist name. |
| `TracksForArtistAction.kt` | `sealed TracksForArtistAction : ChipboxAction()` — `PlayAllClicked`, `ShuffleAllClicked`, `TrackClicked(position: Int)`. |
| `TracksForArtistViewModel.kt` | `@AssistedInject ChipboxListViewModel<TracksForArtistState>` — takes the `@Assisted artistId` + `artistName` plus `Repository`, `Director`, `StringProvider`, `Hatchet`. Loads via `repository.getTracksForArtist(artistId, withGame = true, withArtists = false)`; maps a throw to `LCE.Error`. Play/shuffle/tap start an `ARTIST` session through the `Director` (`contentId = artistId`). Nested `@AssistedFactory` is `@ContributesIntoMap(AppScope::class)`. |
| `TracksForArtistRoute.kt` | `@Composable TracksForArtistRoute(artistId, artistName, onEvent, modifier)` — resolves the VM via `assistedMetroViewModel { create(artistId, artistName) }` and hands it to `ChipboxListEntry`. |
| `TracksForArtistViewModelTest.kt` (`commonTest`) | Covers title seeding, load result mapping (content/empty/error), and session-start actions over fakes. |

## Why depend on this module

The app depends on `:real` to make the screen exist; it is registered in
`ChipboxScreens.kt` as `TracksForArtistDeepScreen(artistId, artistName)` under the
key `"TracksForArtist:$artistId"`. Because each pushed screen gets its own
`ViewModelStore`, opening a second artist's list opens a fresh load. Other features
should depend on `:api` (the route key), not `:real`. Today the only caller is
artist-detail.

## Using it

```kotlin
// Host the screen for an artist:
TracksForArtistRoute(artistId = 42L, artistName = "Nobuo Uematsu", onEvent = onEvent)

// Navigate here from elsewhere (route key lives in :api):
emit(NavigateTo(TracksForArtist(artistId = 42L, artistName = "Nobuo Uematsu")))
```

## Module facts

- **Plugin:** `chipbox.plugins.feature.real` + `chipbox.plugins.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (+ `commonTest`)
- **SAGE/module dependencies:** `api(:features:tracks-for-artist:api)`;
  `:cbox:common:ui:list:api`, `:cbox:common:appcomm:api`,
  `:cbox:common:strings:api`, `:cbox:common:repository:api`,
  `:cbox:common:models:api`, `:cbox:common:player:common:api`,
  `:cbox:common:player:director:api`.
  Test: `:cbox:common:repository:fake`, `:cbox:common:player:director:fake`.
