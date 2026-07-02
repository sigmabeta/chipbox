# `:features:games-for-platform:real`

> The screen that lists every game for one sound-chip platform as a cover grid.

The implementation of the games-for-platform feature: a `ChipboxListViewModel`,
its `ListState`, the action vocabulary, and the composable route entry point. As
a `:real` module this is what the app includes to actually render the screen
behind the `:api` route key.

## Contents

| File | What it is |
| --- | --- |
| `GamesForPlatformState.kt` | `GamesForPlatformState : ListState()` holding the `Platform?` and an `LCE<List<Game>>`. `columnType` is `Regular(160dp)` (a cover grid); title is "Games for &lt;Platform&gt;"; renders play-all + shuffle-all `CtaListModel` CTAs followed by `GridImageListModel` covers at a 3:4 (`0.75`) aspect ratio, or an `EmptyStateListModel` when there are no games. |
| `GamesForPlatformAction.kt` | `sealed GamesForPlatformAction : ChipboxAction()` — `PlayAllClicked`, `ShuffleAllClicked`, `GameClicked(id: Long)`. |
| `GamesForPlatformViewModel.kt` | `@AssistedInject` VM extending `ChipboxListViewModel`; takes the `@Assisted platform` plus `Repository`, `Director`, `StringProvider`, `Hatchet`. Loads via `repository.getGamesForPlatform(platform)`; on play/shuffle-all starts a `PLATFORM` session through the `Director` (`contentId = platform.ordinal`), and on a game tap navigates to `GameDetail`. Nested `@AssistedFactory` is `@ContributesIntoMap(AppScope::class)`. |
| `GamesForPlatformRoute.kt` | `@Composable GamesForPlatformRoute(platform, onEvent, modifier)` — resolves the VM via `assistedMetroViewModel { create(platform) }` and hands it to `ChipboxListEntry`. |
| `GamesForPlatformViewModelTest.kt` (`commonTest`) | Covers state seeding, `Data` → `LCE` mapping, and navigation/session actions over fakes. |

## Why depend on this module

The app depends on `:real` to make the screen exist; it is registered in
`ChipboxScreens.kt` as `GamesForPlatformDeepScreen(platform)`. Because each pushed
screen gets its own `ViewModelStore`, pushing `GamesForPlatform(DREAMCAST)` and
then `GamesForPlatform(GENESIS)` does not reuse the Dreamcast VM — each platform
gets a fresh load. Other features should depend on `:api` (the route key), not
`:real`.

## Using it

```kotlin
// Host the screen for a platform:
GamesForPlatformRoute(platform = Platform.SNES, onEvent = onEvent)

// Navigate here from elsewhere (route key lives in :api):
emit(NavigateTo(GamesForPlatform(platform)))
```

## Module facts

- **Plugin:** `chipbox.plugins.feature.real` + `chipbox.plugins.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (+ `commonTest`)
- **SAGE/module dependencies:** `api(:features:games-for-platform:api)`;
  `:cbox:common:ui:list:api`, `:cbox:common:appcomm:api`,
  `:cbox:common:strings:api`, `:cbox:common:repository:api`,
  `:cbox:common:models:api`, `:cbox:common:player:common:api`,
  `:cbox:common:player:director:api`, `:features:game-detail:api`.
  Test: `:cbox:common:repository:fake`, `:cbox:common:player:director:fake`.
