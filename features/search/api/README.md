# `:features:search:api`

> The Search screen's Voyager route key — the destination other features push to open search.

The public surface of the search feature. It exports only the `@Serializable`
route key `Search`; this is the `:api` half of the feature-screen split, so any
module that needs to navigate to search can depend on it without pulling in the
screen implementation. The route key and the screen's `State`/`Action` types
live here; the ViewModel and Compose UI stay in `:real`.

## Contents

| File | What it is |
| --- | --- |
| `Search.kt` | `@Serializable data object Search` — the Voyager route key for the search screen. It's a `data object` because the screen takes no arguments. |
| `SearchState.kt` | `SearchState : ListState`. Holds the live `query`, the debounced `submittedQuery`, and per-category `LCE` results (`gameResults`/`songResults`/`artistResults`) plus `history`. `toListItems` shows recent searches (with an empty-state prompt when sparse) when no query is submitted, otherwise game/song/artist sections built via `withStandardErrorAndLoading`; `title()` returns an empty `TitleBarModel` since the top bar is hidden. |
| `SearchAction.kt` | `sealed class SearchAction : ChipboxAction()` — `QueryChanged`, `HistoryClicked`, `HistoryRemoved`, `GameClicked`, `SongClicked`, `ArtistClicked`, `BackClicked`. |

## Why depend on this module

Depend on `:features:search:api` when you need to navigate to the search screen
(e.g. emitting `ChipboxEvent.NavigateTo(Search)`) without depending on its
ViewModel/Compose impl. `cbox/common/appui/api/.../ChipboxScreens.kt` maps this
key to a Voyager `Screen` (search is one of the three root tabs), and the app
wires the implementation via `:features:search:real`. Keep navigation-only
callers on `:api`; only the app graph needs `:real`.

## Using it

```kotlin
// From any feature's ViewModel, jump to the search screen:
emit(ChipboxEvent.NavigateTo(Search))
```

## Module facts

- **Plugin:** `chipbox.feature.api`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js` (the `feature.api` plugin transitively applies `sage.kmp.js`).
- **Source set:** `commonMain`
- **SAGE/module dependencies:** the public dependencies of the State/Action types, declared in `build.gradle.kts`; `kotlinx.serialization` comes from the convention plugin
