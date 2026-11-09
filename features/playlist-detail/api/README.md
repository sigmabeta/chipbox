# `:features:playlist-detail:api`

> The route key for navigating to a single playlist's contents.

The public, navigate-here surface of the playlist-detail feature: a `@Serializable`
Voyager destination carrying the playlist id. This is the `:api` half of the
feature — depend on it to navigate to the screen without pulling in the screen
implementation.

## Contents

| File | What it is |
| --- | --- |
| `PlaylistDetail.kt` | `@Serializable data class PlaylistDetail(val id: Long)` — the route key. The `id` is the playlist to open. |
| `PlaylistDetailState.kt` | `ListState` subclass: the LCE-wrapped playlist/tracks plus edit/rename/delete flags, and the `toListItems` reducer that builds the row models for each mode. |
| `PlaylistDetailAction.kt` | `sealed class PlaylistDetailAction : ChipboxAction()` — Play All, Shuffle, TrackClicked, Edit/Done, Rename, Delete, TrackRemoved. |

## Why depend on this module

Depend on `:features:playlist-detail:api` from any feature that needs to navigate
to a playlist's detail screen (e.g. a playlist list emitting a navigate event).
It is the lightweight contract: the route key and the screen's State/Action types. The screen itself, its
`ViewModel` and Compose content live in `:features:playlist-detail:real`,
which the app includes and which maps this key to a Voyager `Screen` in
`cbox/common/appui/api/.../ChipboxScreens.kt`.

## Using it

```kotlin
// Navigate to a playlist's detail screen by id.
onEvent(ChipboxEvent.Navigate(PlaylistDetail(id = playlist.id)))
```

## Module facts

- **Plugin:** `chipbox.feature.api`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** the public dependencies of the State/Action types, declared in `build.gradle.kts`; `kotlinx.serialization` comes from the convention plugin
