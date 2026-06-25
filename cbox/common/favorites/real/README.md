# `:cbox:common:favorites:real`

> The favorites database and the repository that reads/writes it.

The `:real` impl of `:cbox:common:favorites:api`. It owns a separate Room KMP
`FavoritesDatabase` and the `RealFavoritesRepository` over it. Kept apart from
the library `ChipboxDatabase` so favorites survive the library's destructive
rescans; one table per favoritable type, ids referencing library rows by value
with no cross-database foreign keys.

## Contents

| File | What it is |
| --- | --- |
| `FavoritesDatabase.kt` | `@Database(version = 1)` over the track/game/artist favorite entities, `@ConstructedBy` for a per-target actual (Android framework SQLite / JVM bundled driver). In `src/main/java` (`jvmSharedMain`). |
| `RealFavoritesRepository.kt` | Each `set…Favorite` inserts a row stamped with the current wall-clock time (so lists order newest-first) or deletes it; `is…Favorite` / `favorite…Ids` delegate to the DAO streams; `clearFavorites` nukes all three tables. |

## Why depend on this module

The app graph depends on `:real` (via `:cbox:common:favorites:di`) to bind the
repository into `AppScope`; the `FavoritesDatabase` is provided per-platform.
Elsewhere depend on `:cbox:common:favorites:api` for the interface.

## Using it

```kotlin
val repository: FavoritesRepository = RealFavoritesRepository(
    db.trackFavoriteDao(), db.gameFavoriteDao(), db.artistFavoriteDao(), hatchet,
)
repository.setGameFavorite(gameId = 42, favorite = true)
```

## Module facts

- **Plugin:** `sage.kmp` + `ksp` + `chipbox.plugins.kmp.test`
- **Targets:** Android + JVM
- **Source set:** `jvmSharedMain`/`src/main/java` (the `@Database`) + `commonMain` (repository); `jvmMain` adds the bundled SQLite driver
- **SAGE/module dependencies:** `:cbox:common:favorites:api`, `:cbox:common:entities:api`, `room-runtime`, `:cbox:common:models:api`, `kotlinx-coroutines-core`, `sage.common.logging`; `room-compiler` (KSP)
