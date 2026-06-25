# `:cbox:common:repository:real`

> `DatabaseRepository` — the production `Repository`, backed by the database DAOs with LRU hydration caches.

The `:real` (production impl) module for the library repository. It implements
`Repository` (from `:cbox:common:repository:api`) over the Room-style DAOs from
`:cbox:common:database:api`, mapping DB entities (`:cbox:common:entities:api`)
to/from domain models (`:cbox:common:models:api`). It is pure Kotlin: it takes
the `@Dao` interfaces directly (not the Room `@Database`), so it builds for the
`js()` purity gate too — the Room runtime appears only in DI.

## Contents

- **Repository impl** — `DatabaseRepository.kt`: the full `Repository`
  implementation. Reads return `Flow<Data<...>>` built by `setupFlow`
  (`Loading` → `Succeeded`/`Empty`, `Failed` on error, on `ioDispatcher`).
  Hydration (`withTracks`/`withGames`/`withArtists`) fans out per-row DAO calls
  via a `suspendMap` helper. The scan write path — `upsertGame` (insert-new vs
  update-existing, reconciling tracks by `(path, trackNumber)` and rebuilding
  artist links), `pruneGames`, `folderSnapshots`, `clearLibrary` — is traced
  with `traceAsync` (from `:cbox:common:perf:api`) and serializes artist
  get-or-create through a `Mutex`.
- **Cache** — `LruCache.kt`: a tiny coroutine-safe (`Mutex`) LRU keyed by entity
  id; `commonMain` has no access-order `LinkedHashMap`, so recency is maintained
  by hand. `DatabaseRepository` runs one instance per hydration resolver
  (`gameById`, `artistById`, `tracksForGame`, `tracksForArtist`), each cleared on
  any mutation. Misses log a running hit-rate at verbose level.
- **Local data holders** — `database/models/models/{DatabaseArtist,DatabaseGame,DatabaseTrack}.kt`:
  in-package `data class`es mirroring the model shape. (The live converters in
  `DatabaseRepository` map entities straight to `:models:api` domain types.)
- **Tests** (`commonTest`, drive the impl over `:cbox:common:database:fake`):
  `DatabaseRepositoryTest` (general reads/writes/reconciliation),
  `DatabaseRepositoryArtistCacheTest` (artist hydration caching),
  `DatabaseRepositoryTracksByIdsTest` (`getTracksByIds`), and `LruCacheTest`
  (the cache itself).

## Why depend on this module

You usually don't depend on `:real` directly — the app wires it through
`:cbox:common:repository:di`, which extracts the DAOs from the Room
`ChipboxDatabase` (`:cbox:common:database:real`) and constructs
`DatabaseRepository`. Consumers depend on `:cbox:common:repository:api` for the
`Repository` type; tests and previews use `:cbox:common:repository:fake`.

## Using it

```kotlin
// Normally constructed in DI from DAOs pulled off the Room database:
val repository: Repository = DatabaseRepository(
    artistDao, gameDao, trackDao,
    gameArtistDao, trackArtistDao, searchHistoryDao,
    hatchet,
)

repository.getAllGames(withArtists = true)
    .collect { data -> /* Data.Loading / Succeeded / Empty / Failed */ }
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `chipbox.plugins.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain` (tests in `commonTest`)
- **SAGE/module dependencies:** `api`s `:cbox:common:repository:api` and
  `:cbox:common:database:api`; `implementation`s `:cbox:common:perf:api`,
  `:cbox:common:utils:api`, and `sage.common.logging` (`Hatchet`). Tests add
  `:cbox:common:database:fake`.
