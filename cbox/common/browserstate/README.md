# `:cbox:common:browserstate`

> Versioned browser-local state — JSON envelope/codec plus the `Storage`-backed favorites and
> playlists repositories the web app binds.

The shared home for durable state that lives in the browser's `localStorage` (through SAGE's
`Storage` abstraction). It is pure `commonMain`: no `kotlinx.browser`, no DOM types, no Room. The
JSON/ordering rules therefore run as Node tests (`jsNodeTest`) as well as on the JVM. It holds
favorites, playlists, the bounded play history, and the playback-session store the web app binds.

## Contents

| File | What it is |
| --- | --- |
| `VersionedJsonCodec.kt` | `VersionedEnvelope<T>(schemaVersion, payload)` + `VersionedJsonCodec<T>`. Decode returns `Missing`, `Success(value, migratedFrom?)`, or `Failed(CORRUPT / UNSUPPORTED_VERSION)` — never throws. Reads the header before the payload so a newer payload shape is recognised and ignored safely. An optional `legacyDecoder` reads pre-envelope (schema-version-0) blobs; it is only consulted when no `schemaVersion` key exists. |
| `VersionedStorageStore.kt` | One `Storage` key + codec + empty value. `values()` derives from `storage.savedStringFlow(key)` (so cross-tab `storage` events propagate), `update {}` does a mutex-serialized read-modify-write (no same-tab lost update), and storage failures (quota `DOMException`, unavailable storage) report a `BrowserStateEvent`. `CancellationException` always propagates — it is never reported as a read failure. A corrupt blob always gets rewritten by the next `update`, so an explicit clear repairs the key. |
| `BrowserStateEvents.kt` | Non-fatal event sink (`SharedFlow<BrowserStateEvent>` + `userMessages`). Missing data is log-only; corrupt/older/newer/quota/read failures are user-visible messages. Read-side events are deduped so several screens collecting the same store don't spam. |
| `BrowserStateKeys.kt` | The stable `localStorage` keys: `chipbox.favorites`, `chipbox.playlists`, `chipbox.playback_history`, `playback.session.snapshot`. |
| `BrowserStateCodecs.kt` | The current codec instances (`schemaVersion = 1`), including the legacy (v0) readers for play history and the playback session. |
| `FavoritesState.kt` | Payload: per-kind `FavoriteEntry(id, favoritedAtMs)` lists; `normalized()` dedupes by id and sorts newest-first. |
| `StorageFavoritesRepository.kt` | `FavoritesRepository` over the store. Re-favoriting refreshes the timestamp; clear writes an empty payload. |
| `PlaylistsState.kt` | Payload: persisted `nextId` + `PlaylistEntry(id, name, createdAtMs, trackIds)`. `normalized()` enforces unique ids/members and `nextId > max(id)`; `newestFirst()` orders the list. |
| `StoragePlaylistsRepository.kt` | `PlaylistsRepository` over the store: stable monotonic ids, newest-created-first list, ordered membership, append de-dup, rename/delete/add/remove/reorder. `createPlaylist` returns null when the write is refused, so callers never navigate to a phantom playlist. |
| `SearchHistoryState.kt` | Payload: monotonic `nextId` + newest-first `SearchHistory` entries. `normalized()` de-dupes by query, caps at `MAX_ENTRIES` (10), and keeps ids monotonic. |
| `StorageSearchHistoryRepository.kt` | Recent searches over the store: add (moves an existing query to the top, otherwise prepends), remove by id, clear. Browser-only — the server stores none of it. |
| `PlayHistoryState.kt` | Payload: chronological `PlayHistoryEntry(trackId, gameId, artistIds, timeMs)` plays. `MAX_ENTRIES = 500`; `withPlay()` appends and `bounded()` keeps the newest plays by timestamp. |
| `StoragePlaybackHistoryRepository.kt` | `PlaybackHistoryRepository` over the store: records plays, folds recently-played / most-played songs, games, and artists, and exposes played game ids. Field names match the old unversioned `WebPlay` array (v0). |
| `PlaybackSessionState.kt` | Payload: at most one `SessionSnapshot` (queue, current track, position, shuffle, repeat). |
| `StoragePlaybackSessionStore.kt` | Shared `PlaybackSessionStore` contract over the store. `save`/`clear` are synchronous, non-throwing explicit writes (safe from a `pagehide` handler); `clear` leaves no resumable snapshot. Being explicit writes, they also replace a newer-schema blob — see the storage-contract bullet. |
| `SessionSnapshotFiltering.kt` | `withKnownTracks(knownIds)` / `referencedTrackIds()`: pure pre-restore filtering that drops unknown track ids, remaps the current track by queue position, and returns null when nothing survives. |

## Storage contract

Every value is one JSON object at a stable key:

```json
{ "schemaVersion": 1, "payload": { "...": "store-specific" } }
```

- **Favorites** — `chipbox.favorites`:
  ```json
  { "tracks":  [{"id":1,"favoritedAtMs":1700000000000}],
    "games":   [{"id":2,"favoritedAtMs":1700000000001}],
    "artists": [{"id":3,"favoritedAtMs":1700000000002}] }
  ```
  Lists are newest-first; re-favoriting replaces the entry with a fresh timestamp.
- **Playlists** — `chipbox.playlists`:
  ```json
  { "nextId": 4,
    "playlists": [{"id":3,"name":"Drive","createdAtMs":1700000000002,"trackIds":[10,11]}] }
  ```
  `nextId` is persisted and never reused (including across `clearAll`). List order is sorted by
  `createdAtMs` descending; `trackIds` order is the user's order.
- **Play history** — `chipbox.playback_history`:
  ```json
  { "plays": [{"trackId":10,"gameId":2,"artistIds":[3],"timeMs":1700000000000}] }
  ```
  Chronological qualifying plays, **capped at the newest 500** (`PlayHistoryState.MAX_ENTRIES`).
  Recording and loading trim the oldest entries by `timeMs`. The pre-versioning web app stored a
  bare JSON array at this same key; that array is read as schema version 0 and rewritten inside the
  envelope on the next recorded play.
- **Playback session** — `playback.session.snapshot`:
  ```json
  { "snapshot": { "type":"GAME", "contentId":7, "currentTrackId":42,
                  "positionMs":42000, "shuffled":true, "repeatMode":"ALL",
                  "resolvedSetlist":[40,41,42], "modified":false } }
  ```
  At most one snapshot; `clear()` persists `{"snapshot":null}` so no reload can resurrect a
  stopped session. The key matches the shared `RealPlaybackSessionStore`, and a raw snapshot object
  (no envelope) is read as schema version 0.
- **Search history** — `chipbox.search_history`:
  ```json
  { "nextId": 3, "entries": [{"id":2,"query":"mega man"},{"id":1,"query":"chrono"}] }
  ```
  Newest-first, de-duplicated by query, capped at 10. This is browser-only user state; the web
  client never sends it to the server.

Behaviour rules:

- Missing key → empty value (normal; logged, never a user snackbar).
- Corrupt/malformed → empty value + non-fatal message.
- `schemaVersion < current` → payload is decoded if possible and reported as migrated; the next
  write persists the current version. Pre-envelope blobs with no header are legacy v0 and go through
  the store's `legacyDecoder`.
- A header that is present but malformed is corrupt — it is never reinterpreted as legacy data.
- `schemaVersion > current` → empty value + non-fatal message (never crash, never reinterpret a
  future payload).
- Quota/read failure → caught, empty/previous value retained, non-fatal message. Writes are
  last-writer-wins across tabs; no merge is promised. Within one tab, `update {}` is serialized by
  a per-store `Mutex`, so two coroutines cannot lose one mutation (two tabs still have two stores,
  hence two locks).
- Cancellation is not a storage failure: a `CancellationException` from `values()` or `current()`
  propagates to the caller and never reports `READ_FAILED`.
- A corrupt blob is replaced by the next `update`, even when the transform produces the same empty
  value the corrupt blob decoded to. That is what makes an explicit clear (favorites, playlists,
  history, search) actually erase an unreadable string; the session store's `clear()` writes
  unconditionally for the same reason.
- A newer-schema blob (`schemaVersion > current`) is **never rewritten by `update`**: this build
  cannot decode it, so it is left for the build that wrote it. Two explicit writes are the
  exception and always overwrite: `StoragePlaybackSessionStore.save()`/`clear()`. They are
  synchronous (`PlaybackSessionStore` has no failure return) and `Storage` has no synchronous
  read, so a guard is not possible without changing that contract; the blob is unreadable here
  anyway, and an explicit save/clear is user intent that must land. Every `update`-based clear
  keeps the never-rewrite guarantee.

## Why depend on this module

`apps/js` binds the repositories/stores in `WebModules`; use `VersionedJsonCodec` +
`VersionedStorageStore` rather than growing another envelope implementation. Desktop/Android keep
their Room/DataStore implementations and do not depend on this module.

## Using it

```kotlin
val events = BrowserStateEvents { event -> hatchet.w(event.logMessage) }
val favorites = StorageFavoritesRepository(storage, events)
val playlists = StoragePlaylistsRepository(storage, events)
val history = StoragePlaybackHistoryRepository(storage, events)
val session = StoragePlaybackSessionStore(storage, events)

// A screen observes the storage-backed flow directly:
favorites.favoriteTrackIds().collect { ids -> /* resolve against the library; drop unknown ids */ }
history.recentlyPlayed(limit = 20).collect { recent -> /* repository.getTracksByIds(...) */ }

// The web host restores one saved session (paused) and flushes it on page teardown:
session.load()?.let { it.withKnownTracks(knownIds) } // null -> clear the snapshot
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `kotlin.serialization` + `chipbox.plugins.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (impl) / `commonTest` (codec + repository/store tests)
- **SAGE/module dependencies:** `:cbox:common:favorites:api`, `:cbox:common:playlists:api`,
  `:cbox:common:history:api`, `:cbox:common:models:api`, `:cbox:common:player:persistence:api`,
  `sage.common.storage.common`, `kotlinx-coroutines-core`, `kotlinx-serialization-json`

## Tests

```sh
./gradlew :cbox:common:browserstate:jsNodeTest -Psage.js=true \
  -Dorg.gradle.isolated-projects=false -Dorg.gradle.configureondemand=false --no-parallel
```

Covers envelope round-trip, corrupt/missing/older/newer versions, the legacy v0 hooks, favorites
ordering/re-favorite, playlist ids/order/reorder/append de-dup, history recording/folds/cap/trim/
migration, session save/load/clear/migration/failure paths, an explicit session clear replacing a
newer-schema blob, snapshot unknown-id filtering and
remap, cross-tab flow updates, quota/read failures, search-history persistence, same-tab concurrent
updates (both land), corrupt-blob repair on clear for every store, and cancellation propagation
(no spurious `READ_FAILED`).
