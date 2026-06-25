# `:features:home:real`

> The Home screen implementation — a modular, card-stacked landing tab assembled from independent `HomeModule`s.

The `:real` half of the Home feature: the `ChipboxListViewModel`, its
`State`/`Action`, the Compose `Route`, the pluggable `HomeModule` abstraction
with its concrete card modules, and the Metro DI bindings that wire them in. The
app includes this module to actually render Home; it is the only module that
depends on the content/playback/scanner repositories. The screen is a list of
sections, each owned by a `HomeModule` that emits its own `LCE<HomeModuleSection>`
flow — the ViewModel patches each slot independently as data arrives, so a new
row is a single-file change plus one DI `@Binds @IntoSet`.

## Contents

This is a large module; files are grouped by role.

- **Screen scaffolding** — `HomeViewModel.kt`
  (`@ContributesIntoMap(AppScope)` + `@ViewModelKey`, injects `Set<HomeModule>` +
  `Repository`/`Director`/`LibrarySource`/`Scanner`; collects every module's flow,
  drives the first-run empty state off `repository.getAllTracks(limit = 1)`, and
  routes clicks/random picks/playback toggles in `handleAction`), `HomeState.kt`
  (`HomeState : ListState` + `HomeSectionState`; builds section rows via
  `withStandardErrorAndLoading`, wraps multi-item sections in a
  `HorizontalScrollerListModel`, and renders the empty-state CTAs), `HomeAction.kt`
  (`sealed class HomeAction : ChipboxAction()` — game/artist/song clicks, the
  random "RNG" actions, folder/rescan CTAs, and now-playing card events),
  `HomeRoute.kt` (`HomeRoute(onEvent, modifier)` → `metroViewModel()` →
  `ChipboxListEntry`).
- **Module abstraction** — `module/HomeModule.kt`: the `HomeModule` interface
  (`id`, `priority`, `showHeader`, `state(): Flow<LCE<HomeModuleSection>>`) and
  the `HomeModuleSection` row type (title + pre-built `ListModel`s).
- **Card modules** — `modules/*HomeModule.kt`, one `HomeModule` per Home row,
  ordered by `priority`: `NowPlayingHomeModule` (0, headerless hero card),
  `ScanStatusHomeModule` (50, live scan-progress card off the `Scanner` flows),
  `GameOfTheDayHomeModule` (100), `RecentlyPlayedGamesHomeModule` (200),
  `MostPlayedSongsHomeModule` (300), `MostPlayedGamesHomeModule`,
  `MostPlayedArtistsHomeModule` (500), and `RngTakeTheWheelHomeModule` (1000,
  the random-pick tiles).
- **DI bindings** — `di/HomeModuleBindings.kt`:
  `@ContributesTo(AppScope)` interface that `@Multibinds` the `Set<HomeModule>`
  and `@Binds @IntoSet` each concrete module into it.
- **Tests** (`commonTest`) — `HomeViewModelTest.kt`,
  `modules/RngTakeTheWheelHomeModuleTest.kt`, `modules/ScanStatusHomeModuleTest.kt`,
  driven over the repository/history/scanner/director fakes.

## Why depend on this module

The app (`apps/android`, `apps/jvm`) depends on `:features:home:real` so the Home
screen and its ViewModel get contributed into the `AppScope` graph and can be
rendered by `ChipboxScreens.screenFor(Home)`. To merely *navigate* to Home,
depend on `:features:home:api` instead — `:real` `api`-exposes `:api`, so it pulls
the route key along transitively. Adding a new Home card means adding a
`HomeModule` here and binding it in `HomeModuleBindings`; nothing else needs to
change.

## Using it

```kotlin
// A new Home row: implement HomeModule, then bind it into the set.
class FavoritesHomeModule @Inject constructor(
    private val repository: Repository,
    private val stringProvider: StringProvider,
) : HomeModule {
    override val id = "favorites"
    override val priority = 250 // renders between recently-played (200) and most-played-songs (300)

    override fun state(): Flow<LCE<HomeModuleSection>> =
        repository.getFavorites().map { /* … */ LCE.Content(HomeModuleSection(title, items)) }
}

// In HomeModuleBindings:
@Binds @IntoSet
fun bindFavoritesModule(impl: FavoritesHomeModule): HomeModule
```

```kotlin
// Rendering the screen (done by ChipboxScreens):
@Composable
fun HomeRoute(onEvent: (ChipboxEvent) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: HomeViewModel = metroViewModel()
    ChipboxListEntry(viewModel, onEvent, modifier)
}
```

## Module facts

- **Plugin:** `chipbox.feature.real` + `chipbox.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js` (the
  `feature.real` plugin transitively applies `sage.kmp.js`).
- **Source set:** `commonMain` (impl) + `commonTest` (tests).
- **SAGE/module dependencies:** `api`-exposes `:features:home:api`; implements
  against `cbox/common` `ui.components.api`, `ui.list.api`, `appcomm.api`,
  `strings.api`, `repository.api`, `history.api`, `models.api`, `scanner.api`,
  `contentsource.api`, `player.common.api`, `player.director.api`; and the
  `:api`s of the `artist-detail`, `folder-picker`, `game-detail`, `now-playing`,
  and `rescan-status` features (navigation targets). Tests use the
  repository/history/contentsource/scanner/director `:fake`s.
