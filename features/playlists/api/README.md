# `:features:playlists:api`

> Voyager route key for the playlists list / "Add to Playlist" picker screen.

The `:api` half of the `playlists` feature: a single `@Serializable` destination type
that other modules depend on to navigate to the screen. It carries no UI or
implementation — the screen itself lives in `:features:playlists:real`.

## Contents

| File | What it is |
| --- | --- |
| `Playlists.kt` | The `@Serializable data class Playlists(pendingTrackIds, suggestedName)` route key. Empty `pendingTrackIds` = normal browse list; non-empty = "Add to Playlist" picker mode. `suggestedName` seeds the name of a playlist created from the picker. |
| `PlaylistsState.kt` | `PlaylistsState : ListState` — an `LCE<List<Playlist>>` plus an `isPicker` flag. Renders the always-present "New Playlist" CTA, then a loading/empty/error body or one `IconNameCaptionListModel` per playlist (name + track-count caption). Title switches between browse and "Add to Playlist" by `isPicker`. |
| `PlaylistsAction.kt` | `PlaylistsAction : ChipboxAction` — `PlaylistClicked(id)` and `NewPlaylistClicked`. |

## Why depend on this module

Depend on `:features:playlists:api` when you need to navigate to the playlists
screen (or launch the bulk "Add to Playlist" picker) without pulling in the screen's
implementation. Navigation callers see the route key and the screen's State/Action types; the app wires the
matching `Screen` from `:features:playlists:real` in
`cbox/common/appui/api/.../ChipboxScreens.kt`.

## Using it

```kotlin
// Open the browse list of playlists.
emit(NavigateTo(Playlists()))

// Launch the picker to add tracks to a playlist, seeding a created playlist's name.
emit(NavigateTo(Playlists(pendingTrackIds = trackIds, suggestedName = "From game Chrono Trigger")))
```

## Module facts

- **Plugin:** `chipbox.plugins.feature.api`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** the public dependencies of the State/Action types, declared in `build.gradle.kts`; `kotlinx.serialization` comes from the convention plugin
