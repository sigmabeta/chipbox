# `:cbox:common:player:cache:fake`

> A scriptable, in-memory `PcmTrackSource` for driving the pipeline in tests without an emulator or cache file.

The `:fake` module for the playback pipeline's **Cache** stage: an in-memory
test double of `:cbox:common:player:cache:api`'s `PcmTrackSource`. Being a
`:fake`, it carries call-recording, scriptable stand-ins for tests and previews —
not production code. (The *production* fakes — the synth-backed cache sources tied
to `PcmCacheFile` internals — live in `:real`, not here.)

## Contents

| File | What it is |
| --- | --- |
| `FakePcmTrackSource.kt` | A `PcmTrackSource` whose frames come from an enqueued `Chunk` queue (`Audible(frames, pattern)` / `Silent(frames)`). `enqueueAudible`/`enqueueSilent` script the stream; oversized chunks split across `readFrames` calls; `setLastError` injects a failure; `parkOnEmpty()` makes the next read past the queue suspend cancellably (to exercise a caller's cancel-mid-read path); `seek` throws; `closed` records teardown. |

## Why depend on this module

Depend on `:cache:fake` from a test (or preview) source set that needs to feed
the generator or a cache source real-looking PCM without standing up a native
emulator or touching the filesystem. `:cbox:common:player:cache:real` already
pulls it into its `jvmSharedTest` set, where `CachingPcmSourceTest` wraps a
`FakePcmTrackSource` as the "emulator" behind a `CachingPcmSource` and uses
`parkOnEmpty()` to drive the writer-loop cancel path. Pipeline and production code
depend on `:api`; only tests depend on `:fake`.

## Using it

```kotlin
val source = FakePcmTrackSource(sampleRate = 44_100)
source.enqueueAudible(frames = 4_096, pattern = 1_000)
source.enqueueSilent(frames = 2_048)
source.parkOnEmpty()              // optional: suspend (cancellably) once drained

val buffer = ShortArray(1_024 * 2)
val read = source.readFrames(buffer)   // up to buffer.size / 2 frames
// ... assert on `read`, source.isOver, source.getLastError() ...
source.close()
assert(source.closed)
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `:cbox:common:player:cache:api` (`api`), `kotlinx.coroutines.core` (`implementation`)
