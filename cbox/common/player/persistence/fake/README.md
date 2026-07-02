# `:cbox:common:player:persistence:fake`

> `FakePlaybackSessionStore` — an in-memory store that records every save/clear.

The `:fake` module for playback-session persistence: an in-memory
`PlaybackSessionStore` for tests of the persister (or anything that reads/writes a
snapshot) without touching DataStore. Depend on it from `commonTest`.

## Contents

| File | What it is |
| --- | --- |
| `FakePlaybackSessionStore.kt` | `PlaybackSessionStore` holding a single `stored: SessionSnapshot?` (seedable via the `initial` ctor arg). `save` appends to `saveCalls` and updates `stored`; `clear` bumps `clearCalls` and nulls `stored`; `load` returns `stored`. The recorded lists/counters are what assertions inspect. |

## Why depend on this module

Depend on `:cbox:common:player:persistence:fake` from `commonTest` to verify what
(and how often) a `PlaybackSessionPersister` saved or cleared — it's the store
double `:persistence:real`'s tests pair with `FakeDirector`. Stands in for `:real`.

## Using it

```kotlin
val store = FakePlaybackSessionStore()
val persister = RealPlaybackSessionPersister(fakeDirector, store, BluntHatchet())

// …drive the director through PLAYING → PAUSED…
assertEquals(1, store.saveCalls.size)
assertEquals(expectedPositionMs, store.stored?.positionMs)
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `:cbox:common:player:persistence:api` (`api`); `kotlinx.coroutines.core` (`implementation`, resolves the coroutines opt-in marker the re-exported types carry)
