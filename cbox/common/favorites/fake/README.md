# `:cbox:common:favorites:fake`

> An in-memory `FavoritesRepository` whose streams emit reactively.

The `:fake` test double for `:cbox:common:favorites:api`.
`FakeFavoritesRepository` backs each favoritable type with a `MutableStateFlow`
of ids, so the `is…Favorite` / `favorite…Ids` streams emit reactively — enough
to drive the real Compose UI over fakes, and seedable/inspectable directly.

## Contents

| File | What it is |
| --- | --- |
| `FakeFavoritesRepository.kt` | Three `MutableStateFlow<List<Long>>` (`trackIds` / `gameIds` / `artistIds`, newest favorite first). `set…Favorite` toggles into the right flow; `is…Favorite` maps membership; `favorite…Ids` expose the flows; `clearFavorites` empties all three. |

## Why depend on this module

Use it in UI/feature tests that drive favorite toggles and lists without Room.
The `:di` module also depends on it so the debug menu can swap it in at runtime.

## Using it

```kotlin
val repo = FakeFavoritesRepository().apply { gameIds.value = listOf(1, 2) }
repo.setGameFavorite(gameId = 3, favorite = true)
// gameIds now emits [3, 1, 2]
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `:cbox:common:favorites:api`, `kotlinx-coroutines-core`
