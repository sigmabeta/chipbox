# `:features:browse-by-game:real`

> The Browse-by-Game screen — a paginated grid of game covers.

The `:real` implementation of the browse-by-game feature: a `ChipboxListEntry`
backed by a `ChipboxListViewModel` that pages the full game catalog into a cover
grid and navigates to `GameDetail` on tap. This is a pure browse surface — it
only reads games and navigates, with no playback or favorites wiring. The app
includes `:real` to actually render the screen (`:api` is just the route key).

## Contents

| File | What it is |
| --- | --- |
| `BrowseByGameAction.kt` | `sealed BrowseByGameAction : ChipboxAction()` with one case, `GameClicked(id: Long)` — the only user action. |
| `BrowseByGameState.kt` | `BrowseByGameState : ListState()`. A 160dp `ColumnType.Regular` cover grid with `PaginationType.Standard()`. Holds `LCE<List<Game>>` plus a sliding paging window (`windowStart`, `hasMoreBefore`/`hasMoreAfter`, `loadingPrevious`/`loadingMore`); renders inline header/footer paging spinners and `GridImageListModel` covers at 3:4 aspect ratio, with an empty state when loaded-empty. |
| `BrowseByGameViewModel.kt` | `BrowseByGameViewModel : ChipboxListViewModel<BrowseByGameState>`. `@Inject` + `@ContributesIntoMap(AppScope, binding<ViewModel>())` + `@ViewModelKey`; ctor takes `Repository` + `StringProvider` + `Hatchet` (no route args, no `Director`). Windowed pagination over `repository.getAllGames(limit, offset)` with a single in-flight `pageJob`; `loadInitial`/`loadNextPage`/`loadPreviousPage` reducers. `handleAction` navigates to `GameDetail` on tap and handles `SageAction.LoadMoreRequested`/`LoadPreviousRequested` plus a test-only `InitWithPageNumber` seam. |
| `BrowseByGameRoute.kt` | `@Composable BrowseByGameRoute(onEvent, modifier)` — resolves the VM via `metroViewModel()` and renders it through `ChipboxListEntry`. |
| `BrowseByGameViewModelTest.kt` | `commonTest` coverage of LCE mapping, navigation, and the paging window (over `repository.fake`). |

## Why depend on this module

The app depends on `:real` to show the screen — it's registered in
`ChipboxScreens.kt` as `BrowseByGameScreen` (a parameterless `Screen` object) and
its plain, non-assisted `BrowseByGameViewModel` is contributed into the `AppScope`
map. To merely navigate *to* the screen, depend on `:api` for the `BrowseByGame`
route key instead; the app wires `:real` so Voyager can resolve it.

## Using it

```kotlin
// Inside ChipboxScreens.kt, mapped from the BrowseByGame route key:
BrowseByGameRoute(onEvent = onEvent)
// Tapping a game cover emits NavigateTo(GameDetail(id)).
```

## Module facts

- **Plugin:** `chipbox.plugins.feature.real` + `chipbox.plugins.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain` (+ `commonTest`)
- **SAGE/module dependencies:** `api` → `:features:browse-by-game:api`;
  `implementation` → `:cbox:common:ui:list:api`, `:cbox:common:appcomm:api`,
  `:cbox:common:strings:api`, `:cbox:common:repository:api`,
  `:cbox:common:models:api`, `:features:game-detail:api`; `commonTest` →
  `:cbox:common:repository:fake`
