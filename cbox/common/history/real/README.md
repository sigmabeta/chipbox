# `:cbox:common:history:real`

> The playback-history database, repository, and the recorder that decides when a
> play counts.

The `:real` impl of `:cbox:common:history:api`. It owns a separate Room KMP
`HistoryDatabase`, the `RealPlaybackHistoryRepository` that writes/reads it, and
the `RealPlaybackHistoryRecorder` that observes the director and records one play
per track once the listen-threshold is met. The database is kept apart from the
library `ChipboxDatabase` so history survives the library's destructive rescans;
ids reference library rows by value, with no cross-database foreign keys.

## Contents

| File | What it is |
| --- | --- |
| `HistoryDatabase.kt` | `@Database(version = 1)` over the four play/counter entities, `@ConstructedBy` so Room generates a per-target actual (Android framework SQLite / JVM bundled driver). In `src/main/java` (`jvmSharedMain`). |
| `RealPlaybackHistoryRepository.kt` | `recordPlay` inserts a `song_play` and fans the increment out to the track, its game, and each artist under one timestamp; read streams delegate to the DAOs; `clearHistory` nukes all four tables. |
| `RealPlaybackHistoryRecorder.kt` | Combines the director's metadata/playback/session streams; records once per track-start when position hits 10s or the track reaches its natural end (tracks max position, keyed on `(trackId, sessionId)` so seeks don't re-arm). |

## Why depend on this module

The app graph depends on `:real` (via `:cbox:common:history:di`) to bind the
repository and recorder into `AppScope`; the `HistoryDatabase` itself is provided
per-platform. Elsewhere depend on `:cbox:common:history:api` for the interfaces.

## Using it

```kotlin
val repository = RealPlaybackHistoryRepository(
    db.songPlayDao(), db.songPlayCountDao(), db.gamePlayCountDao(), db.artistPlayCountDao(), hatchet,
)
val recorder = RealPlaybackHistoryRecorder(director, repository, hatchet)
recorder.observe()
```

## Module facts

- **Plugin:** `sage.kmp` + `ksp` + `chipbox.plugins.kmp.test`
- **Targets:** Android + JVM
- **Source set:** `jvmSharedMain`/`src/main/java` (the `@Database`) + `commonMain` (repository, recorder); `jvmMain` adds the bundled SQLite driver
- **SAGE/module dependencies:** `:cbox:common:history:api`, `:cbox:common:entities:api`, `room-runtime`, `:cbox:common:models:api`, `:cbox:common:player:director:api`, `:cbox:common:player:common:api`, `kotlinx-coroutines-core`, `sage.common.logging`; `room-compiler` (KSP)
