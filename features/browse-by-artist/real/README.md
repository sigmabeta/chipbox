# `:features:browse-by-artist:real`

> The Browse-by-Artist screen: a paged grid of artist photos that navigates to Artist Detail.

> The screen's `State`/`Action` value types live in `:features:browse-by-artist:api`; this module holds the ViewModel, Route and runtime helpers.

The `:real` half of the feature — the screen implementation behind the
`BrowseByArtist` route key. It's a pure browse grid: a windowed, paginated list
of artists rendered as square cover tiles, whose only action is to open an
artist's detail page. The app includes `:real` to actually show the screen;
`cbox/common/appui` registers it in `ChipboxScreens.kt` as the parameterless
`BrowseByArtistScreen`.

## Contents

| File | What it is |
| --- | --- |
| `BrowseByArtistViewModel.kt` | `@Inject @ContributesIntoMap(AppScope, binding<ViewModel>()) @ViewModelKey` VM extending `ChipboxListViewModel<BrowseByArtistState>`. Constructor takes `Repository` + `StringProvider` + `Hatchet` — no route args, no `Director`. Windowed pagination via `repository.getAllArtists(limit, offset)` with a single in-flight `pageJob` and `loadInitial`/`loadNextPage`/`loadPreviousPage` reducers; `handleAction` navigates to `ArtistDetail` on tap and handles `LoadMoreRequested`/`LoadPreviousRequested` plus a test-only `InitWithPageNumber` seam. |
| `BrowseByArtistRoute.kt` | `@Composable BrowseByArtistRoute(onEvent, modifier)` → `metroViewModel<BrowseByArtistViewModel>()` → `ChipboxListEntry`. |
| `BrowseByArtistViewModelTest.kt` (commonTest) | Covers `Data` → `LCE` folding, click → `NavigateTo(ArtistDetail)`, and the forward/backward paging window, backed by `repository.fake`. |

## Why depend on this module

The app depends on `:real` to render the Browse-by-Artist screen; everything
else should depend on `:api` instead and navigate via the `BrowseByArtist` route
key. The VM is a plain, non-assisted binding contributed into the `AppScope`
map, so no manual wiring is needed beyond including the module. Tests depend on
`:cbox:common:repository:fake` to drive the catalog.

## Using it

```kotlin
// In ChipboxScreens.kt, BrowseByArtistScreen renders the route like so:
BrowseByArtistRoute(onEvent = onEvent)
```

Tapping a tile emits `NavigateTo(ArtistDetail(id))`, so the host needs the
`:features:artist-detail:api` route registered to land the navigation.

## Module facts

- **Plugin:** `chipbox.plugins.feature.real` + `chipbox.plugins.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (+ `commonTest`)
- **SAGE/module dependencies:** `api` → `:features:browse-by-artist:api`;
  `implementation` → `:cbox:common:ui:list:api`, `:cbox:common:appcomm:api`,
  `:cbox:common:strings:api`, `:cbox:common:repository:api`,
  `:cbox:common:models:api`, `:features:artist-detail:api`. Test:
  `:cbox:common:repository:fake`.
