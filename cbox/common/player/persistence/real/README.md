# `:cbox:common:player:persistence:real`

> DataStore-backed session store + the director-watching persister.

The production implementations of the persistence `:api` interfaces. This is the
`:real` module: the app pulls it in through
`:cbox:common:player:persistence:di`; nothing else should depend on it directly.

## Contents

| File | What it is |
| --- | --- |
| `RealPlaybackSessionStore.kt` | `PlaybackSessionStore` over SAGE's shared `Storage` (DataStore). JSON-encodes the snapshot into one string key (`playback.session.snapshot`). `Storage` has no remove, so `clear()` writes `""` and `load()` reads empty/old-schema/corrupt back as "nothing saved" (decode failures swallowed). |
| `RealPlaybackSessionPersister.kt` | `PlaybackSessionPersister`. `restore()` reads the snapshot, runs the optional `SessionSnapshotFilter` (web resolves track ids against the library), and submits `SessionRequest.Restore`; any failure is swallowed and the snapshot cleared so a bad save can't wedge launch. `observe()` `combine`s the director's session/playback/metadata/setlist flows and saves on the `PLAYING→PAUSED` edge, clears on `STOPPED`. `snapshotNow(teardown)` is the synchronous teardown net; a `tearingDown` flag (set only for a teardown flush) stops the release-time `STOPPED` from clearing what it just wrote, while a visibility flush lets a later real stop clear. `resumeAfterTeardown()` clears the flag when the host resumes (BFCache `pageshow`). |
| `SessionSnapshotMapping.kt` | Internal mappers: `SessionSnapshot.toSession()` (saved track id → `startingTrackId`, `resolvedSetlist`/`explicitSetlist` → explicit setlist) and `snapshotOf(session, track, positionMs, setlist)`. |

Tests (`commonTest`): `RealPlaybackSessionPersisterTest` drives the persister over
`FakeDirector` + `FakePlaybackSessionStore`.

## Why depend on this module

Only `:cbox:common:player:persistence:di` should — to construct the store and
persister and bind them into `AppScope`. Everyone else depends on `:api`. The
runtime collaborators are SAGE `Storage`, a `Hatchet` logger, and the `Director`.

## Using it

```kotlin
val store = RealPlaybackSessionStore(storage)
val persister = RealPlaybackSessionPersister(director, store, hatchet)
// Web: `filter` resolves stored track ids against the library before restore.
val webPersister = RealPlaybackSessionPersister(director, webStore, hatchet, filter = webFilter)
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `kotlin.serialization` + `chipbox.plugins.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (impl) / `commonTest` (tests)
- **SAGE/module dependencies:** `:cbox:common:player:persistence:api` (`api`); `implementation` of `:cbox:common:player:common:api`, `:cbox:common:player:director:api`, `:cbox:common:models:api`, `kotlinx.coroutines.core`, `kotlinx.serialization.json`, `sage.common.storage.common`, `sage.common.logging`. Test deps add `:persistence:fake` and `:director:fake`.
