# `:cbox:common:database:fake`

> In-memory map-backed doubles for the six library DAOs.

A single `FakeDatabase` that implements the six DAO interfaces from
`:cbox:common:database:api` over plain Kotlin maps. This is the `:fake` module —
pure-Kotlin test doubles standing in for the production Room runtime in
`:cbox:common:database:real`, used by `DatabaseRepositoryTest` in
`:cbox:common:repository:real`. It mimics Room's "Flow re-emits when an
underlying table changes" contract without any SQLite.

## Contents

| File | What it is |
| --- | --- |
| `FakeDatabase.kt` | One class exposing `artistDao`, `gameDao`, `trackDao`, `gameArtistDao`, `trackArtistDao`, `searchHistoryDao` plus the public backing maps/lists. A shared `MutableStateFlow` trigger is bumped on every write; `Flow`-returning methods `.map` over it so subscribers re-emit, the same way production Room does. |

Behavioral notes from the source: not thread-safe (callers run on a single
coroutine, typically `runTest` + `UnconfinedTestDispatcher`); it does not enforce
SQL constraints, but it *does* mimic FK delete cascades
(game → track → `track_artist_join`, plus `game_artist_join`) so
`deleteOrphans()` sees zero references; `getRandom()` draws from a fixed-seed
`Random(0)` (overridable via the constructor) for determinism; and a tiny
`sqlLike` helper approximates the production `%…%` `LIKE` searches.

## Why depend on this module

Depend on `:cbox:common:database:fake` from test source sets that need DAO
behavior without a real database. It pairs with `:cbox:common:database:api` (the
contracts it implements) and `:cbox:common:entities:api` (the entity types);
production code uses `:cbox:common:database:real` instead. These database
modules have no `:di` sibling — the repository `:di`
(`:cbox:common:repository:di`) wires the real database into `AppScope`, while
tests construct `FakeDatabase` directly.

## Using it

```kotlin
val db = FakeDatabase()
val id = db.artistDao.insert(ArtistEntity(name = "Nobuo Uematsu"))

db.artistDao.getAll().test {
    assertEquals(listOf(id), awaitItem().map { it.id })
}
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `:cbox:common:database:api`, `:cbox:common:entities:api`, `kotlinx-coroutines-core`
