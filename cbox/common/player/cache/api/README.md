# `:cbox:common:player:cache:api`

> The cache stage's public contract — the `PcmTrackSource` abstraction and its on-disk `.pcm` format constants.

The `:api` module for the playback pipeline's **Cache** stage: it defines
`PcmTrackSource` (the frame-addressable PCM source the generator reads from), its
`PcmTrackSource.Factory` seam, and the cache-file identity/format types
(`PcmCacheKey`, `PcmCacheFormat`). Being an `:api`, it carries interfaces and
types only — depend on it to consume the cache service; the production
implementation lives in `:cbox:common:player:cache:real` and the
call-recording test doubles in `:cbox:common:player:cache:fake`.

## Contents

| File | What it is |
| --- | --- |
| `PcmTrackSource.kt` | The core interface: `sampleRate`, `totalFrames`, `cachedFrames`, `loudnessLufs`/`truePeakDbtp`, `suspend readFrames(buffer)`/`seek(frame)`, `isOver`, `awaitingRender`, `getLastError`/`getDiagnostics`, `suspend close`. Nested `Factory` (with `requiresContent`) resolves a `Track` + its bytes to the right backing source. |
| `PcmCacheKey.kt` | `data class PcmCacheKey(sourceHash, trackNumber, sampleRate)` — the cache entry's stable identity, plus `filename()`/`tempFilename()` deriving the `.pcm`/`.pcm.tmp` names. |
| `PcmCacheFormat.kt` | `object` of on-disk layout constants for a `.pcm` file: `MAGIC` (`"CBPC"`), `VERSION`, fixed `HEADER_SIZE_BYTES` (128), `CHANNELS`/`BITS_PER_SAMPLE`, `BYTES_PER_FRAME`, the in-progress/complete flags, and the source-hash field width. |
| `PcmCacheKeyTest.kt` (`commonTest`) | Pins the filename derivation and that `trackNumber`/`sampleRate` are part of cache identity. |

## Why depend on this module

Depend on `:cache:api` when your code needs to read decoded PCM for a track or
construct cache identities: `RealGenerator` opens a `PcmTrackSource` via the
injected `PcmTrackSource.Factory` and drives `readFrames`/`seek`/`isOver`, while
`cachedFrames`/`loudnessLufs`/`truePeakDbtp` surface up through the director's
`ChipboxPlaybackState`. The `Factory` interface is the seam: generic generator
code depends on `:api` and never sees whether it got a cache hit, a render-ahead
writer, or a bare emulator. Depend on `:api` for the types; the app wires
`:real` into the `AppScope` graph (the factory is provided there — there is no
cache `:di` module), and tests pull in `:fake`.

## Using it

```kotlin
// Generator side: open one source per track, then read frames sequentially.
val source: PcmTrackSource = factory.open(track, fileBytes)
val rate = source.sampleRate
val buffer = ShortArray(frameCapacity * 2) // interleaved L/R

while (!source.isOver) {
    val frames = source.readFrames(buffer)
    if (frames == 0) {
        if (source.awaitingRender) continue   // writer hasn't caught the cursor yet
        source.getLastError()?.let { /* abort */ }
    }
    // ... hand frames + source.loudnessLufs/truePeakDbtp downstream ...
}
source.close()

// Cache identity for an entry on disk:
val key = PcmCacheKey(sourceHash = hash, trackNumber = 0, sampleRate = rate)
val onDisk = key.filename()       // "<hash>-0-<rate>.pcm"
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (+ `commonTest`)
- **SAGE/module dependencies:** `:cbox:common:models:api` (`api`), `:cbox:common:player:buffer:api` (`api`), `kotlinx.coroutines.core` (`implementation`)
