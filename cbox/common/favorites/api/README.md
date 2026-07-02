# `:cbox:common:favorites:api`

> Favorites contracts — the repository interface plus the per-type Room DAOs.

The `:api` module for user favorites: the `FavoritesRepository` interface and
the three Room `@Dao` interfaces (track / game / artist). Annotations only
(`room-common`) — the `@Database` and the room-compiler run live in
`:cbox:common:favorites:real`.

## Contents

| File | What it is |
| --- | --- |
| `FavoritesRepository.kt` | Facade: `set…Favorite` toggles, `is…Favorite` per-item streams the CTAs observe, `favorite…Ids` lists (newest first), `clearFavorites`. Reads return bare library ids — callers hydrate via the library `Repository`. |
| `dao/TrackFavoriteDao.kt` / `GameFavoriteDao.kt` / `ArtistFavoriteDao.kt` | Per-type DAOs: `add` (REPLACE, refreshing the timestamp), `remove`, `isFavorite` stream, `getAllIds` (newest favorite first), `nukeTable`. |

## Why depend on this module

Depend on `:api` for the `FavoritesRepository` type — favorite-toggle CTAs
observe `is…Favorite`, and the Favorites screen / favorites playback session read
the id lists. The app binds `:real` via `:cbox:common:favorites:di`; tests use
`:cbox:common:favorites:fake`.

## Using it

```kotlin
class FavoriteToggle(private val repo: FavoritesRepository, private val trackId: Long) {
    val isFavorite: Flow<Boolean> = repo.isTrackFavorite(trackId)
    suspend fun toggle(on: Boolean) = repo.setTrackFavorite(trackId, on)
}
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `room-common`, `:cbox:common:entities:api`, `:cbox:common:models:api`, `kotlinx-coroutines-core`
