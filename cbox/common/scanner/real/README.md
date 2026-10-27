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
| `RealScanner.kt` | The scanner. Refuses an overlapping library-location list up front (nested/duplicate roots would emit the same folder twice and race the parallel readers on the unique `game.folder_key` index): the scan ends `Failed` with a path-free message, the log names both roots, and nothing is written or pruned. Streams folders from the content source and reads them concurrently with discovery (bounded parallel), skips unchanged folders via a SHA-256 folder signature *and* matching per-track scanner/reader versions (so a reader fix re-reads old folders), emits live `Scanning` progress + per-folder/per-file/per-game events, and prunes games whose folders vanished. An optional trailing `rsnExtractor` (server-only, default null) rewrites RSN tracks to extracted `.spc` files after m3u/title fallbacks and prunes orphaned extraction output after a fully successful scan. |
| `FolderVersionCheck.kt` | `FolderSnapshot.isUpToDate(...)` — the pure version gate: a folder is current only when all its tracks carry the current scanner version and the current reader version for their format. |
| `VgmstreamPlatform.kt` | `platformForVgmstreamExtension` — best-effort `Platform` for vgmstream-decoded files (maps unambiguous PSX extensions, else `OTHER`); metadata only. |
| `AvailableProcessors.kt` | `expect fun availableProcessors()` sizing scan parallelism — JVM/Android read `Runtime`; the enforcement-only JS actual returns 1. |

## Why depend on this module

The app graph depends on `:real` to bind the concrete `Scanner` into `AppScope`;
elsewhere depend on `:cbox:common:scanner:api` for the `Scanner` type. `RealScanner`
is constructed with the library `Repository`, a `LibrarySource`, the format
`Readers`, and a `VgmstreamProber` (the native subsong probe, injected so this
module stays platform-neutral). The server additionally passes an optional
`RsnExtractor`; every other target omits it and keeps archive staging. The
scanner passes a folder+filename `source` to `extract()`; a
`DuplicateRsnContentException` becomes a per-file failure (the archive's tracks
are dropped, the scan still completes).

## Library locations must be disjoint

Before it walks, writes, or prunes, the scanner asks the `LibrarySource` for an
overlapping pair of roots (`findOverlappingLocations()`). The same folder twice
or one root inside another is a configuration error: both roots would emit the
shared folders in one pass and the concurrent readers would race on the unique
`game.folder_key` index, aborting the scan and leaving the stored library stale.
The refusal ends the scan `Failed` with a message that names the problem but no
host path (the public `/api/health` body exposes only that message), logs both
configured roots in full, and writes/prunes nothing. Remove one of the roots and
rescan.

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
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (with per-target `availableProcessors` actuals in `src/main/java` and `src/jsMain`)
- **Tests:** `commonTest` — folder-version gate, vgmstream platform mapping, and the overlapping-locations refusal (`OverlappingLocationsScanTest`, against `contentsource:fake` + `repository:fake`)
- **SAGE/module dependencies:** `:cbox:common:scanner:api`, `:cbox:common:repository:api`, `:cbox:common:contentsource:api`, `:cbox:common:readers:api`, `:cbox:common:perf:api`, `:cbox:common:utils:api`, `:cbox:common:player:emulators:api`, `okio`, `sage.common.logging`
