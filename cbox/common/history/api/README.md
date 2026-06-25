# `:cbox:common:history:api`

> Playback-history contracts — the repository/recorder interfaces, projection
> types, and Room DAOs.

The `:api` module for playback history: the `PlaybackHistoryRepository` /
`PlaybackHistoryRecorder` interfaces, the `RecentPlay` / `PlayCount` projection
types, and the four Room `@Dao` interfaces. Annotations only (`room-common`) —
the `@Database` and the room-compiler run live in `:cbox:common:history:real`.

## Contents

| File | What it is |
| --- | --- |
| `PlaybackHistoryRepository.kt` | Facade: `recordPlay(track)`, `recentlyPlayed`/`mostPlayedSongs`/`mostPlayedGames`/`mostPlayedArtists` read streams, `clearHistory`. |
| `PlaybackHistoryRecorder.kt` | `observe()` — watches the director and records a play once a track is listened to long enough (10s, or natural end). |
| `HistoryReads.kt` | `RecentPlay(trackId, timeMs)` and `PlayCount(id, playCount, lastPlayedMs)` projection types. |
| `dao/SongPlayDao.kt` | Individual plays: insert, count, recent-distinct (newest first), nuke. |
| `dao/SongPlayCountDao.kt` / `GamePlayCountDao.kt` / `ArtistPlayCountDao.kt` | Per-song/game/artist counters: UPSERT `increment`, sync get, `getMostPlayed` (playCount > 1, highest first), nuke. |

## Why depend on this module

Depend on `:api` for the repository/recorder types and projections — Home
modules collect the recent / most-played streams; the playback owner triggers
the recorder. The app binds `:real` via `:cbox:common:history:di`; tests use
`:cbox:common:history:fake`.

## Using it

```kotlin
class RecentlyPlayedModule(repo: PlaybackHistoryRepository) {
    val recent: Flow<List<RecentPlay>> = repo.recentlyPlayed(limit = 20)
}

// At the playback owner:
recorder.observe()   // records qualifying plays for the process's life
```

## Module facts

- **Plugin:** `sage.kmp`
- **Targets:** Android + JVM (no JS gate — nothing on JS consumes playback history)
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `room-common`, `:cbox:common:entities:api`, `:cbox:common:models:api`, `kotlinx-coroutines-core`
