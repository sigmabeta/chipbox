# `:cbox:common:entities:api`

> The Room persistence rows — every `@Entity` table and join in Chipbox's databases.

Holds the Room `@Entity` data classes (and their join tables) that back Chipbox's
SQLite databases. This is an `:api` module: pure types, no Room runtime. It carries
only `room-common` (the annotation artifact, which publishes a Kotlin/JS variant), so
the entities compile for Android, JVM, and the JS purity gate — the actual Room
codegen runs in `:cbox:common:database:real`, which keeps `room-runtime`. The DAOs in
`:cbox:common:database:api` operate on these rows; the repository maps them to/from the
domain models in `:cbox:common:models:api`.

## Contents

These are Room rows split across several databases (the library `ChipboxDatabase` is a
derived cache that is rebuilt destructively, so user data lives in separate databases
and references library rows by plain id rather than cross-database FK).

- **Library rows** — `ArtistEntity` (`artist`, unique on `name`), `GameEntity`
  (`game`, unique on `folder_key`, plus `folderSignature` for rescan skipping and
  optional descriptive metadata), `TrackEntity` (`track`, unique on `(path,
  trackNumber)`, `CASCADE` FK to `game`, `platform` stored as `Platform.name`,
  `chainFiles` as an encoded string). Both `GameEntity` and `TrackEntity` carry
  `dateAdded`/`dateLastUpdated` (epoch millis) stamped by the scanner.
  `@PrimaryKey(autoGenerate = true)` ids.
- **Library joins** (`joins/`) — `GameArtistJoin`, `TrackArtistJoin`: composite-PK
  many-to-many tables with `CASCADE` foreign keys to both parents.
- **Favorites** (separate `FavoritesDatabase`) — `ArtistFavoriteEntity`,
  `GameFavoriteEntity`, `TrackFavoriteEntity`: id-keyed marks with a `favoritedAtMs`
  index for newest-first ordering.
- **Play history** (separate `HistoryDatabase`) — `SongPlayEntity` (one row per
  recorded play) and the running-total `SongPlayCountEntity`, `GamePlayCountEntity`,
  `ArtistPlayCountEntity` (id-PK, UPSERT-maintained by the `history.dao` DAOs).
- **Playlists** (separate `PlaylistsDatabase`) — `PlaylistEntity` and
  `PlaylistTrackEntity` (composite `(playlistId, trackId)` PK, `position` ordering,
  real `CASCADE` FK to `PlaylistEntity` since both live in the same database).
- **Search** — `SearchHistoryEntity` (`search_history`): recent query strings.

## Why depend on this module

Depend on `:cbox:common:entities:api` when you need to reference the persistence rows
directly — the DAOs in `:cbox:common:database:api`, the Room database registration in
`:cbox:common:database:real`, and the repository in `:cbox:common:repository:real`
that converts entities ↔ models. Application/feature code consumes the **domain
models** in `:cbox:common:models:api` instead; these entities are the DB-layer
counterpart, not the type features see.

## Using it

Entities are plain Room data classes, typically named in DAO queries and database
declarations:

```kotlin
@Dao
interface ArtistDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(artist: ArtistEntity): Long

    @Query("SELECT * FROM artist WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): ArtistEntity?
}

// And declared on the database:
@Database(entities = [ArtistEntity::class, GameEntity::class, TrackEntity::class, GameArtistJoin::class, /* … */], version = /* … */)
abstract class ChipboxDatabase : RoomDatabase()
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** none (leaf); `api(libs.room.common)` for the Room
  annotations only.
