# `:features:playlist-detail:real`

> The playlist-detail screen: view, play, and edit a single playlist.

> The screen's `State`/`Action` value types live in `:features:playlist-detail:api`; this module holds the ViewModel, Route and runtime helpers.

The `:real` implementation of the playlist-detail feature — `ViewModel` and the Compose route content. Renders a single playlist's tracks with
play/shuffle CTAs in view mode and inline drag-reorder, remove, rename, and delete
in edit mode. The app includes this module; `cbox/common/appui/api/.../ChipboxScreens.kt`
maps the `:api` route key to it via `screenFor()`.

## Contents

| File | What it is |
| --- | --- |
| `PlaylistDetailRoute.kt` | `@Composable PlaylistDetailRoute(playlistId, onEvent, modifier)` — resolves the VM via `assistedMetroViewModel` and renders through `ChipboxReorderableEntry`. |
| `PlaylistDetailViewModel.kt` | `ChipboxListViewModel<PlaylistDetailState>` with `@AssistedInject` (the `playlistId` route arg is `@Assisted`); reduces actions, hydrates tracks, drives the `Director`. Inner `Factory` is `@ContributesIntoMap(AppScope::class)`. |

## Why depend on this module

You normally don't depend on `:real` directly — the app graph includes it so its
`@ContributesIntoMap` VM factory and the route registration are wired up. To
*navigate* to this screen, depend on `:features:playlist-detail:api` instead. Note
the assisted-inject split: the `playlistId` route arg is passed as an `@Assisted`
value to the VM factory (no `SavedStateHandle`), matching the Voyager + Metro
convention used by other id-bearing detail screens.

## Using it

```kotlin
// How ChipboxScreens.kt maps the :api route key to this screen's content:
is PlaylistDetail -> PlaylistDetailDeepScreen(destination.id)

// ...where the Screen renders the route, supplying the assisted playlistId:
PlaylistDetailRoute(playlistId = playlistId, onEvent = LocalChipboxEventSink.current)
```

## Module facts

- **Plugin:** `chipbox.feature.real` + `chipbox.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (impl), `commonTest` (`PlaylistDetailViewModelTest`)
- **SAGE/module dependencies:** `api(:features:playlist-detail:api)`; `implementation` of `:cbox:common:ui:list:api`, `:cbox:common:ui:components:api`, `:cbox:common:appcomm:api`, `:cbox:common:strings:api`, `:cbox:common:models:api`, `:cbox:common:repository:api`, `:cbox:common:playlists:api`, `:cbox:common:player:common:api`, `:cbox:common:player:director:api`. Test: `:cbox:common:repository:fake`, `:cbox:common:playlists:fake`, `:cbox:common:player:director:fake`.
