# `:cbox:common:scanner:real`

> The production library scanner — walks a content source, parses tracks, and
> persists one game per folder.

The `:real` impl of `:cbox:common:scanner:api`. `RealScanner` walks every file a
`LibrarySource` exposes, runs the format `Readers` (PSF `_lib` chain resolution,
m3u overlays, streamed-audio readers), and upserts one `RawGame` per source
folder into the library `Repository`. **Scanning is pure Kotlin and never loads a
native library** — every in-scope streamed format is parsed by a Kotlin reader;
playback still uses `libvgmstream`. It talks to the library only through the
platform-neutral `LibrarySource`/`LibraryFileInfo` interfaces, so the Android and
JVM targets share this same code.

An extension with no registered reader stays a silent debug-level skip unless
`isKnownStreamedExtension(ext)` says the vendored vgmstream build knows the
format: then the file is **skipped, counted and warned about** ("no Kotlin
reader"). A registered reader that returns `emptyList()` (the file is recognised
but has no stream this build can decode — e.g. an FSB5 Vorbis bank) is counted
and warned the same way. The count rides through `Progress.filesSkipped` into
`ScannerState.Scanning`/`Complete` (trailing field, default 0) so the CLI and a
log summary can report it.

The design behind this (format coverage, codec allow-list, exclusions and how to
add a reader) is in
[`arch-docs/architecture/streamed-audio-readers.md`](../../../../arch-docs/architecture/streamed-audio-readers.md).

## Streamed-audio skip reporting

The native vgmstream probe is gone: there is no prober class, no prober
injection, no `vgmstreamSupports` fallback and no unavailability latch. A scan
cannot lose every streamed track because a `.so` failed to load.

Per file, after the PSF fast-path:

1. `readers.forExtension(ext)` — a registered Kotlin reader parses the file.
   - It returns tracks: they are persisted.
   - It returns `null`: the file failed to parse and is counted as failed.
   - It returns `emptyList()`: recognised but unsupported (an external-codec
     stream, an unreadable ACB/AWB, …). If `isKnownStreamedExtension(ext)`, the
     file is warned about and counted as skipped, like step 2.
2. No reader, but `isKnownStreamedExtension(ext)` — the vendored build knows the
   format, so it is a coverage gap. Log a warning naming the extension and file,
   increment the skipped count, and continue.
3. No reader and unknown — an ordinary non-audio file (`.txt`, `.gif`, …) is a
   debug-level skip and is **not** counted.

The count is carried in `Progress.filesSkipped` (included in `plus`/`EMPTY`, and
preserved on the early "yielded no tracks" return path), surfaces as the
trailing `filesSkipped` field on `ScannerState.Scanning`/`Complete`, and is
printed by the CLI and at scan end. `StreamedExtensions.ALL` is the 702-entry
mirror of the vendored build's extension list; the scanner checks registered
readers first, so its overlap with them is inert.

`RealScanner.version = 2` is the cutover: streamed `reader_version`s moved from
the old no-reader sentinel `0` to real versions, and folders containing only
still-unreadable streamed files would otherwise stay skippable at `0 == 0` and
never report. The bump forces one re-read of every existing library.

## Contents

| File | What it is |
| --- | --- |
| `RealScanner.kt` | The scanner. Refuses an overlapping library-location list up front (nested/duplicate roots would emit the same folder twice and race the parallel readers on the unique `game.folder_key` index): the scan ends `Failed` with a path-free message, the log names both roots, and nothing is written or pruned. Streams folders from the content source and reads them concurrently with discovery (bounded parallel), skips unchanged folders via a SHA-256 folder signature *and* matching per-track scanner/reader versions (so a reader fix re-reads old folders), emits live `Scanning` progress + per-folder/per-file/per-game events, counts streamed files with no Kotlin reader, and prunes games whose folders vanished. An optional trailing `rsnExtractor` (server-only, default null) rewrites RSN tracks to extracted `.spc` files after m3u/title fallbacks and prunes orphaned extraction output after a fully successful scan. `version = 2` is the reader cutover: it forces one re-read of libraries written by the native-prober era. |
| `FolderVersionCheck.kt` | `FolderSnapshot.isUpToDate(...)` — the pure version gate: a folder is current only when all its tracks carry the current scanner version and the current reader version for their format. |
| `AvailableProcessors.kt` | `expect fun availableProcessors()` sizing scan parallelism — JVM/Android read `Runtime`; the enforcement-only JS actual returns 1. |

## Why depend on this module

The app graph depends on `:real` to bind the concrete `Scanner` into `AppScope`;
elsewhere depend on `:cbox:common:scanner:api` for the `Scanner` type. `RealScanner`
is constructed with the library `Repository`, a `LibrarySource`, the format
`Readers`, and a `Hatchet` logger — no native probe is injected, and the module
does not depend on `:cbox:common:player:emulators:api` at all. The server
additionally passes an optional `RsnExtractor`; every other target omits it and
keeps archive staging. The scanner passes a folder+filename `source` to
`extract()`; a `DuplicateRsnContentException` becomes a per-file failure (the
archive's tracks are dropped, the scan still completes).

## Library locations must be disjoint

Before it walks, writes, or prunes, the scanner asks the `LibrarySource` for an
overlapping pair of roots (`findOverlappingLocations()`). The same folder twice
or one root inside another is a configuration error: both roots would emit the
shared folders in one pass and the concurrent readers would race on the unique
`game.folder_key` index, aborting the scan and leaving the stored library stale.
The refusal ends the scan `Failed(CONFIGURATION)` with a message that names the
problem but no host path: the public `/api/health` body reports `ok = false` and
leaves `failedFile` absent, because there is no failing file. The log names both
configured roots in full, and the scan writes/prunes nothing. Remove one of the
roots and rescan.

## Using it

```kotlin
val scanner: Scanner = RealScanner(
    repository = repository,
    librarySource = librarySource,
    readers = readers,
    hatchet = hatchet,
)
scanner.startScan()   // walks librarySource, persists RawGames, emits state()/scanEvents()
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (with per-target `availableProcessors` actuals in `src/main/java` and `src/jsMain`)
- **Tests:** `commonTest` — folder-version gate, and the overlapping-locations refusal (`OverlappingLocationsScanTest`, against `contentsource:fake` + `repository:fake`); `jvmTest` — the unchanged-folder skip gate plus the skipped-file reporting seam (`RealScannerSkipTest`: a known streamed extension with no reader, and a registered reader that yields no supported stream, both counted and reported live).
- **SAGE/module dependencies:** `:cbox:common:scanner:api`, `:cbox:common:repository:api`, `:cbox:common:contentsource:api`, `:cbox:common:readers:api`, `:cbox:common:perf:api`, `:cbox:common:utils:api`, `okio`, `sage.common.logging`
