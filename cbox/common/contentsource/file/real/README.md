# `:cbox:common:contentsource:file:real`

> The local-filesystem `LibrarySource` — identifies files and library roots by
> absolute path.

The `:real` filesystem impl of `:cbox:common:contentsource:api`.
`LocalFileContentSource` is an okio-backed `LibrarySource` (source id `"file"`):
it walks library roots, opens bytes by absolute path, and persists the user's
locations as a newline-separated text file. It lives in `commonMain` and does all
I/O through an injected `okio.FileSystem`, so the JVM/desktop app, the Android
app, the CLI and the server share it — Android uses `MANAGE_EXTERNAL_STORAGE` +
raw paths rather than SAF, so every target drives the same walker (and the same
shared `RealScanner`).

## Contents

| File | What it is |
| --- | --- |
| `LocalFileContentSource.kt` | The `LibrarySource`. `scanFolders()` is a depth-first walk that emits each directory as a `LibraryFolderInfo` (the dir + its direct files) the moment it's enumerated, so reading overlaps discovery; `openBytes()` reads a file by path; add/remove-location validate directories and persist the list to `locationsFile`. Also `addLocation(Path)`. Internal `SOURCE_ID = "file"`. Add-time and scan-time overlap checks canonicalize through the injected `FileSystem` (resolving `.`/`..`, trailing separators, symlinks, case aliases) — `addLibraryLocation` refuses an overlapping root, and `findOverlappingLocations()` reports a hand-edited locations file. Stored identifiers keep the form the user picked, so folder keys never change under a rescan. |
| `LocalFileContentSourceLocationsTest.kt` (`commonTest`) | The disjointness cases over a `FakeFileSystem`: nested root, containing root, exact duplicate, sibling roots, a symlink alias, `.`/trailing separators, and the refused-add never persisting. |

## Why depend on this module

Each platform's DI depends on `:real` to bind `LocalFileContentSource` as the
`LibrarySource`/`ContentSource` (picking the `locationsFile`: `workDir` on the
JVM, `context.filesDir` on Android). Elsewhere depend on
`:cbox:common:contentsource:api` for the interface; tests use
`:cbox:common:contentsource:fake`.

## Library locations must be disjoint

A folder equal to an existing root, inside one, or containing one is refused at
add time and again at scan start, so a hand-edited `locationsFile` cannot produce
a double-walk of the same folder (which would duplicate folder keys and race the
scanner's parallel readers on the unique `game.folder_key` index). Two distinct
paths that resolve to the same folder (a symlink, `..`, a trailing separator) are
detected via canonicalization. Known limit: bind mounts and case-folded aliases
that `canonicalize` cannot resolve may still produce duplicate entries — benign
duplicate games, not row corruption.

## Using it

```kotlin
val source = LocalFileContentSource(locationsFile = File(workDir, "library-locations.txt"))
source.addLocation(File("/storage/emulated/0/Music"))

source.scanFolders().collect { folder -> /* feed RealScanner — read while later folders discover */ }
val bytes = source.openBytes("/storage/emulated/0/Music/game/track.spc")
```

## Module facts

- **Plugin:** `sage.kmp`
- **Targets:** Android + JVM
- **Source set:** `src/commonMain` (okio-backed; no `java.io`)
- **SAGE/module dependencies:** `:cbox:common:contentsource:api`, `sage.common.logging`, `okio` (tests: `okio-fakefilesystem`)
