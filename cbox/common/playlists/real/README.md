# `:cbox:common:playlists:real`

> The playlists database and the repository that reads/writes it.

The `:real` impl of `:cbox:common:playlists:api`. It owns a separate Room KMP
`PlaylistsDatabase` and the `RealPlaylistsRepository` over it. Kept apart from
the library `ChipboxDatabase` so playlists survive the library's destructive
rescans; the `playlist` table holds metadata and `playlist_track` holds
memberships (FK to `playlist` for cascade-delete) referencing library track ids
by value, with no cross-database foreign key.

## Contents

| File | What it is |
| --- | --- |
| `PlaylistsDatabase.kt` | `@Database(version = 1)` over `PlaylistEntity` + `PlaylistTrackEntity`, `@ConstructedBy` for a per-target actual (Android framework SQLite / JVM bundled driver). In `src/main/java` (`jvmSharedMain`). |
| `RealPlaylistsRepository.kt` | Metadata reads map `PlaylistWithCount` → `Playlist`; `addTracks` appends after `maxPosition`; `removeTrack` is a single-row delete (no clear+reinsert flash); `setTrackOrder` rewrites the whole membership at contiguous positions; `deletePlaylist` cascades memberships; `clearAll` nukes both tables. |

## Why depend on this module

The app graph depends on `:real` (via `:cbox:common:playlists:di`) to bind the
repository into `AppScope`; the `PlaylistsDatabase` is provided per-platform.
Elsewhere depend on `:cbox:common:playlists:api` for the interface.

## Using it

```kotlin
val repository: PlaylistsRepository = RealPlaylistsRepository(
    db.playlistDao(), db.playlistTrackDao(), hatchet,
)
val id = repository.createPlaylist("Favorites")
repository.addTracks(id, listOf(10, 11, 12))
```

## Module facts

- **Plugin:** `sage.kmp` + `ksp` + `chipbox.plugins.kmp.test`
- **Targets:** Android + JVM
- **Source set:** `jvmSharedMain`/`src/main/java` (the `@Database`) + `commonMain` (repository); `jvmMain` adds the bundled SQLite driver
- **SAGE/module dependencies:** `:cbox:common:playlists:api`, `:cbox:common:entities:api`, `room-runtime`, `:cbox:common:models:api`, `kotlinx-coroutines-core`, `sage.common.logging`; `room-compiler` (KSP)
