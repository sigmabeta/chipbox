# `:features:browse-all-tracks:real`

> The "Browse all tracks" screen — a windowed, paginated list of the whole track catalog.

The implementation of the browse-all-tracks feature: a `ChipboxListViewModel`,
its `ListState`, the action type, and the Compose route. As the `:real` module
it's what the app actually includes to show the screen; the screen is a plain
(non-assisted) `ViewModel` contributed into the `AppScope` map and registered in
`ChipboxScreens.kt` as the parameterless `BrowseAllTracksScreen`.

## Contents

| File | What it is |
| --- | --- |
| `BrowseAllTracksAction.kt` | `sealed BrowseAllTracksAction : ChipboxAction()` — `TrackClicked(position: Int)` and `ShuffleAllClicked`. |
| `BrowseAllTracksState.kt` | `BrowseAllTracksState : ListState()` — single-column (`ColumnType.One`), `PaginationType.Standard()`; holds `LCE<List<Track>>`, `playingTrackId`, and the sliding window (`windowStart`, `hasMoreBefore/After`, `loadingPrevious/More`). Renders a shuffle-all CTA up front, inline header/footer paging spinners, `NameCaptionValue` track rows (click position = absolute catalog index `windowStart + index`), and an empty state when loaded-empty. |
| `BrowseAllTracksViewModel.kt` | `@Inject @ContributesIntoMap(AppScope, binding<ViewModel>()) @ViewModelKey` plain `ChipboxListViewModel<BrowseAllTracksState>`. Windowed pagination via `repository.getAllTracks(withGame = true, limit, offset)` behind a single in-flight `pageJob`; `loadInitial`/`loadNextPage`/`loadPreviousPage` reducers; observes `director.metadataState()` for the playing track; `handleAction` starts an `ALL_TRACKS` session and handles `LoadMore`/`LoadPrevious`/`InitWithPageNumber`. No `@Assisted`/route args. |
| `BrowseAllTracksRoute.kt` | `@Composable BrowseAllTracksRoute(onEvent, modifier)` → `metroViewModel<BrowseAllTracksViewModel>()` → `ChipboxListEntry`. Plain `metroViewModel` since there are no route args. |
| `BrowseAllTracksViewModelTest.kt` | (commonTest) Drives the ViewModel over `FakeRepository`/`FakeDirector` — paging, playback, and the `InitWithPageNumber` prepend seam. |

## Why depend on this module

The app includes `:real` to register and show the screen. Other code that only
needs to navigate here should depend on `:api` for the `BrowseAllTracks` route
key instead — `:real` is wired into the app graph automatically via
`@ContributesIntoMap`, so you rarely reference its types directly.

## Using it

```kotlin
// Inside ChipboxScreens.kt, the parameterless BrowseAllTracksScreen renders:
BrowseAllTracksRoute(onEvent = onEvent)

// To reach the screen, push the route key from :api:
navigator.push(BrowseAllTracks)
```

## Module facts

- **Plugin:** `chipbox.plugins.feature.real` + `chipbox.plugins.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (+ `commonTest`)
- **SAGE/module dependencies:** `api` → `:features:browse-all-tracks:api`;
  `implementation` → `:cbox:common:ui:list:api`, `:cbox:common:appcomm:api`,
  `:cbox:common:strings:api`, `:cbox:common:repository:api`,
  `:cbox:common:models:api`, `:cbox:common:player:common:api`,
  `:cbox:common:player:director:api`. Tests: `:cbox:common:repository:fake`,
  `:cbox:common:player:director:fake`.
