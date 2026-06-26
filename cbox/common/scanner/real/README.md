# `:cbox:common:scanner:real`

> The production library scanner — walks a content source, parses tracks, and
> persists one game per folder.

The `:real` impl of `:cbox:common:scanner:api`. `RealScanner` walks every file a
`LibrarySource` exposes, runs the format `Readers` (PSF `_lib` chain resolution,
m3u overlays, vgmstream fallback for streamed audio), and upserts one `RawGame`
per source folder into the library `Repository`. Pure Kotlin: it talks to the
library only through the platform-neutral `LibrarySource`/`LibraryFileInfo`
interfaces, so the Android and JVM targets share this same code.

## Contents

| File | What it is |
| --- | --- |
| `RealScanner.kt` | The scanner. Groups discovered files by parent folder, skips unchanged folders via a SHA-256 folder signature, parses tracks in bounded parallel, emits live `Scanning` progress + per-folder/per-file/per-game events, and prunes games whose folders vanished. |
| `VgmstreamPlatform.kt` | `platformForVgmstreamExtension` — best-effort `Platform` for vgmstream-decoded files (maps unambiguous PSX extensions, else `OTHER`); metadata only. |
| `AvailableProcessors.kt` | `expect fun availableProcessors()` sizing scan parallelism — JVM/Android read `Runtime`; the enforcement-only JS actual returns 1. |

## Why depend on this module

The app graph depends on `:real` to bind the concrete `Scanner` into `AppScope`;
elsewhere depend on `:cbox:common:scanner:api` for the `Scanner` type. `RealScanner`
is constructed with the library `Repository`, a `LibrarySource`, the format
`Readers`, and a `VgmstreamProber` (the native subsong probe, injected so this
module stays platform-neutral).

## Using it

```kotlin
val scanner: Scanner = RealScanner(
    repository = repository,
    librarySource = librarySource,
    readers = readers,
    vgmstreamProbe = vgmstreamProber,
    hatchet = hatchet,
)
scanner.startScan()   // walks librarySource, persists RawGames, emits state()/scanEvents()
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain` (with per-target `availableProcessors` actuals in `src/main/java` and `src/jsMain`)
- **SAGE/module dependencies:** `:cbox:common:scanner:api`, `:cbox:common:repository:api`, `:cbox:common:contentsource:api`, `:cbox:common:readers:api`, `:cbox:common:perf:api`, `:cbox:common:utils:api`, `:cbox:common:player:emulators:api`, `okio`, `sage.common.logging`
