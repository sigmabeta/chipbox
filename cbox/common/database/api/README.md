# `:cbox:common:database:api`

> Room `@Dao` contracts for Chipbox's library database.

The six `@Dao` interfaces (and one projection type) that define every read/write
against the library database. This is the `:api` module: it holds interfaces/types
only, so any layer can depend on the DAO contracts without pulling in the Room
runtime. It needs only Room's annotations (`room-common`, which publishes a JS
variant), the entity types from `:cbox:common:entities:api`, and `Flow`, which is
what lets it build for the JS purity gate alongside Android + JVM. The production
`@Database` lives in `:cbox:common:database:real`; in-memory doubles live in
`:cbox:common:database:fake`.

## Contents

| File | What it is |
| --- | --- |
| `ArtistDao.kt` | Artist reads/writes: by-id (`Flow` + sync), by-name, paged `getAll`, `LIKE` search, random, insert, `deleteOrphans` (drop artists no track references), `nukeTable`. |
| `GameDao.kt` | Game reads/writes: by-id, by-`folder_key`, signature snapshot (`getSignatureRows` → `GameSignatureRow`), paged `getAll`, per-platform, `LIKE` search, random, `getRecentlyAdded` (date_added window + random + limit, for the Home row), update, `deleteByIds`, `nukeTable`. Also declares `GameSignatureRow`. |
| `TrackDao.kt` | Track reads/writes: paged `getAll`, per-game, per-platform, by-id(s), distinct platforms, `LIKE` search, random, insert/insertAll, `updateAll`, `deleteByIds`, `nukeTable`. |
| `GameArtistDao.kt` | The `game_artist_join` table: insert/delete links, id lists and full-row joins resolving games↔artists in display order, `nukeTable`. |
| `TrackArtistDao.kt` | The `track_artist_join` table: insert/delete links, a track's artists, and an artist's tracks ordered by game title then track number, `nukeTable`. |
| `SearchHistoryDao.kt` | Recent searches (most-recent-first, `LIMIT 10`), by-query lookup, insert, `deleteById`. |

Conventions across the DAOs: `Flow`-returning methods stay plain `fun`; every
other method is `suspend` (Room KMP requires this on non-Android targets). Paged
queries pass a negative `limit` to mean "no limit" (SQLite semantics).

## Why depend on this module

Depend on `:cbox:common:database:api` to consume the DAO contracts and the
`GameSignatureRow` projection without the (JVM/Android-only) Room runtime. The
repository layer depends on it for the interfaces; the app's running database is
`:cbox:common:database:real`, and tests substitute `:cbox:common:database:fake`.
The DAOs operate on entities declared in `:cbox:common:entities:api`. These
database modules have no `:di` sibling — the repository `:di`
(`:cbox:common:repository:di`) is what wires the real database into `AppScope`.

## Using it

```kotlin
class ArtistRepository(private val artistDao: ArtistDao) {
    fun all(): Flow<List<ArtistEntity>> = artistDao.getAll()

    suspend fun add(artist: ArtistEntity): Long = artistDao.insert(artist)
}
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `:cbox:common:entities:api`, `room-common`, `kotlinx-coroutines-core`
