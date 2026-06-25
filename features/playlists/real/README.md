# `:features:playlists:real`

> The playlists list screen: browse your playlists, create new ones, and pick a target for "Add to Playlist".

The `:real` half of the `playlists` feature — the actual screen. It supplies a
`ChipboxListViewModel`, its `ListState`/`Action` value types, and the Compose
`PlaylistsRoute` entry point. The app includes this module; the route is registered in
`cbox/common/appui/api/.../ChipboxScreens.kt` via `screenFor()`.

## Contents

| File | What it is |
| --- | --- |
| `PlaylistsState.kt` | `PlaylistsState : ListState` — an `LCE<List<Playlist>>` plus an `isPicker` flag. Renders the always-present "New Playlist" CTA, then a loading/empty/error body or one `IconNameCaptionListModel` per playlist (name + track-count caption). Title switches between browse and "Add to Playlist" by `isPicker`. |
| `PlaylistsAction.kt` | `PlaylistsAction : ChipboxAction` — `PlaylistClicked(id)` and `NewPlaylistClicked`. |
| `PlaylistsViewModel.kt` | `ChipboxListViewModel<PlaylistsState>` wired with Metro (`@AssistedInject` + `@ContributesIntoMap(AppScope::class)` factory). Collects `PlaylistsRepository.playlists()`; routes clicks to `PlaylistDetail` (browse) or appends tracks and navigates back (picker); creates playlists with a unique-default-name fallback (`uniqueDefaultName`). |
| `PlaylistsRoute.kt` | `@Composable PlaylistsRoute(pendingTrackIds, suggestedName, onEvent, modifier)` — resolves the assisted VM via `assistedMetroViewModel` and renders it through `ChipboxListEntry`. |

## Why depend on this module

The app graph depends on `:features:playlists:real` so the screen's ViewModel factory
is contributed to `AppScope` and `PlaylistsRoute` can be mounted. Navigation callers
should depend on `:features:playlists:api` (the route key) instead; only the app/UI
wiring needs `:real`.

## Using it

The route is mounted from `ChipboxScreens.kt`, passing the typed route args straight
into the assisted VM factory:

```kotlin
PlaylistsRoute(
    pendingTrackIds = destination.pendingTrackIds,
    suggestedName = destination.suggestedName,
    onEvent = onEvent,
)
```

In picker mode (`pendingTrackIds` non-empty), tapping an existing playlist appends the
tracks and pops back; tapping "New Playlist" creates one seeded with `suggestedName`,
adds the tracks, and backs out. In browse mode, "New Playlist" creates an empty
playlist and opens its `PlaylistDetail`.

## Module facts

- **Plugin:** `chipbox.plugins.feature.real` + `chipbox.plugins.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain` (impl), `commonTest` (`PlaylistsViewModelTest`)
- **SAGE/module dependencies:** `:features:playlists:api` (`api`),
  `:cbox:common:ui:list:api`, `:cbox:common:appcomm:api`, `:cbox:common:strings:api`,
  `:cbox:common:models:api`, `:cbox:common:playlists:api`,
  `:features:playlist-detail:api`; test: `:cbox:common:playlists:fake`
