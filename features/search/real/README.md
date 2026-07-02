# `:features:search:real`

> The search screen: a debounced live-query ViewModel plus a custom Compose screen with an in-screen search bar over a results grid.

The `:real` half of the search feature — the implementation the app includes to
actually show the screen. It holds `SearchViewModel`
(`@ContributesIntoMap(AppScope)` + `@ViewModelKey` + `@Inject`), the
`SearchState`/`SearchAction` types, and the Compose UI. Unlike a stock
list-screen feature, search **does not** route through `ChipboxListEntry` /
`ListScreen` — it renders its own `SearchContent` (a `LazyVerticalGrid` of SAGE
`ListModel`s) with `SearchBar` and a status-bar scrim overlaid on top, so the
usual `*Route → ChipboxListEntry` quartet is replaced by `SearchRoute` +
`SearchContent` + `SearchBar`.

## Contents

| File | What it is |
| --- | --- |
| `SearchState.kt` | `SearchState : ListState`. Holds the live `query`, the debounced `submittedQuery`, and per-category `LCE` results (`gameResults`/`songResults`/`artistResults`) plus `history`. `toListItems` shows recent searches (with an empty-state prompt when sparse) when no query is submitted, otherwise game/song/artist sections built via `withStandardErrorAndLoading`; `title()` returns an empty `TitleBarModel` since the top bar is hidden. |
| `SearchAction.kt` | `sealed class SearchAction : ChipboxAction()` — `QueryChanged`, `HistoryClicked`, `HistoryRemoved`, `GameClicked`, `SongClicked`, `ArtistClicked`, `BackClicked`. |
| `SearchViewModel.kt` | `ChipboxListViewModel<SearchState>`. Debounces typing (300ms, min length 3) into `submittedQuery`, runs `searchGames/Songs/Artists` in parallel via `flatMapLatest`, and records history only after a 3s linger window when results exist. Handles navigation (game/artist detail), plays song results as a `director` setlist, and removes history entries. |
| `SearchRoute.kt` | `SearchRoute(onEvent, modifier)` — resolves the VM via `metroViewModel()`, hides the app top bar (`ChipboxEvent.RequestTopBarVisibility(false)`), collects `events`, and feeds `uiStateActual.listItems` + raw `query` + `showDebug` into `SearchContent`. |
| `SearchContent.kt` | The custom screen: a `LazyVerticalGrid` (`GridCells.Adaptive(160.dp)`) of `ListModel`s, with `SearchBar` and a top scrim overlaid in a `Box`. Auto-scrolls to top when the query changes. |
| `SearchBar.kt` | The in-screen search field: a `BasicTextField` with back + clear `MenuActionIcon`s and an animated hint. Auto-focuses at runtime (skipped under `LocalInspectionMode` to avoid a Paparazzi/Preview IME crash). Drives `SearchAction.QueryChanged`/`BackClicked`. |
| `SearchViewModelTest.kt` | `commonTest` coverage of the debounce/submit pipeline, `Data → LCE` mapping, the 3s history-record linger (and its cancel-on-new-query), and every action handler. Layers minimal overrides on `FakeRepository`/`FakeDirector`. |

## Why depend on this module

The app (`apps/android`, `apps/jvm`) depends on `:features:search:real` so the
search route can actually be shown; `SearchViewModel` contributes itself into the
`AppScope` ViewModel map via Metro, and `SearchRoute` is invoked from
`ChipboxScreens.kt`. Navigation-only callers should depend on
`:features:search:api` (the route key) instead — this module `api`-exposes that
module transitively. The `:screenshot` companion depends on this module for its
`SearchContent`/`SearchState` previews.

## Using it

```kotlin
// In ChipboxScreens.kt, the Search route key maps to a Screen that renders:
@Composable
fun SearchRoute(onEvent: (ChipboxEvent) -> Unit, modifier: Modifier = Modifier)

// The screen drives everything through SearchAction, e.g. the search box:
actionSink.sendAction(SearchAction.QueryChanged(newText))
// ...which the VM debounces (300ms, min 3 chars) into submittedQuery,
// then runs game/song/artist searches and records history after a 3s linger.
```

## Module facts

- **Plugin:** `chipbox.feature.real` + `chipbox.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js` (the `feature.real` plugin transitively applies `sage.kmp.js`).
- **Source set:** `commonMain` (impl), `commonTest` (`SearchViewModelTest`)
- **SAGE/module dependencies:** `api`-exposes `:features:search:api`; `implementation` of `:cbox:common:ui:list:api`, `:cbox:common:ui:components:api`, `:cbox:common:appcomm:api`, `:cbox:common:strings:api`, `:cbox:common:repository:api`, `:cbox:common:models:api`, `:cbox:common:player:common:api`, `:cbox:common:player:director:api`, `:features:game-detail:api`, `:features:artist-detail:api`, plus SAGE `appcomm`/`images`/`ui-components`/`ui-strings` and Compose material-icons-extended. Tests use `:cbox:common:repository:fake` and `:cbox:common:player:director:fake`.
