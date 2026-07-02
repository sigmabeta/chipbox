# `:features:game-detail:real`

> The game-detail screen: hero art, play/shuffle/favorite/playlist CTAs, a details section, an artist scroller, and the song list.

The implementation behind the `GameDetail` route. As a `:real` module it provides
the `ChipboxListViewModel`/`ListState` and the Compose entry point that render a
single game — its cover, descriptive metadata, contributing artists, and tracks —
and that drive playback and navigation. The app includes `:real` to show the screen;
other features depend on `:api` to reach it.

## Contents

| File | What it is |
| --- | --- |
| `GameDetailAction.kt` | Sealed `GameDetailAction : ChipboxAction()`: `TrackClicked(position)`, `ArtistClicked(id)`, `PlayAllClicked`, `ShuffleAllClicked`, `AddToFavoritesClicked`, `AddToPlaylistClicked`. |
| `GameDetailState.kt` | `GameDetailState : ListState()` holding `LCE<Game>`/`LCE<List<Track>>`/`LCE<List<Artist>>` plus `playingTrackId`, `isFavorite`, `notFound`. Builds the hero image, CTA rows, a read-only "Details" section of `LabelValue` rows (release date / genre / copyright / Japanese title — only present fields render; section hidden when none), an artists `HorizontalScroller`, and the songs section. Songs collapse to label/value rows for a single-artist game, name+caption rows when multiple; shows an empty state on `notFound`. Column type `Staggered(320dp)`. |
| `GameDetailViewModel.kt` | `@AssistedInject GameDetailViewModel : ChipboxListViewModel<GameDetailState>` with `@Assisted gameId: Long`, plus `Repository`, `Director`, `FavoritesRepository`, `StringProvider`, `Hatchet`. Loads the game with tracks+artists, observes `director.metadataState()` and `favorites.isGameFavorite`; `handleAction` starts `GAME` playback sessions, toggles favorite, and navigates to `ArtistDetail` / the `Playlists` picker. Nested `@AssistedFactory @ContributesIntoMap(AppScope) Factory`. |
| `GameDetailRoute.kt` | `@Composable GameDetailRoute(gameId, onEvent, modifier)` — resolves the VM via `assistedMetroViewModel<…, Factory> { create(gameId) }` and renders `ChipboxListEntry`. |
| `GameDetailViewModelTest.kt` | `commonTest`: covers the Data→LCE fan-out across the three slots, `notFound` semantics, `playingTrackId`, and action routing using `repository.fake` / `favorites.fake` / `player.director.fake`. |

The `gameId` is taken as `@Assisted` directly because the AndroidX-nav
`SavedStateHandle` was removed; Voyager pushes typed `Screen` instances, so the
screen hands `gameId` to the `Factory` explicitly.

## Why depend on this module

The app depends on `:real` to register and display the game-detail screen — it is
wired in `ChipboxScreens.kt` as `GameDetailDeepScreen(gameId)` with a distinct key
(`"GameDetail:$gameId"`) and a per-screen `ViewModelStore`, so navigating to
different game ids doesn't reuse a cached VM. Other features should depend on
`:features:game-detail:api` for the route key instead.

## Using it

```kotlin
// Render the screen (the app's ChipboxScreens wiring calls this):
GameDetailRoute(gameId = 42L, onEvent = onEvent)

// Reach it from another feature:
emit(NavigateTo(GameDetail(id)))
```

## Module facts

- **Plugin:** `chipbox.plugins.feature.real` + `chipbox.plugins.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (+ `commonTest`)
- **SAGE/module dependencies:** `api` `:features:game-detail:api`; `implementation` `:cbox:common:ui:list:api`, `:cbox:common:appcomm:api`, `:cbox:common:strings:api`, `:cbox:common:repository:api`, `:cbox:common:favorites:api`, `:cbox:common:models:api`, `:cbox:common:player:common:api`, `:cbox:common:player:director:api`, `:features:artist-detail:api`, `:features:playlists:api`. `commonTest`: `:cbox:common:repository:fake`, `:cbox:common:favorites:fake`, `:cbox:common:player:director:fake`.
