# `:features:browse-all-tracks:api`

> Route key for the "Browse all tracks" library screen.

The public contract for the browse-all-tracks feature: a single, parameterless
Voyager route key. As the `:api` module it carries the navigation type and the screen's State/Action types, so
other features (e.g. the Library tab) can route to this screen without depending
on its implementation.

## Contents

| File | What it is |
| --- | --- |
| `BrowseAllTracks.kt` | `@Serializable data object BrowseAllTracks` — a parameterless Chipbox screen destination (Voyager route key). |
| `BrowseAllTracksAction.kt` | `sealed BrowseAllTracksAction : ChipboxAction()` — `TrackClicked(position: Int)` and `ShuffleAllClicked`. |
| `BrowseAllTracksState.kt` | `BrowseAllTracksState : ListState()` — single-column (`ColumnType.One`), `PaginationType.Standard()`; holds `LCE<List<Track>>`, `playingTrackId`, and the sliding window (`windowStart`, `hasMoreBefore/After`, `loadingPrevious/More`). Renders a shuffle-all CTA up front, inline header/footer paging spinners, `NameCaptionValue` track rows (click position = absolute catalog index `windowStart + index`), and an empty state when loaded-empty. |

## Why depend on this module

Depend on `:api` when you need to navigate to the browse-all-tracks screen —
push the `BrowseAllTracks` route key. It's a leaf with no SAGE/module
dependencies, so navigating to the screen doesn't pull in the repository,
player, or UI stack. The app wires the screen itself via `:real` (registered in
`ChipboxScreens.kt`).

## Using it

```kotlin
// From another feature, navigate to the all-tracks list:
navigator.push(BrowseAllTracks)
```

## Module facts

- **Plugin:** `chipbox.plugins.feature.api`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** the public dependencies of the State/Action types, declared in `build.gradle.kts`; `kotlinx.serialization` comes from the convention plugin
