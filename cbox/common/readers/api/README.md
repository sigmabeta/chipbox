# `:cbox:common:readers:api`

> The chiptune and streamed-audio file-format parsers — turn raw SPC/NSF/PSF/VGM/STRM/… bytes
> into `RawTrack` metadata.

An `:api` module, but a *fat* one: rather than holding only interfaces and types,
it ships the actual readers that parse chiptune and streamed-audio file headers
and embedded tags.
These run during a library scan, decoding each file's bytes into the
`RawTrack`/`RawGame`-style metadata the scanner persists. The single
multiplatform contract is `Reader.readTracksFromFile(bytes, identifier)`, which
returns `List<RawTrack>?` (null on a parse miss). Each reader also exposes a
`version` — bump it when a change alters the tracks a file yields, so the scanner
re-reads files parsed by the old logic (`Readers.readerVersionFor(extension)`
resolves it per format for the skip check).

The streamed-audio readers' contract, format table, codec allow-list and
"add a format" checklist live in
[`arch-docs/architecture/streamed-audio-readers.md`](../../../../arch-docs/architecture/streamed-audio-readers.md).

## Contents

This module is large; files group by role:

- **Dispatch / entry point** — `Readers.kt`: holds one instance of each reader and
  `forExtension(extension)` maps a file extension to the right `Reader`; also the
  `sealed class Reader` contract and `isPsfFamily(extension)`.
  `StreamedExtensions.kt` mirrors the vendored vgmstream build's supported
  extensions (minus its generic "common" ones) as a sorted set, with
  `isKnownStreamedExtension(extension)` — the scanner's replacement for the
  deleted native prober's `isSupported`, used to report "this is a streamed
  format we can't read yet" instead of silently skipping it. The provenance
  comment records how to regenerate it from the host build. The set
  deliberately overlaps registered readers (adx/brstm/spc/vgm/…); the scanner
  dispatches `forExtension` first. The PSF *family*
  (`psf`/`minipsf`, `gsf`, `psf2`, `2sf`, `ssf`, `dsf`, `usf`, `ncsf` and their
  `mini*` variants) all route to one `PsfReader`.
- **Byte plumbing** — `ByteReader.kt` (internal random-access reader over a
  `ByteArray`; a multiplatform stand-in for `java.nio.ByteBuffer`). Every integer
  read names its endianness (`readShortLe`/`readShortBe`, `readIntLe`/`readIntBe`,
  `readLongLe`/`readLongBe`) and has an absolute, cursor-preserving `…At` variant;
  fixed-width reads throw on underflow, while `readBytes` returns null without
  advancing. `ReaderUtils.kt` holds positioned read helpers, Latin-1/UTF decode,
  tag normalization `orUnknown`/`orNullIfBlank`, time-string parsing, and the
  public `deriveMetaFromFilename` / `FilenameMeta`.
- **Magic checks** — `ByteMagic.kt`: `ByteArray.hasAsciiAt` / `hasAsciiPrefix` and
  the internal `ByteReader` equivalents, for header/id sniffing without per-reader
  `decodeToString()` comparisons.
- **Streamed-audio conventions** — `StreamedAudio.kt`:
  `subsongTrackNumber(index)` maps a 0-based subsong index to the **1-based**
  `RawTrack.trackNumber` streamed readers must emit (playback passes it to
  vgmstream's `setTrackNumber`, and `(path, trackNumber)` is the DB identity).
  This is the deliberate exception to the chiptune readers' 0-based numbering.
  `platformForStreamedExtension(extension)` is the public extension→`Platform`
  fallback (`xa`/`vag`/`vb`/`vh`/`vab`/`seq`/`vpk` → PSX, otherwise OTHER),
  moved here from the scanner. `streamedLengthMs(...)` reproduces the native
  probe's length policy (a looping stream plays its intro, **two** loop passes
  and a 10 s fade; non-looping plays its sample count; integer millisecond
  truncation), and `streamedRawTrack(...)` builds the shared single-track shape
  (1-based number, `Unknown` tags, zero fade, platform passed explicitly — OTHER
  unless the format implies a console, never parsed from the identifier).
  `readStreamedTracks(...)` is the shared try-parse/catch wrapper those readers
  use to turn `ByteReader` throws into a logged `null`.
  `NintendoStreams.kt` holds the Nintendo-DSP-family plumbing: `DspStreamInfo`
  (rate, loop flag/points, sample count), the big/little-endian
  `ByteReader.readU16/readS16/readU32/readS32` helpers used when a BOM decides
  the file's endianness, `dspNibblesToSamples` (DSP loop offsets are in
  nibbles), and a `dspTrack` wrapper over `streamedLengthMs`/`streamedRawTrack`.
- **Codec allow-list** — `StreamedCodecs.kt`: the single source of truth for
  which streamed-audio codecs the vendored `libvgmstream` build actually
  compiles in. One `ALLOWED_CODECS` map is keyed by `StreamedCodecs.Format`
  (`FSB5`, `EA_SCHL`, `GENH`) and read through
  `isCodecAllowed(format, codec)`; there are no per-format predicates to drift.
  FSB5 allows PCM8/16/float, GCADPCM, IMA ADPCM, VAG, HEVAG and FADPCM, and
  rejects every FMOD codec that needs an external library (XMA, MPEG, CELT,
  ATRAC9, XWMA, Vorbis, Opus) plus PCM24/PCM32, which the vendored
  `meta/fsb5.c` fails outright. EA SCHl is the narrower set — PCM8/16 LE/BE
  (split and interleaved), EA-XA (stereo and split mono) and EA-ADPCM/MicroTalk
  MT10/MT5 — rejecting MPEG Layer II/III + EA Layer 3 (`VGM_USE_MPEG`),
  ATRAC3plus (`VGM_USE_FFMPEG`), EAAC, and the built-in but out-of-scope
  VAG/VADPCM/IMA/GCADPCM/Xbox-IMA codecs. GENH allows every `genh.c` case not
  behind a `VGM_USE_*` guard (`0..7`, `9..17`, `24..27`) and rejects MPEG `8`,
  the FFmpeg-backed `18..23` and AAC `28`. The KDoc records the mirroring check
  against `cbox/native/vgmstream/CMakeLists.txt` + `src/vgmstream.h:6-18`.
- **Streamed-audio readers** — header-only parsers for vgmstream formats, each
  registered by extension and each still leaving playback to libvgmstream:
  - `StrmReader.kt` — NDS STRM: PCM8/PCM16/IMA, loop flag, 1–2 channels.
  - `GenhReader.kt` — GENH generic wrapper: header-size/offset gating with
    0 → 0x800, explicit sample count vs loop-end fallback, and a codec gate that
    yields `emptyList()` for an id outside the vendored `genh.c` switch.
  - `SebReader.kt` — SEB/GMS: inverted loop flag, mono/stereo.
  - `SadlReader.kt` — SADL: IMA `0x00`/`0x70` and NDS Procyon `0xB0` branches,
    rate/flags decoding. None of these four formats carries a stream name, so
    they emit `Unknown` titles and the scanner's filename fallback produces
    today's titles.
  - `XaReader.kt` — PSX/CD-i CD-XA (`.xa`) from raw 2352-byte mode2/form2 CD
    sectors or a RIFF/CDXA wrapper (audio at 0x2C). Walks sectors like
    vgmstream's `xa_read_subsongs`: one track per interleaved file+channel
    subsong (1-based via `subsongTrackNumber`), the end-of-file submode closes a
    subsong, channel-0xFF padding and non-audio submodes are skipped. Length is
    `xa_bytes_to_samples` + truncating `samples*1000/rate`, with `platform =
    PSX`, `fadeLengthMs = 0`, and the file+channel marker as the title only when
    the file has several subsongs (else `TAG_UNKNOWN`, so the scanner falls back
    to the filename).
  - `Fsb5Reader.kt` — FMOD Studio sample banks (`.fsb`/`.fsb5`): one `RawTrack`
    per subsong (1-based `trackNumber`), titles from the bank's name table,
    lengths from the sample headers under the native probe's 2-loop +
    10-second-fade policy, and — because the vendored build decodes only a
    subset of FMOD codecs — no tracks at all for a bank whose codec
    `StreamedCodecs` rejects.
  - `AdxReader.kt` — CRI ADX (`.adx`): validates the `0x8000` signature,
    `(c)CRI` marker and encoding/frame/bits/channels/rate, then reads sample
    rate, sample count and the generation-specific loop block (`0x0300` fixed
    offset, `0x0400` after the per-channel history with an optional `AINF` block
    skipped, `0x0500` no loop). Encrypted `0x0408`/`0x0409` headers use the
    `0x0400` layout and parse with no key. Length is the vgmstream play count
    (2 loops + 10 s fade when looped, else sample count), `trackNumber` is
    `subsongTrackNumber(0)`, and `platform` comes from
    `platformForStreamedExtension("adx")` (OTHER). Bad/truncated headers yield
    `null`, never a throw.
  - `EaSchlReader.kt` — EA SCHl (`asf`/`lasf`/`chk`/`eam`/`exa`/`sng`/`aud`/`sx`/
    `stm`/`hab`/`xsf`; the ambiguous `xa`/`strm`/`gsf`/`snd`/`r`/`str` entries
    stay with their existing owners): the standard header at 0x00 plus the
    patch-encoded fields (platform entry `PT`/`GSTR`, version, codec1/codec2,
    channels, rate, sample count, loop start/end) with upstream's per-platform
    defaults, then a safe walk of the `SCCl`/`SCDl`/`SCLl`/`SCEl` block chain.
    One track per audio-header block — one for a standard file, one per localized
    `SHxx` header in a multi-language bank (1-based numbers). Length is the
    vgmstream play count under the shared loop policy, `fadeLengthMs = 0`,
    titles are `Unknown` (the meta carries no stream name) and the platform is
    `OTHER` (the served extensions imply no console). Codecs outside the EA SCHl
    allow-list (MPEG/EAAC/ATRAC3plus, plus the out-of-scope built-ins) yield
    `emptyList()`; malformed block tables yield `null` or skip the bad stream. No
    real-world SCHl corpus exists, so parity is best-effort on synthetic
    fixtures.
  - `VagReader.kt` — Sony VAG (`.vag`): dispatches on the header id across the
    generations vgmstream accepts — standard and PS2 `VAGp` (second-header
    stereo interleaves, EoR/Killzone, HEVAG Vita/PS4, Kingdom Hearts II,
    NFS `VAGx`, Garfield, Eko, THQ and `KAudioDLL` layouts), `VAG1`, `VAG2`,
    `VAGi`, little-endian `pGAV`, data-first `VAGp` "footer", `AAAp` and
    Evolution Games. Sample count is `channel_size / 0x10 * 28`; loop points come
    from PS-ADPCM `0x06`/`0x03` frame flags (or a variant-specific header field);
    length is the two-loop + ten-second-fade play count, else the sample count.
    The title is the header's 0x20 name when present, else `TAG_UNKNOWN`.
    Bad/truncated headers yield `null`. Loop scanning (`findPsxLoop`) and
    sample-count math (`psBytesToSamples`) are shared with `VabReader`; both use
    `streamedLengthMs` / `streamedRawTrack`.
  - `VabReader.kt` — Sony VAB (`.vab`) sound banks: walks the 128-slot program
    table and its tone entries, emitting **one track per tone** (1-based
    `subsongTrackNumber`) so a bank rip appears as individual songs. Each tone's
    wave is resolved through the 256-entry size table; its rate comes from the
    centre note/fine through the SPU pitch table (`spu1_note_to_pitch` →
    `44100 * pitch / 4096`), and its length uses the same two-loop +
    ten-second-fade policy. Titles use the program's 8-char name. A tone whose
    entry or wave points outside the file is logged and skipped without failing
    the rest of the bank; invalid/empty banks yield `null`. Sidecar
    `.vab_config` tuning files are not available to a bytes-only reader, so tones
    use the default note 60 / fine 0 (vgmstream's own fallback).
  - `HcaReader.kt` — CRI HCA (`.hca`), the ADX2/Atom compressed stream: reads
    only the header (`HCA\0` + version/header size, required `fmt` and
    `comp`/`dec` chunks, optional `loop`/`ciph`), validates it the way
    `clhca.c`'s `clHCA_DecodeHeader` does (version, sizes, channels, sample
    rate, block count/size, resolution/band limits, loop bounds, trailing
    CRC-16) and emits one `RawTrack` whose length is the vgmstream play count
    (2 loops + 10 s fade when looped, else `blocks * 1024 - encoderDelay -
    encoderPadding`, then truncating `samples * 1000 / rate`). HCA has no stream
    name, so `title` is `TAG_UNKNOWN` (filename fallback applies);
    `trackNumber` is `subsongTrackNumber(0)`, `fadeLengthMs = 0`, `platform`
    is `OTHER` (the shared default; never parsed from the identifier). Encrypted
    payloads (`ciph` type 56) parse identically because no key is ever read,
    and the signature check masks the high bit per header byte like upstream's
    `0x7F7F7F7F` comparison. The reusable `readHcaHeader(bytes, offset)` (and
    `hcaCrc16`) is `internal` so the ACB/AWB reader (ticket 09) can read the
    HCA cues those containers embed; it returns `null` rather than throwing. Bad,
    truncated or checksum-failing headers yield `null`.
  - `AixReader.kt` — CRI AIX (`.aix`), a **segmented ADX container**, not an HCA
    cue sheet. Upstream's `meta/aix.c` builds one layered ADX stream per segment
    and joins them with `layout_segmented`, so a file yields exactly one
    `RawTrack` (`trackNumber = subsongTrackNumber(0)`; AIX carries no stream or
    cue names, so `title` is `TAG_UNKNOWN` and the filename fallback applies;
    `platform` is `OTHER`).
    Length is the **sum of the header's segment sample counts** — the embedded
    ADX headers are never consulted for it — with the loop derived from segment
    boundaries exactly as `allocate_segmented_vgmstream` does: segment 1 for
    1–3 segments, segment 2 and `count - 2` for 4–5, looping only for 1–5
    segments and never for upstream's truncated-three-segment case, then the
    shared 2-loops + 10 s fade policy. Validation mirrors upstream: `AIXF`
    magic, version/header-size dwords, 1–120 segments, table bounds, first
    segment at the data offset, equal rates (a later segment's zero rate
    inherits segment 0's), a layer count ≥ 1 with matching rates, and every
    played segment inside the file. Any bad table or offset is a `null` miss,
    never a throw — as upstream, one invalid segment invalidates the file,
    because the segments form one continuous stream, not separate subsongs.
  - `AwbReader.kt` — CRI AFS2/AWB wave banks (`.awb`, `.afs2`), the streaming
    bank of CRI Atom/ADX2 games. Reads the wave-id table and the absolute
    subfile-offset table with upstream's alignment padding, then sniffs each
    cue (`0x8000` → `readAdxHeader`, masked `HCA\0` → `readHcaHeader`) for its
    length under the shared loop policy. One `RawTrack` per readable cue,
    1-based `trackNumber` = wave index, `Unknown` title (a bank carries wave
    ids, not names) and `fadeLengthMs = 0`. A cue with any other codec (VAG,
    RIFF, DSP, Opus, …) is skipped while keeping its index, so the surviving
    cues keep their numbers. Bad magic → `null`; an unusable or cue-less AWB →
    `emptyList`.
  - `AcbReader.kt` — CRI ACB cue sheets (`.acb`), the `@UTF` table that names a
    game's bank. Reads the `Header` table's `AwbFile` column as an **embedded**
    AWB (via `readAwbBank`) and resolves cue names through `CueNameTable` →
    `CueTable` (`ReferenceType == 1` only) → `WaveformTable` → wave id. A name
    is used only when exactly one non-blank cue name maps to a wave and that
    name is unique in the file; duplicates/blanks become `TAG_UNKNOWN`.
    **Bytes-only boundary:** an ACB whose `AwbFile` is empty references an
    external `.awb` a `Reader` cannot open, so it yields `emptyList`
    (recognised and logged as skipped, never guessed). Likewise an
    unreadable/encrypted `@UTF` header, an ACB that wraps a CPK, or a cue chain
    through Synth/Sequence (types 2/3/8) leaves the wave unnamed or unparsed;
    HCA/ADX payload encryption does not affect metadata (headers are
    cleartext). The internal `CriUtf.kt` holds the minimal `@UTF` table reader
    (schema, rows, strings, `vlData`) the ACB reader is built on.
  - `BrstmReader.kt` — Nintendo Wii `RSTM` (`.brstm`, plus `.brstmspm` as a
    separate instance that halves a 44.1 kHz rate, as upstream's fake extension
    does). Reads the `HEAD` stream info in both layouts (1.0 chunk table and the
    compact 0.1 header), requires the declared file size to match (so truncation
    is a miss), and emits one track: the `HEAD` track table selects channel
    groups of a single stream, not subsongs.
  - `BfstmReader.kt` — Wii U / Switch `FSTM` (`.bfstm`), big- or little-endian
    per the BOM. Reads the `INFO` stream info and emits one track per file; a
    `REGN` section describes layout, and the vendored `bfstm.c` never sets
    `num_streams`, so the native probe exposed one subsong too.
  - `BcstmReader.kt` — Nintendo 3DS `CSTM` (`.bcstm`), little-endian throughout.
    Reads the `INFO` stream info; mirrors upstream's Camelot-IMA case (DSP codec
    with no `SEEK` section starts at the seek offset) and emits one track.
  - `DspReader.kt` — standalone GameCube/Wii `.dsp`: a 0x60-byte DSPADPCM
    header plus NGC DSP frames. Validates the header like upstream (which is
    what detects endianness), tries big-endian then little-endian, converts
    nibble loop offsets with `dspNibblesToSamples`, clamps the loop end to the
    sample count, and honors the loop flag. Multi-channel/interleaved DSP
    variants are out of scope.
- **Gzip (expect/actual)** — `Gzip.kt`: `internal expect fun gunzip(...)` in
  `commonMain`, with JVM/Android actual in `src/main/java` (`java.util.zip`), and
  a JS actual in `jsMain` (no-op — `.vgz` unsupported on the enforcement-only JS
  target). Used by `VgmReader` to inflate `.vgz`.
- **Format readers** — `PsfReader.kt` (PSF family + `_lib` chain tags →
  `PsfTagInfo`), `NsfReader.kt`, `NsfeReader.kt` (chunked NSFe), `GbsReader.kt`,
  `SpcReader.kt` (ID666 / xid6 → `SpcTags`),   `RsnReader.kt` (unpacks the RAR
  archive in Kotlin and delegates each member to `SpcReader`, folding the
  delegate's version into its own), `VgmReader.kt`
  (VGM/VGZ with GD3 tags; `version = 2` after the loop-length fix). Each parses
  headers/tags and emits `RawTrack`s.
- **Playlist reader** — `M3uReader.kt`: parses `.m3u` subtune playlists into
  `List<M3uEntry>` (note: this one returns `M3uEntry`, not `RawTrack` — its
  per-track overrides are merged onto a game's tracks elsewhere).
- **Detekt baseline** — `detekt-baseline.xml` suppresses pre-existing findings in
  the ported reader code.

## Streamed-audio readers

The streamed readers below parse headers only: a library scan never needs a
native library, and playback still goes through the vendored `libvgmstream`.
Every registration is a `val` plus a `when` arm in `Readers.forExtension` —
dispatch is extension-only and case-sensitive (the scanner lowercases first).
`EA_SCHL_EXTENSIONS` lives in `EaSchlReader.kt` because the list is long.

| Reader | Registered extensions | Family | Tier |
| --- | --- | --- | --- |
| `StrmReader` | `strm` | NDS STRM | 0 (corpus) |
| `XaReader` | `xa` | PSX/CD-i CD-XA (raw and RIFF) | 0 (corpus) |
| `AdxReader` | `adx` | CRI ADX | 0 (corpus) |
| `SebReader` | `seb`, `gms` | SEB / GMS | 0 (corpus) |
| `GenhReader` | `genh` | GENH generic wrapper | 0 (corpus) |
| `SadlReader` | `sad` | SADL | 0 (corpus) |
| `Fsb5Reader` | `fsb`, `fsb5` | FMOD Studio sample bank | 1 (synthetic) |
| `BrstmReader` | `brstm`, `brstmspm` | Nintendo Wii RSTM | 1 (synthetic) |
| `BfstmReader` | `bfstm` | Wii U / Switch FSTM | 1 (synthetic) |
| `BcstmReader` | `bcstm` | Nintendo 3DS CSTM | 1 (synthetic) |
| `DspReader` | `dsp` | standalone NGC/Wii DSP | 1 (synthetic) |
| `HcaReader` | `hca` | CRI HCA | 1 (synthetic) |
| `AixReader` | `aix` | CRI AIX segmented ADX | 1 (synthetic) |
| `AwbReader` | `awb`, `afs2` | CRI AFS2/AWB wave bank | 1 (synthetic) |
| `AcbReader` | `acb` | CRI ACB cue sheet (embedded AWB) | 1 (synthetic) |
| `VagReader` | `vag` | Sony VAG / PS-ADPCM | 1 (synthetic) |
| `VabReader` | `vab` | Sony VAB sound bank | 1 (synthetic) |
| `EaSchlReader` | `asf`, `lasf`, `chk`, `eam`, `exa`, `sng`, `aud`, `sx`, `stm`, `hab`, `xsf` | EA SCHl (standard header) | 1 (synthetic) |

Tier 0 was checked against the 618-row streamed corpus (except the `.seb`
extension itself, which has no local file); Tier 1 has no local corpus and is
proven with synthetic fixtures. See the architecture doc for the per-format
verification column, exclusions and the one-track-per-file AIX correction.

### Versioning

`Reader.version` defaults to `Reader.INITIAL_VERSION = 1`. Bump it whenever a
change alters the `RawTrack`s a file yields (length, count, title, platform,
chain files); a refactor that produces identical output needs no bump. The
scanner persists `readerVersion = readers.readerVersionFor(extension)` on every
track and re-reads a folder when any stored `extension → version` pair differs
from the current code. `readerVersionFor` returns
`Readers.NO_READER_VERSION = 0` for an unregistered extension, so registering a
format moves its stored value `0 → 1` and re-reads existing folders
automatically — no database migration. Versions are keyed by extension, so two
extensions sharing one reader share one version. (The scanner itself is at
`version = 2`, which forced a one-time re-read of libraries written by the
native-prober era.)

### Length, fade and titles

`streamedLengthMs(...)` is the single length implementation and reproduces the
old probe's fixed config: a looping stream plays its intro, **two** loop passes
and a **10-second fade** (`loop_start + (loop_end − loop_start) × 2 + 10 × rate`
samples), while a non-looping stream plays its sample count. Milliseconds are
`playSamples × 1000 / rate` in `Long`, truncated, never rounded.

`fadeLengthMs` is always `0`: the 10 s fade is already in the length, and the
scanner then adds its own `2000 ms` zero-fade padding, so the stored value is
`readerLengthMs + 2000` exactly as before the cutover. Do not emit
`FADE_LENGTH_MS` from a streamed reader.

`streamedRawTrack(...)` builds the shared shape. `title` is the container/stream
name when the format carries one, else `TAG_UNKNOWN` (`"Unknown"`) so the
scanner's single-track filename fallback applies; `artist`/`game` are
`TAG_UNKNOWN`, and `platform` is passed explicitly — `Platform.OTHER` by default,
`PSX` for the PSX formats — never derived from `identifier` (a content-source id
need not carry an extension). Track numbers are
**1-based** via `subsongTrackNumber(index)` — the deliberate exception to the
chiptune readers' 0-based numbering, because playback forwards the number as
vgmstream's subsong and `(path, trackNumber)` is the database identity.

### Parity harness

The dev-only `perfbench reader-parity` command runs the registered readers over
a pre-cutover database export and compares `readerLengthMs + padding` (default
`2000 ms`) with the stored length. It needs no native library and is not a CI
gate. Capture the reference TSV and run it as documented in
[`testing/perfbench/README.md`](../../../../testing/perfbench/README.md); the
cutover reference is 618/618 exact. Tier-1 formats have no corpus rows and are
covered by synthetic tests instead.

## Why depend on this module

Depend on `:cbox:common:readers:api` to identify and read a chiptune or streamed-audio file's
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
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (readers + `ByteReader`/`ByteMagic`/`ReaderUtils`/
  `StreamedAudio`/`StreamedCodecs`/`Gzip` expect); `src/main/java` (`jvmSharedMain`)
  and `src/jsMain` hold the `gunzip` actuals
- **SAGE/module dependencies:** `api` `:cbox:common:repository:api` (the `RawTrack`
  type); `implementation` `:cbox:common:models:api`, `:cbox:common:utils:api`,
  `sage.common.logging` (`Hatchet`)
