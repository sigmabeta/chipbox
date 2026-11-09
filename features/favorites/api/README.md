# `:features:favorites:api`

> The Favorites screen's public route key.

The `:api` half of the Favorites feature screen: it exports the `@Serializable`
Voyager route key other features navigate to. Being the `:api` module, it carries
the route key and the screen's State/Action value types, but no ViewModel or
Compose — so any feature can reference the destination without pulling in the
screen implementation. `FavoritesState`/`FavoritesAction` are the value types
this module adds.

## Contents

| File | What it is |
| --- | --- |
| `Favorites.kt` | `@Serializable data object Favorites` — the route key. `ChipboxScreens.kt` maps it to a Voyager `Screen`; emit `ChipboxEvent.NavigateTo(Favorites)` to open the screen. |
| `FavoritesState.kt` | `FavoritesState : ListState`. Holds `LCE` lists of tracks/games/artists plus `playingTrackId`. `columnType` is `Staggered`; `toListItems` builds a tracks `SectionHeader` + `NameCaptionValueListModel` rows and games/artists `HorizontalScrollerListModel`s of `WideItemListModel`s, collapsing to one `EmptyStateListModel` when all three sections have loaded empty. |
| `FavoritesAction.kt` | `sealed class FavoritesAction : ChipboxAction()` — `TrackClicked(position)`, `GameClicked(id)`, `ArtistClicked(id)`. |

## Why depend on this module

Depend on `:features:favorites:api` when you need to navigate to the Favorites
screen (e.g. a Library/Home entry point that pushes `Favorites`), or when
`ChipboxScreens.kt` wires the route key to its `Screen`. Depending on the api
module keeps you off the implementation's transitive graph (repositories,
director, Compose) — the app includes `:real` to actually render the screen.

## Using it

```kotlin
// Navigate to the Favorites screen from another ViewModel:
emit(ChipboxEvent.NavigateTo(Favorites))
```

## Module facts

- **Plugin:** `chipbox.feature.api`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js` (the `feature.api` plugin transitively applies `sage.kmp.js`)
- **Source set:** `commonMain`
- **SAGE/module dependencies:** the public dependencies of the State/Action types, declared in `build.gradle.kts`; `kotlinx.serialization` comes from the convention plugin
