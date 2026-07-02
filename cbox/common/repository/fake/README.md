# `:cbox:common:repository:fake`

> In-memory `Repository` test doubles — a full preview library plus a minimal test stub.

The `:fake` module for the library repository: pure-Kotlin in-memory
implementations of `Repository` (from `:cbox:common:repository:api`) for tests,
previews, and screenshots. No database; the library lives in maps.

## Contents

| File | What it is |
| --- | --- |
| `memory/MemoryRepository.kt` | `open class MemoryRepository` — the full in-memory `Repository`: stores games/tracks/artists in maps keyed by id and title/name, serves them through cached shared flows (with fresh cold flows for paged/by-id reads), and supports `upsertGame`/`clearLibrary`/search/search-history. The preview/screenshot fake. |
| `memory/RandomMemoryRepository.kt` | A `MemoryRepository` pre-seeded at construction with a deterministic pseudo-random library (`seed`/`games`/`tracks`/`artists`) — a populated library in one line for previews and UI tests. |
| `fake/FakeRepository.kt` | A minimal test-only stub: resolves `getTrack`/`getTracksByIds` from a caller-supplied `Map<Long, Track>`, returns `Data.Empty` for list/flow reads, and `TODO()`s the per-relation track queries (Director tests stick to SETLIST sessions). |
| `memory/models/models/{MemoryArtist,MemoryGame,MemoryTrack}.kt` | Internal in-memory data holders with mutable back-references, converted to `:models:api` domain types by `MemoryRepository`. |
| `memory/MemoryRepositoryTest.kt` (`commonTest`) | Tests for `MemoryRepository` behaviour. |

## Why depend on this module

Depend on `:cbox:common:repository:fake` (usually in `testImplementation` or
preview/screenshot source sets) when you need a working `Repository` without a
real database: `FakeRepository` for narrow unit tests that only resolve tracks by
id, `MemoryRepository`/`RandomMemoryRepository` when a screen or test needs a
populated library. Production code uses `:cbox:common:repository:real`
(`DatabaseRepository`) over the `:cbox:common:database:api` DAOs instead;
`:cbox:common:repository:di` `api`-exposes this module to bind the in-memory
debug sources.

## Using it

```kotlin
// A populated, deterministic library for a preview or UI test:
val repository: Repository = RandomMemoryRepository(games = 12, tracks = 80, artists = 6)

// A minimal stub that only needs to resolve known track ids:
val stub: Repository = FakeRepository(tracksById = mapOf(1L to track))
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `chipbox.plugins.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (tests in `commonTest`)
- **SAGE/module dependencies:** `api`s `:cbox:common:repository:api` and
  `:cbox:common:models:api`; `implementation` `:cbox:common:utils:api`
