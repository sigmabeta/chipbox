# `:cbox:common:browserstate`

> Versioned browser-local state — JSON envelope/codec plus the `Storage`-backed favorites and
> playlists repositories the web app binds.

The shared home for durable state that lives in the browser's `localStorage` (through SAGE's
`Storage` abstraction). It is pure `commonMain`: no `kotlinx.browser`, no DOM types, no Room. The
JSON/ordering rules therefore run as Node tests (`jsNodeTest`) as well as on the JVM, and ticket 07
reuses the envelope/store for playback history and the playback session.

## Contents

| File | What it is |
| --- | --- |
| `VersionedJsonCodec.kt` | `VersionedEnvelope<T>(schemaVersion, payload)` + `VersionedJsonCodec<T>`. Decode returns `Missing`, `Success(value, migratedFrom?)`, or `Failed(CORRUPT / UNSUPPORTED_VERSION)` — never throws. Reads the header before the payload so a newer payload shape is recognised and ignored safely. |
| `VersionedStorageStore.kt` | One `Storage` key + codec + empty value. `values()` derives from `storage.savedStringFlow(key)` (so cross-tab `storage` events propagate), `update {}` does a read-modify-write, and reads/writes catch throwables (quota `DOMException`, unavailable storage) and report a `BrowserStateEvent`. |
| `BrowserStateEvents.kt` | Non-fatal event sink (`SharedFlow<BrowserStateEvent>` + `userMessages`). Missing data is log-only; corrupt/older/newer/quota/read failures are user-visible messages. Read-side events are deduped so several screens collecting the same store don't spam. |
| `BrowserStateKeys.kt` | The stable `localStorage` keys: `chipbox.favorites`, `chipbox.playlists`. |
| `BrowserStateCodecs.kt` | The current codec instances (`schemaVersion = 1`). Add ticket 07's history/session codecs here. |
| `FavoritesState.kt` | Payload: per-kind `FavoriteEntry(id, favoritedAtMs)` lists; `normalized()` dedupes by id and sorts newest-first. |
| `StorageFavoritesRepository.kt` | `FavoritesRepository` over the store. Re-favoriting refreshes the timestamp; clear writes an empty payload. |
| `PlaylistsState.kt` | Payload: persisted `nextId` + `PlaylistEntry(id, name, createdAtMs, trackIds)`. `normalized()` enforces unique ids/members and `nextId > max(id)`; `newestFirst()` orders the list. |
| `StoragePlaylistsRepository.kt` | `PlaylistsRepository` over the store: stable monotonic ids, newest-created-first list, ordered membership, append de-dup, rename/delete/add/remove/reorder. |
| `Hydration.kt` | `hydrateIds(ids, resolve)` — maps ids to models and drops unknown/missing ids (RSN re-keying, rebuilt libraries). |

## Storage contract (ticket 07 builds on this)

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

Behaviour rules:

- Missing key → empty value (normal; logged, never a user snackbar).
- Corrupt/malformed → empty value + non-fatal message.
- `schemaVersion < current` → payload is decoded if possible and reported as migrated; the next
  write persists the current version.
- `schemaVersion > current` → empty value + non-fatal message (never crash, never reinterpret a
  future payload).
- Quota/read failure → caught, empty/previous value retained, non-fatal message. Writes are
  last-writer-wins across tabs; no merge is promised.

## Why depend on this module

`apps/js` binds the two repositories in `WebModules`; ticket 07's history/session stores should use
`VersionedJsonCodec` + `VersionedStorageStore` rather than growing another envelope implementation.
Desktop/Android keep their Room/DataStore implementations and do not depend on this module.

## Using it

```kotlin
val events = BrowserStateEvents { event -> hatchet.w(event.logMessage) }
val favorites = StorageFavoritesRepository(storage, events)
val playlists = StoragePlaylistsRepository(storage, events)

// A screen observes the storage-backed flow directly:
favorites.favoriteTrackIds().collect { ids -> /* hydrateIds(ids, repository::getTrack) */ }
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `kotlin.serialization` + `chipbox.plugins.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (impl) / `commonTest` (codec + repository tests)
- **SAGE/module dependencies:** `:cbox:common:favorites:api`, `:cbox:common:playlists:api`,
  `:cbox:common:models:api`, `sage.common.storage.common`, `kotlinx-coroutines-core`,
  `kotlinx-serialization-json`

## Tests

```sh
./gradlew :cbox:common:browserstate:jsNodeTest -Psage.js=true \
  -Dorg.gradle.isolated-projects=false -Dorg.gradle.configureondemand=false --no-parallel
```

Covers envelope round-trip, corrupt/missing/older/newer versions, favorites ordering/re-favorite,
playlist ids/order/reorder/append de-dup, cross-tab flow updates, quota/read failures, and
unknown-id hydration.
