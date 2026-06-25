# `:features:now-playing:real`

> The full-screen player: artwork, transport, seek bar, inline setlist, context menus, and error log.

The screen implementation behind the `NowPlaying` route. This is the feature's
`:real` module: it provides a `ChipboxFreeformViewModel` (`@ContributesIntoMap` +
`@ViewModelKey` + `@Inject`) that drives the player off the `Director`, plus the
Compose UI it renders. The app includes this module; `ChipboxScreens.kt` maps
the `NowPlaying` key to `NowPlayingRoute`.

## Contents

**State types** — `NowPlayingState.kt` (`NowPlayingState`, the `FreeformState`
that maps `Director`/`Repository`/`FavoritesRepository` data to a
`NowPlayingModel`, plus `SetlistRowData`); `NowPlayingModel.kt` (the immutable UI
model the composables read, plus `NowPlayingError`, `ContextMenuMode`,
`NowPlayingArtist`, `NowPlayingTag`/`NowPlayingTagKind`); `NowPlayingAction.kt`
(the `ChipboxAction` sealed hierarchy — transport taps, seek, context-menu and
setlist actions).

**ViewModel** — `NowPlayingViewModel.kt`: collects `Director` metadata/playback/
session/setlist/error flows and favorite state, translates `NowPlayingAction`s
into `SessionRequest`s, owns the error-log / context-menu auto-dismiss timers,
resolves setlist slot ids to `Track` metadata, and suggests a playlist name when
capturing the current setlist. Navigates away when playback goes `IDLE`/`STOPPED`.

**Composables** — `NowPlayingRoute.kt` (the `@Composable` entry point registered
in `ChipboxScreens.kt`; hides the host top bar + mini-player); `NowPlayingContent.kt`
(the responsive root — narrow/wide/compact layouts with shared-element
transitions between the info and setlist panes); `NowPlayingInfoPane.kt`,
`NowPlayingTransport.kt` (artwork/title, seek `ProgressSection`, transport row),
`NowPlayingContextMenu.kt` (the in-place LINKS/ARTISTS/CONTROLS/TAG menus),
`NowPlayingSetlistPane.kt` (the reorderable, swipe-to-remove inline queue),
`ErrorSection.kt` (the rolling error log), `NowPlayingDimens.kt` (shared
panel sizing + test tags).

## Why depend on this module

Only the app graph depends on `:real`, to bind the `NowPlayingViewModel` into the
ViewModel map and register `NowPlayingRoute`. Features that merely want to open
the player depend on `:api` for the `NowPlaying` route key instead — `:real`
pulls in the player, repository, favorites, and sage UI stack and should not be a
dependency of other feature screens. (`:real` itself depends on the `:api`
modules of `game-detail`, `games-for-platform`, `artist-detail`, and `playlists`
for its context-menu navigation targets.)

## Using it

```kotlin
// Registered in cbox/common/appui/api/.../ChipboxScreens.kt:
NowPlaying -> NowPlayingScreen   // a TablessScreen whose content() calls:

@Composable
fun NowPlayingRoute(onEvent: (ChipboxEvent) -> Unit, modifier: Modifier = Modifier)
```

The ViewModel is resolved with `metroViewModel<NowPlayingViewModel>()` inside the
route and wrapped by `ChipboxFreeformEntry`, which renders `NowPlayingContent`
from the emitted `NowPlayingModel` and forwards UI `NowPlayingAction`s back to it.

## Module facts

- **Plugin:** `chipbox.plugins.feature.real` + `chipbox.plugins.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain` (Compose UI + ViewModel); tests in `commonTest`
- **SAGE/module dependencies:** `:features:now-playing:api` (`api`); the `:api`
  modules of `game-detail`, `games-for-platform`, `artist-detail`, `playlists`;
  `cbox/common` `ui.freeform.api`, `ui.components.api`, `appcomm.api`,
  `strings.api`, `models.api`, `player.common.api`, `player.director.api`,
  `repository.api`, `favorites.api`; sage `appcomm`, `freeform`, `images`,
  `ui.components`, `ui.strings`, `ui.iconsReal`, and the `reorderable` library.
  Tests use the `player.director`, `repository`, and `favorites` `:fake`s.
