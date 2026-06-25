# `:cbox:common:readers:api`

> The chiptune file-format parsers — turn raw SPC/NSF/PSF/VGM/… bytes into `RawTrack` metadata.

An `:api` module, but a *fat* one: rather than holding only interfaces and types,
it ships the actual readers that parse chiptune file headers and embedded tags.
These run during a library scan, decoding each file's bytes into the
`RawTrack`/`RawGame`-style metadata the scanner persists. The single
multiplatform contract is `Reader.readTracksFromFile(bytes, identifier)`, which
returns `List<RawTrack>?` (null on a parse miss).

## Contents

This module is large; files group by role:

- **Dispatch / entry point** — `Readers.kt`: holds one instance of each reader and
  `forExtension(extension)` maps a file extension to the right `Reader`; also the
  `sealed class Reader` contract and `isPsfFamily(extension)`. The PSF *family*
  (`psf`/`minipsf`, `gsf`, `psf2`, `2sf`, `ssf`, `dsf`, `usf`, `ncsf` and their
  `mini*` variants) all route to one `PsfReader`.
- **Byte plumbing** — `ByteReader.kt` (internal little-endian, random-access reader
  over a `ByteArray`; a multiplatform stand-in for `java.nio.ByteBuffer`),
  `ReaderUtils.kt` (positioned read helpers, Latin-1/UTF decode, tag
  normalization `orUnknown`/`orNullIfBlank`, time-string parsing, and the public
  `deriveMetaFromFilename` / `FilenameMeta`).
- **Gzip (expect/actual)** — `Gzip.kt`: `internal expect fun gunzip(...)` in
  `commonMain`, with JVM/Android actual in `src/main/java` (`java.util.zip`), and
  a JS actual in `jsMain` (no-op — `.vgz` unsupported on the enforcement-only JS
  target). Used by `VgmReader` to inflate `.vgz`.
- **Format readers** — `PsfReader.kt` (PSF family + `_lib` chain tags →
  `PsfTagInfo`), `NsfReader.kt`, `NsfeReader.kt` (chunked NSFe), `GbsReader.kt`,
  `SpcReader.kt` (ID666 / xid6 → `SpcTags`), `RsnReader.kt` (unpacks the RAR
  archive in Kotlin and delegates each member to `SpcReader`), `VgmReader.kt`
  (VGM/VGZ with GD3 tags). Each parses headers/tags and emits `RawTrack`s.
- **Playlist reader** — `M3uReader.kt`: parses `.m3u` subtune playlists into
  `List<M3uEntry>` (note: this one returns `M3uEntry`, not `RawTrack` — its
  per-track overrides are merged onto a game's tracks elsewhere).
- **Detekt baseline** — `detekt-baseline.xml` suppresses pre-existing findings in
  the ported reader code.

## Why depend on this module

Depend on `:cbox:common:readers:api` to identify and decode a chiptune file's
metadata — i.e. from the library scanner. It `api`-exposes
`:cbox:common:repository:api` so the `RawTrack` type readers return comes along
transitively. There is no separate `:real`/`:di` split here: construct `Readers`
directly with a `Hatchet` logger.

## Using it

```kotlin
val readers = Readers(hatchet)

fun scan(bytes: ByteArray, path: String, extension: String): List<RawTrack> {
    val reader = readers.forExtension(extension) ?: return emptyList()
    return reader.readTracksFromFile(bytes, identifier = path).orEmpty()
}

// .m3u subtune playlists are parsed separately into per-track overrides:
val entries: List<M3uEntry> = readers.m3u.parse(m3uBytes)
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain` (readers + `ByteReader`/`ReaderUtils`/`Gzip` expect);
  `src/main/java` (`jvmSharedMain`) and `src/jsMain` hold the `gunzip` actuals
- **SAGE/module dependencies:** `api` `:cbox:common:repository:api` (the `RawTrack`
  type); `implementation` `:cbox:common:models:api`, `:cbox:common:utils:api`,
  `sage.common.logging` (`Hatchet`)
