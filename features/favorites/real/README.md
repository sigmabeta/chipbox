# `:features:favorites:real`

> The Favorites screen — favorited tracks, games, and artists in one staggered list.

The `:real` half of the Favorites feature screen: the `ChipboxListViewModel`, its
`State`/`Action` value types, and the Compose `Route`. It joins each favorite-id
stream with the full library, hydrates the matching models in favorite-recency
order, and renders them as a tracks section plus horizontally-scrolling games and
artists sections (or a single empty state when nothing is favorited). The app
includes this module to show the screen; other features should depend on
`:features:favorites:api` for the route key instead.

## Contents

| File | What it is |
| --- | --- |
| `FavoritesState.kt` | `FavoritesState : ListState`. Holds `LCE` lists of tracks/games/artists plus `playingTrackId`. `columnType` is `Staggered`; `toListItems` builds a tracks `SectionHeader` + `NameCaptionValueListModel` rows and games/artists `HorizontalScrollerListModel`s of `WideItemListModel`s, collapsing to one `EmptyStateListModel` when all three sections have loaded empty. |
| `FavoritesAction.kt` | `sealed class FavoritesAction : ChipboxAction()` — `TrackClicked(position)`, `GameClicked(id)`, `ArtistClicked(id)`. |
| `FavoritesViewModel.kt` | `@ContributesIntoMap(AppScope) @ViewModelKey @Inject` VM. Combines `FavoritesRepository` id streams with `Repository` library lists (and `Director.metadataState()` for the playing track), hydrates them, and handles actions: track tap starts a `SessionType.FAVORITES` session via `Director`; game/artist taps `NavigateTo(GameDetail/ArtistDetail)`. |
| `FavoritesRoute.kt` | `@Composable FavoritesRoute(onEvent, modifier)` — resolves the VM with `metroViewModel()` and hands it to `ChipboxListEntry`. |

## Why depend on this module

The app (`apps/android`, `apps/jvm`) depends on `:real` so the screen's VM is
contributed to the Metro `AppScope` graph and its `Route` is available to
`ChipboxScreens.kt`. Don't depend on `:real` to merely navigate here — depend on
`:features:favorites:api` for the route key and stay off this module's transitive
graph (repository, favorites store, director, game/artist-detail apis).

## Using it

```kotlin
// Registered in ChipboxScreens.kt — the screen wraps the Route:
FavoritesRoute(onEvent = LocalChipboxEventSink.current)

// The VM is auto-contributed via @ContributesIntoMap(AppScope) + @ViewModelKey;
// the Route resolves it through metroViewModel<FavoritesViewModel>().
```

## Module facts

- **Plugin:** `chipbox.feature.real` + `chipbox.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js` (the `feature.real` plugin transitively applies `sage.kmp.js`)
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `api` → `:features:favorites:api`; `implementation` → `:cbox:common:ui:list:api`, `:cbox:common:appcomm:api`, `:cbox:common:strings:api`, `:cbox:common:models:api`, `:cbox:common:repository:api`, `:cbox:common:favorites:api`, `:cbox:common:player:common:api`, `:cbox:common:player:director:api`, `:features:game-detail:api`, `:features:artist-detail:api`
