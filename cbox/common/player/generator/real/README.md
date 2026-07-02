# `:cbox:common:player:generator:real`

> `RealGenerator` — the production producer that decodes tracks through native emulators and a render-ahead PCM cache.

The `:real` implementation of `:cbox:common:player:generator:api`. A thin
`BaseGenerator` subclass that supplies a `RealPcmTrackSourceFactory` over the
available native emulators (selected per-track by file extension) with input
staging and the render-ahead PCM cache. Depend on `:api` for the interface; this
module is the production binding the app wires.

## Contents

| File | What it is |
| --- | --- |
| `RealGenerator.kt` | Subclasses `BaseGenerator` and sets `pcmSourceFactory` to a `RealPcmTrackSourceFactory`. Constructor takes the `Repository`, `ContentSourceRegistry`, `ProducerBufferManager`, the list of `Emulator`s, the staging + PCM-cache dirs as okio `Path`s, the `FileSystem`, a `Hatchet`, and an optional dispatcher (default `ioDispatcher`). |

The cache directories arrive as plain `Path` params (the Android graph derives
them from `Context.cacheDir` with `FileSystem.SYSTEM`; the JVM app passes a work
dir and the same filesystem), so this is one `sage.kmp` module for both targets —
no platform seam. The decode-loop machinery all lives in `BaseGenerator` (`:api`);
the actual caching/emulation work lives in the pure-JVM
`:cbox:common:player:cache:real`.

## Why depend on this module

The app graph depends on `:real` to construct the production `RealGenerator` and
bind it as the `Generator`. Pipeline/UI code depends on `:api`, not here. The dev
synth swap (`SynthGenerator`) is in `:fake`, not this module.

## Using it

```kotlin
val generator: Generator = RealGenerator(
    repository = repository,
    contentSourceRegistry = registry,
    bufferManager = producerBufferManager,
    emulators = emulators,            // native cores, picked per-track by extension
    stagingDir = stagingDir,          // okio Path
    pcmCacheDir = pcmCacheDir,        // okio Path
    fileSystem = FileSystem.SYSTEM,
    hatchet = hatchet,
)
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `:cbox:common:player:generator:api`, `:cbox:common:player:emulators:api`, `:cbox:common:player:cache:real` (all `api`); `:cbox:common:utils:api` (`implementation`)
