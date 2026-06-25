# `:cbox:common:player:persistence:api`

> Contracts for saving and restoring the last-played session across app launches.

The `:api` module for playback-session persistence: the two interfaces and the one
serializable snapshot type used to remember "what was playing, and where" and
resume it next launch. Production impls live in
`:cbox:common:player:persistence:real`, are wired via
`:cbox:common:player:persistence:di`, and an in-memory store double lives in
`:cbox:common:player:persistence:fake`.

## Contents

| File | What it is |
| --- | --- |
| `SessionSnapshot.kt` | `@Serializable data class` — the minimal tuple to rebuild and resume a `Session`: `type` + `contentId` (+ ad-hoc `explicitSetlist`/`sourceName`), `currentTrackId` (resume by id, not index, so shuffle survives), `shuffled`, `repeatMode`, `positionMs`, `resolvedSetlist` (verbatim play order at save time), and `modified`. JSON-encoded into DataStore. |
| `PlaybackSessionStore.kt` | Durable store of the single snapshot: `suspend load()`, fire-and-forget `save(snapshot)`, and `clear()`. At most one snapshot — `save` overwrites, `clear` drops. |
| `PlaybackSessionPersister.kt` | Bridges the `Director` and the store: `suspend restore()` (load + restore paused on launch), `observe()` (write on pause, clear on stop), and `snapshotNow()` (synchronous teardown net for "closed while still playing"). |

## Why depend on this module

Depend on `:cbox:common:player:persistence:api` to read or write the saved session
without binding to DataStore — chiefly the Android playback service, which owns the
director's lifecycle and drives `restore()`/`observe()`/`snapshotNow()`. The app
wires `:real` via `:di`; tests substitute `:fake` for the store.

## Using it

```kotlin
class PlaybackService(private val persister: PlaybackSessionPersister) {
    fun onCreate() {
        persister.observe()
        scope.launch { persister.restore() }
    }
    fun onDestroy() = persister.snapshotNow() // safety net if never paused
}
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `kotlin.serialization`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `:cbox:common:player:common:api` (`api`, re-exports `SessionType`/`RepeatMode`), `kotlinx.serialization.core` (`api`)
