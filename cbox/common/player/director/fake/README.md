# `:cbox:common:player:director:fake`

> `FakeDirector` — a test-only `Director` you push state into and inspect for requests.

The `:fake` module for the director: an in-memory `Director` implementation for
tests that consume the director's flows or assert what was requested, without
dragging a real generator + speaker + repository pipeline in. Depend on it from a
module's `commonTest`.

## Contents

| File | What it is |
| --- | --- |
| `FakeDirector.kt` | `open class FakeDirector : Director`. The five state flows are `MutableSharedFlow`s (replay = 1 on metadata/playback/session/setlist, seeded with a "nothing playing" baseline in `init` to mirror `RealDirector`; the error sink has `replay = 0`, capacity 8, `DROP_OLDEST`). Tests drive emissions via `emitMetadata` / `emitPlayback` (state-only overload too) / `emitSession` / `emitSetlist(ids, activeIndex)` / `emitErrorSink`. `request()` records into a `requests` list, with convenience views (`playCalls`, `pauseCalls`, `skipForwardCalls`, `seekCalls`, `playPositionCalls`, `reorderCalls`, `setShuffledCalls`, `restoreCalls`, …) for assertions. |

## Why depend on this module

Depend on `:cbox:common:player:director:fake` from `commonTest` whenever a unit
test needs a `Director` it controls — e.g. ViewModel tests
(`PlayerStatusViewModel`, now-playing, queue) that subscribe to the state flows,
or the session-persistence tests that assert the right `SessionRequest`s were
submitted. It pairs with `:api`'s types and stands in for `:real`.

## Using it

```kotlin
val director = FakeDirector()
val vm = PlayerStatusViewModel(director, BluntHatchet())

director.emitMetadata(track)
director.emitPlayback(PlayerState.PLAYING)

vm.sendAction(PlayerStatusAction.PlayPauseClicked)
assertEquals(1, director.pauseCalls)
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `:cbox:common:player:director:api`, `:cbox:common:player:common:api`, `:cbox:common:models:api` (all `api`); `kotlinx.coroutines.core` (`implementation`)
