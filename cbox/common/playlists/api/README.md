# `:cbox:common:playlists:api`

> Playlists contracts — the repository interface, the Room DAOs, and the
> count projection.

The `:api` module for user playlists: the `PlaylistsRepository` interface, the
two Room `@Dao` interfaces (playlist metadata and track membership), and the
`PlaylistWithCount` projection. Annotations only (`room-common`) — the
`@Database` and the room-compiler run live in `:cbox:common:playlists:real`.

## Contents

| File | What it is |
| --- | --- |
| `PlaylistsRepository.kt` | Facade: `playlists`/`playlist` metadata streams, `trackIds` membership stream, `createPlaylist`/`renamePlaylist`/`deletePlaylist`, `addTracks`/`removeTrack`/`setTrackOrder`, `clearAll`. Track reads return bare ids the caller hydrates via the library `Repository`. |
| `dao/PlaylistDao.kt` | Metadata: insert, rename, delete (memberships cascade), and `getAllWithCounts`/`getByIdWithCount` (LEFT JOIN + GROUP BY → `PlaylistWithCount`, newest first), nuke. |
| `dao/PlaylistTrackDao.kt` | Membership: `insertAll` (IGNORE on the `(playlistId, trackId)` PK), ordered `trackIds`, `maxPosition` (for appends), single-row `remove`, `clear`, nuke. |
| `dao/PlaylistWithCount.kt` | `@Embedded PlaylistEntity` + `trackCount` projection so lists show size without hydrating tracks. |

## Why depend on this module

Depend on `:api` for the `PlaylistsRepository` type — the playlists list/detail
screens observe its streams and the add-to-playlist CTAs call its mutators. The
app binds `:real` via `:cbox:common:playlists:di`; tests use
`:cbox:common:playlists:fake`.

## Using it

```kotlin
class PlaylistDetailViewModel(private val repo: PlaylistsRepository, id: Long) {
    val playlist: Flow<Playlist?> = repo.playlist(id)
    val trackIds: Flow<List<Long>> = repo.trackIds(id)
    suspend fun add(ids: List<Long>) = repo.addTracks(id, ids)
}
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `room-common`, `:cbox:common:entities:api`, `:cbox:common:models:api`, `kotlinx-coroutines-core`
