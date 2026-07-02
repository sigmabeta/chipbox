# `:features:favorites:api`

> The Favorites screen's public route key.

The `:api` half of the Favorites feature screen: it exports the `@Serializable`
Voyager route key other features navigate to. Being the `:api` module, it carries
only the public surface — no ViewModel, Compose, or state — so any feature can
reference the destination without pulling in the screen implementation.
(`FavoritesState`/`FavoritesAction` live in `:real` for this feature.)

## Contents

| File | What it is |
| --- | --- |
| `Favorites.kt` | `@Serializable data object Favorites` — the route key. `ChipboxScreens.kt` maps it to a Voyager `Screen`; emit `ChipboxEvent.NavigateTo(Favorites)` to open the screen. |

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
- **SAGE/module dependencies:** none (leaf — only `kotlinx.serialization`)
