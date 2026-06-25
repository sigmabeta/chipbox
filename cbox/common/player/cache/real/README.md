# `:cbox:common:player:cache:real`

> The render-ahead PCM cache — emulate-once to a `.pcm` file, replay instantly, normalize loudness, evict on an LRU cap.

The `:real` implementation of `:cbox:common:player:cache:api`. It provides the
production `PcmTrackSource.Factory` that decides — per track — whether to serve
from a complete cached `.pcm` file or to start a render-ahead session that runs a
native emulator into a cache file while the generator's reader trails it. Depend
on `:api` for the `PcmTrackSource`/`Factory` interfaces; this module is the
production binding, provided to the `AppScope` graph (there is no cache `:di`
module — the factory is wired elsewhere).

## Contents

Grouped by role:

- **Factories** — `RealPcmTrackSourceFactory` (production: emulator-by-extension
  selection, source hashing, cache-hit vs render-ahead decision, janitor wiring)
  and `UncachedPcmTrackSourceFactory` (cache-bypass fallback: live emulator per
  track, no read-through, no writer, no janitor — not wired in today).
- **`PcmTrackSource` implementations** — `CachedFilePcmSource` (cache **hit**:
  random-access reads over a complete file, no emulation), `CachingPcmSource`
  (cache **miss**: a writer coroutine races the emulator into `.pcm.tmp` while the
  reader trails on a separate handle, publishing a `watermark` and `awaitingRender`;
  trims trailing silence, measures loudness, atomically promotes to `.pcm` on
  completion), and `EmulatorPcmSource` (bare live emulator, all native calls
  confined to the emulator dispatcher; `seek` unsupported).
- **On-disk format** — `PcmCacheFile` (okio-backed read/write `object` with nested
  `Writer`/`Reader`/`Header`: writes the 128-byte little-endian header, appends
  body frames, flips the completion flag, atomically renames temp→final; readers
  reject incomplete/mismatched files) and `PcmCacheHasher` (FNV-1a 64-bit hash of
  source bytes **plus** sorted chain files → the key's `sourceHash`).
- **Cache maintenance** — `PcmCacheJanitor` (startup sweep of partial/corrupt
  files, lock-free `inUse` protection set, mtime-LRU cap enforcement) and the
  `CacheFileTouch` `expect`/`actual` set-mtime helper (see source-set facts).
- **Input staging** — `TrackStager` (`stageTrack`: writes source + chain files
  into a per-track dir the native emulator can read; unpacks RSN archive members
  to a staged `.spc`) and `ArchiveStaging` (`playbackExtension` /
  `asStagedPlaybackTrack` helpers keeping RSN's archive-vs-member identity in
  lockstep).
- **Logging** — `LoudnessLog` (the shared one-line LUFS / true-peak / gain report
  emitted by both cache sources).
- **Tests** (`commonTest` + `jvmSharedTest`) — `LoudnessLogTest`,
  `PcmCacheHasherTest`, and JVM-only `CachingPcmSourceTest`,
  `EmulatorPcmSourceConfinementTest`, `PcmCacheFileTest`, `PcmCacheJanitorTest`.

## Why depend on this module

Only the DI/wiring that binds the cache `Factory` into the `AppScope` graph
depends on `:real` directly — it constructs a `RealPcmTrackSourceFactory` from the
emulator list, staging/cache dirs, an okio `FileSystem`, the content-source
registry, and a `Hatchet`. Pipeline code (the generator) depends on `:api`, not
here. Tests that need real cache behaviour (not a `FakePcmTrackSource`) may depend
on `:real`.

## Using it

```kotlin
// Production factory: one instance serves the whole pipeline.
val factory: PcmTrackSource.Factory = RealPcmTrackSourceFactory(
    emulators = emulators,
    stagingDir = stagingDir,
    pcmCacheDir = pcmCacheDir,
    fileSystem = FileSystem.SYSTEM,
    contentSourceRegistry = registry,
    hatchet = hatchet,
)

// open() picks the emulator by extension, hashes the source, then returns either a
// CachedFilePcmSource (hit) or a render-ahead CachingPcmSource (miss) — the caller
// just sees a PcmTrackSource and reads frames.
val source = factory.open(track, fileBytes)
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `chipbox.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain` + `jsMain` + `src/main/java` (`jvmSharedMain`) — the
  `touchLastModified` set-mtime helper is an `expect` in `commonMain` with a
  `java.io.File` `actual` in `src/main/java` (covers both Android and JVM) and a
  no-op `actual` in `jsMain`. Tests live in `commonTest` and `jvmSharedTest`
  (`src/test/java`).
- **SAGE/module dependencies:** `:cbox:common:player:cache:api` (`api`), `:cbox:common:player:emulators:api` (`api`), `okio` (`api`), `:cbox:common:contentsource:api`, `:cbox:common:player:common:api`, `:cbox:common:utils:api`, `kotlinx.coroutines.core`, `sage.common.logging` (all `implementation`); `:cbox:common:player:cache:fake` (`jvmSharedTest` only)
