# `:cbox:common:repository:api`

> The `Repository` interface — the library data layer that sits over the database and serves domain entities to features.

The `:api` module for the Chipbox library repository: the `Repository`
interface plus its `Raw*` write inputs, the `Data<T>` result wrapper, and the
scan-reconciliation types. The repository sits *over* the database (Room-style
DAOs) and hands `Game`/`Artist`/`Track`/`Platform`/`SearchHistory` domain models
(from `:cbox:common:models:api`) to features. As an `:api` module it holds only
interfaces and types — depend on it to consume the repository; the production
impl lives in `:cbox:common:repository:real`, wired via `:cbox:common:repository:di`.

## Contents

| File | What it is |
| --- | --- |
| `Repository.kt` | The library interface: list/`Flow` reads (`getAllArtists/Games/Tracks`, paged via `limit`/`offset`), by-id and by-relation reads (`getTracksForGame/Artist/Platform`, `getGame`, `getArtist`, `getTracksByIds`), random picks (`getRandomTrack/Game/Artist`), search + search history, and the scan write path (`folderSnapshots`, `upsertGame`, `pruneGames`, `clearLibrary`). |
| `Data.kt` | `sealed class Data<out DataType>` result wrapper: `Loading`, `Empty`, `Succeeded(data)`, `Failed(message)`. Reads are `Flow<Data<...>>`. |
| `RawGame.kt` | Scanner-side write input for one game: title, `photoUrl`, `folderKey` (stable folder identity), `folderSignature` (files hash), its `RawTrack`s, plus release-level descriptive metadata. |
| `RawTrack.kt` | Scanner-side write input for one track/subtune: path, source, title, artist, length, `trackNumber`, `chainFiles`, `Platform`, and optional descriptive metadata. |
| `RawArtist.kt` | Minimal scanner-side artist input (`name`). |
| `FolderSnapshot.kt` | Pre-scan view of a stored game (`signature`, `trackCount`), keyed by `folderKey`; lets the scanner skip folders whose signature is unchanged. |
| `GameWriteResult.kt` | `GameWriteOutcome(gameId, result)` and the `GameWriteResult` enum (`ADDED`/`UPDATED`/`UNCHANGED`) returned by `upsertGame`. |

## Why depend on this module

Depend on `:cbox:common:repository:api` when you need to read or write the music
library and want to stay decoupled from storage — features, the player, and the
scanner all consume `Repository` through this `:api`. The app supplies the
backing implementation: `:cbox:common:repository:real` (a `DatabaseRepository`
over the `:cbox:common:database:api` DAOs) bound into the graph by
`:cbox:common:repository:di`; tests and previews use the in-memory doubles in
`:cbox:common:repository:fake`. Domain models live in `:cbox:common:models:api`;
the DB-row entities the real impl maps to/from live in
`:cbox:common:entities:api`.

## Using it

```kotlin
class LibraryViewModel(private val repository: Repository) {
    val games: Flow<Data<List<Game>>> = repository.getAllGames(withArtists = true)

    suspend fun shuffleOne(): Track? = repository.getRandomTrack()
}

// Scanner write path:
suspend fun ingest(repository: Repository, scanned: RawGame) {
    val outcome = repository.upsertGame(scanned)
    when (outcome.result) {
        GameWriteResult.ADDED, GameWriteResult.UPDATED -> notify(outcome.gameId)
        GameWriteResult.UNCHANGED -> Unit
    }
}
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `api`s `:cbox:common:models:api` and
  `kotlinx-coroutines-core`
