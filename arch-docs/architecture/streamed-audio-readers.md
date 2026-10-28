# Streamed audio readers

How Chipbox reads the metadata of streamed (prerecorded) game-audio formats
without a native library, which formats are in scope, and how to add another.

**Scanning is pure Kotlin.** A library scan parses every in-scope file's header
in `:cbox:common:readers:api`; it never loads `libvgmstream` and never decodes
audio. Playback is unchanged: the GUI/Android/JS apps still hand the file to the
vendored vgmstream emulator, which opens and decodes it. The readers only
reproduce what the old scan-time native probe reported — sample count, rate,
loop points, subsong count and stream name — so a reader and the decoder must
agree about *what* the file is, but not about how to play it.

This replaced a native probe that loaded `libvgmstream.so` during scanning. That
probe was metadata-only but made a whole scan depend on a loadable `.so`: a
failed load silently dropped every streamed track (the July incident behind this
effort). The Kotlin port reproduced the probe's output exactly on the Tier-0
corpus — **618/618 rows** across six formats — before the cutover.

Related docs:

- [`cbox/common/readers/api/README.md`](../../cbox/common/readers/api/README.md) —
  the module's files, shared helpers and test conventions.
- [`cbox/common/scanner/real/README.md`](../../cbox/common/scanner/real/README.md) —
  how a scan dispatches files and reports skips.
- [`testing/perfbench/README.md`](../../testing/perfbench/README.md) — the
  dev-only parity harness.

## The Reader contract

Every format is a direct subclass of `Reader` in the readers module:

```kotlin
sealed class Reader {
    open val version: Int = INITIAL_VERSION               // 1
    abstract fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>?
}
```

- **Input is bytes.** The scanner opens the file (`LibrarySource.openBytes`) and
  passes the whole `ByteArray` plus an opaque `identifier` (a path on JVM, a SAF
  URI string on Android). A reader must not open paths, use `java.io`, or stream;
  it only echoes `identifier` into `RawTrack.path` and uses it in log lines.
- **Three return outcomes:**
  - `null` — parse miss, wrong magic, truncation or malformed header. The
    scanner logs a failure and counts `tracksFailed`.
  - `emptyList()` — the format was recognised but yields no tracks: a codec the
    vendored build cannot decode, an empty bank, or an external-file container.
    This is **not** a failure; the scanner logs it at debug.
  - a non-empty list — normal success.
- **Never throw.** Each reader wraps its parse and returns `null`; a thrown
  exception would abort the file and emit a scanner failure. `ByteReader`'s
  fixed-width reads throw on underflow, so readers catch
  `IndexOutOfBoundsException`/`IllegalArgumentException` at the boundary.
- **Stateless.** One instance of each reader is created in `Readers` and shared
  across concurrent folder scans. No mutable fields, caches or reused buffers.
- **`Reader` is sealed and `ByteReader` is `internal`**, so a new reader belongs
  in `:cbox:common:readers:api` (Kotlin requires direct subclasses in the same
  package and module) unless that plumbing is deliberately unsealed/made public.

`RawTrack`/`RawGame` are in `:cbox:common:repository:api`. Readers fill metadata
only; the scanner overwrites `source` and `extension`, stamps
`scannerVersion`/`readerVersion`, and applies its length/title fallbacks.

## Registration

All registrations live in `Readers.kt`:

- `Readers` holds one `val` per reader (`strm`, `xa`, `adx`, `fsb5`, `hca`, …).
- `Readers.forExtension(extension): Reader?` is the **only** dispatch point. It
  is a flat, case-sensitive `when` over lowercase extension constants
  (`.adx`, not `.ADX`); the scanner lowercases the file extension before calling.
  There is no magic sniffing across formats.
- `Readers.readerVersionFor(extension): Int` returns the reader's `version`, or
  `NO_READER_VERSION` (`0`) when no reader is registered.
- `isPsfFamily(extension)` is a separate scanner fast-path for the PSF family.
  Streamed formats use the normal reader path.
- EA SCHl's eleven extensions live in `EA_SCHL_EXTENSIONS` in
  `EaSchlReader.kt`, because the list is long; `forExtension` tests membership.

Adding a reader needs **no scanner or DI change**. The scanner calls
`forExtension`, and existing construction sites create `Readers(hatchet)`.

## Versioning

`Reader.version` participates in the scanner's folder-skip check, so it is the
mechanism that re-reads files after a parser fix.

1. At persist time the scanner stamps each track with
   `readerVersion = readers.readerVersionFor(extension)`.
2. A folder is skipped only when its stored scanner version matches **and** every
   stored `extension → reader_version` pair still equals the current code
   (`FolderVersionCheck.isUpToDate`).
3. The comparison is equality, not "at least": any changed reader version forces
   a re-read of folders containing that extension.

Rules for contributors:

- **Bump `version` whenever the `RawTrack`s a file yields change** — length,
  track count, title, artist, platform, chain files. A parser refactor that
  produces identical tracks needs no bump.
- `Reader.INITIAL_VERSION` is `1`. All streamed readers start at `1`; the
  chiptune `VgmReader` is at `2` after its loop-length fix and `RsnReader`
  composes its own version with `SpcReader`'s.
- Versions are keyed by **extension**, so two extensions sharing a reader share
  one version.
- `NO_READER_VERSION = 0` is the stored value for formats with no reader. A
  newly registered format therefore moves `0 → 1` and automatically re-reads
  folders that previously held those files — no database migration.

The cutover also set `RealScanner.version = 2`. Streamed formats moved from the
old no-reader sentinel to real reader versions, but folders containing only
*still-unreadable* streamed files would have stayed at `0 == 0` and remained
skippable, so their new skipped-file report would never appear. The scanner bump
forces one full re-read of every existing library.

## Formats in scope

"Tier 0" formats were verified against the real 618-row corpus; "Tier 1"
formats have synthetic fixtures only because the local corpus has none of them.
The parity harness covers Tier 0; Tier 1 literals are derived from the vendored
parser's arithmetic and, where noted, cross-checked against the native probe on
synthetic files.

| Reader | Registered extensions | Format family | Tier | Verification |
| --- | --- | --- | --- | --- |
| `StrmReader` | `strm` | NDS STRM | 0 | corpus 62/62 |
| `XaReader` | `xa` | PSX/CD-i CD-XA (raw and RIFF) | 0 | corpus 200/200 (all single-subsong); multi-subsong and mono/8-bit synthetic |
| `AdxReader` | `adx` | CRI ADX | 0 | corpus 4/4; looping/encrypted/AINF synthetic |
| `SebReader` | `seb`, `gms` | SEB / GMS | 0 | corpus 239/239 (`.gms`; no `.seb` locally) |
| `GenhReader` | `genh` | GENH generic wrapper | 0 | corpus 112/112 |
| `SadlReader` | `sad` | SADL | 0 | corpus 1/1 |
| `Fsb5Reader` | `fsb`, `fsb5` | FMOD Studio sample bank (FSB5) | 1 | synthetic; multi-subsong/loop fixtures also cross-checked with the native probe |
| `BrstmReader` | `brstm`, `brstmspm` | Nintendo Wii RSTM | 1 | synthetic |
| `BfstmReader` | `bfstm` | Wii U / Switch FSTM | 1 | synthetic (one track per file; REGN regions ignored) |
| `BcstmReader` | `bcstm` | Nintendo 3DS CSTM | 1 | synthetic |
| `DspReader` | `dsp` | standalone NGC/Wii DSPADPCM | 1 | synthetic |
| `HcaReader` | `hca` | CRI HCA | 1 | synthetic (real headers, including CRC) |
| `AixReader` | `aix` | CRI AIX segmented ADX | 1 | synthetic |
| `AwbReader` | `awb`, `afs2` | CRI AFS2/AWB wave bank | 1 | synthetic |
| `AcbReader` | `acb` | CRI ACB cue sheet (embedded AWB) | 1 | synthetic |
| `VagReader` | `vag` | Sony VAG / PS-ADPCM | 1 | synthetic |
| `VabReader` | `vab` | Sony VAB sound bank | 1 | synthetic |
| `EaSchlReader` | `asf`, `lasf`, `chk`, `eam`, `exa`, `sng`, `aud`, `sx`, `stm`, `hab`, `xsf` | EA SCHl (standard header) | 1 | synthetic; two fixtures cross-checked with the native probe |

Formats that pack several songs into one file emit one `RawTrack` per stream,
each with its own track number:

- XA: one per interleaved file+channel subsong.
- FSB5: one per sample-bank subsong.
- AWB/ACB: one per wave/cue, not per cue-name alias.
- VAB: one per tone.
- EA SCHl: one per audio-header block (one for a standard file, one per `SHxx`
  language header in a localized file).

Containers that hold **one** stream stay one track even when they contain a
track table: BRSTM's track table selects channel groups, BFSTM's `REGN` regions
describe layout (the vendored `bfstm.c` leaves `num_streams` unset, so the probe
emitted one subsong) and AIX's segments are one continuous stream (see
[AIX correction](#aix-is-one-track-not-one-per-cue)).

## Length, fade, titles and track numbers

The scanner stores what the old native probe reported, so the readers reproduce
the probe's fixed playback configuration rather than a container's raw duration.

### Length

`streamedLengthMs(numSamples, loopFlag, loopStartSample, loopEndSample, sampleRate)`
is the single implementation:

- **Looping:** `loop_start + (loop_end − loop_start) × 2 + 10 × sample_rate`
  samples — the intro, **two loop passes** and a **10-second fade**
  (`loop_count = 2.0`, `fade_time = 10.0` in the old probe and in the playback
  wrapper).
- **Not looping:** the file's sample count (after any format-specific
  encoder-delay/padding adjustment).
- **Milliseconds:** `playSamples × 1000 / sampleRate` using `Long` integer
  (truncating) division. Do not round, and keep the operation order.

`fadeLengthMs` is always **`0`**. The fade is already inside the length, and the
scanner's zero-fade path then adds `ZERO_FADE_PADDING_MS = 2000 ms` to every
stored track. So the database value is:

```
stored_length_ms = floor(play_samples * 1000 / sample_rate) + 2000
stored_fade_ms   = 0
```

A reader must **not** emit `FADE_LENGTH_MS` here. Doing so would bypass the 2 s
padding and make every streamed track 2 s shorter than the existing database.
The readers deliberately leave that padding to the scanner, as the spec
requires.

### Titles and the sentinel

- A format that carries a stream name uses it as `title` (XA's file+channel
  marker for multi-subsong files, FSB5's name table, VAG's header name, VAB's
  program name, ACB's unique cue name).
- Otherwise `title` is `TAG_UNKNOWN` (`"Unknown"`). The scanner's filename
  fallback only fires for a **single-track** file whose title is exactly
  `TAG_UNKNOWN`; it derives the title (and, only if `trackNumber < 0`, the
  number) from the filename. That is why single-stream XA/ADX/etc. keep their
  filename-derived titles.
- `artist` and `game` are always `TAG_UNKNOWN`; the scanner fills `game` from
  the folder name. A multi-track file with unnamed streams gets the scanner's
  folder-wide `"Track N"` counter.
- `platform` comes from `platformForStreamedExtension(extension)`:
  `xa`/`vag`/`vb`/`vh`/`vab`/`seq`/`vpk` → `PSX`, everything else → `OTHER`. A
  reader that knows its console may set it directly; this is the fallback. It is
  metadata only and never selects a decoder.

### Track numbers are 1-based

`subsongTrackNumber(index) = index + 1`. This is the deliberate exception to the
chiptune readers' 0-based subtune numbers:

- playback forwards `RawTrack.trackNumber` to vgmstream's `setTrackNumber`,
  whose native side treats it as a 1-based subsong and clamps
  `subsong > 0 ? subsong : 1`;
- `(path, trackNumber)` is the database identity, and every pre-cutover
  streamed row was already 1-based.

A skipped cue keeps its index/number, leaving a gap rather than renumbering the
following tracks.

## Codec allow-list

Chipbox vendors vgmstream core-only. The build decodes a codec only if it is
compiled in; a format whose codec is not decodable must yield **no tracks**
(never a length for audio the player cannot produce).

### What the build decodes

**Built in (no external library):**

- PCM 8/16/24/32-bit and float
- PSX/XA ADPCM, VAG, HEVAG
- IMA/DVI/Xbox IMA, MS ADPCM
- GCADPCM / NGC DSP, NDS Procyon, DTK
- CRI HCA (bundled `clhca`), ADX
- EA-XA, EA-MT (MicroTalk), VADPCM, FADPCM

**Compiled out:** Vorbis, MPEG (mpg123), G.722.1, G.719, MP4v2, FDK-AAC,
everything FFmpeg-backed (XMA, XWMA, AC3, AAC, ATRAC3/ATRAC3plus, Opus), ATRAC9,
CELT and Speex.

### The source of truth

The allow-list mirrors **this build**, not upstream vgmstream's documentation:

- `cbox/native/vgmstream/CMakeLists.txt` compiles the core with exactly one
  definition, `VGM_LOG_OUTPUT`; it adds no codec flags.
- `cbox/native/vgmstream/Core/vgmstream/src/vgmstream.h:6-18` is the codec
  switchboard. Every `VGM_USE_*` define there is commented out.
- A meta or decoder case behind an undefined `VGM_USE_*` compiles to a stub. The
  metadata parser may still recognise the container, but playback cannot decode
  it, so a reader that gates a codec id returns `emptyList()`.

The Kotlin mirror is `StreamedCodecs.kt` — one `ALLOWED_CODECS` map keyed by
`StreamedCodecs.Format`, read through `isCodecAllowed(format, codec)`:

- `Format.FSB5` — PCM8, PCM16, PCM float, GCADPCM, IMA ADPCM, VAG, HEVAG,
  FADPCM.
- `Format.EA_SCHL` — PCM8/16 LE/BE (split and interleaved), EA-XA (stereo and
  split mono), EA-ADPCM/MicroTalk MT10/MT5.
- `Format.GENH` — every `genh.c` case not behind a `VGM_USE_*` guard: `0..7`,
  `9..17`, `24..27`.

**FSB5 PCM24 (`0x03`) and PCM32 (`0x04`) are rejected** even though the coders
compile in: the vendored `meta/fsb5.c` explicitly logs "found (report)" and
`goto fail`s for both, so a bank using either would be listed but never play.
Upstream's docs mention them; this build does not decode them.

**GENH `8` (MPEG), `18..23` (ATRAC3/ATRAC3plus, XMA1/2, FFmpeg, AC3) and `28`
(AAC) are rejected.** `GenhReader` parses the header, then returns
`emptyList()` for a codec outside the list — never a length the player cannot
decode. The 112-file corpus uses PSX and PCM16, so parity is unaffected.

### Re-checking after a vendored-source update

```sh
# 1. Any uncommented VGM_USE_* means an external codec was enabled.
grep -n "#define VGM_USE" cbox/native/vgmstream/Core/vgmstream/src/vgmstream.h

# 2. The CMake build must still add only VGM_LOG_OUTPUT.
grep -n "compile_definitions" cbox/native/vgmstream/CMakeLists.txt

# 3. FSB5: ids inside #ifdef VGM_USE_* are not decodable.
grep -n "VGM_USE\|FMOD_" cbox/native/vgmstream/Core/vgmstream/src/meta/fsb5.c

# 4. EA SCHl: compare the codec table against Format.EA_SCHL's list.
grep -n "case 0x" cbox/native/vgmstream/Core/vgmstream/src/meta/ea_schl.c

# 5. GENH: the switch's guarded cases are not decodable here.
grep -n "VGM_USE\|case " cbox/native/vgmstream/Core/vgmstream/src/meta/genh.c
```

Then update `StreamedCodecs.kt` and its test. If the vendored meta set changes,
regenerate `StreamedExtensions.ALL` as described in its KDoc.

## Exclusions

### Formats that need external codecs

These are explicitly out of scope, not "not yet":

- Vorbis, MPEG, CELT, ATRAC9, Speex, G.719, Opus, XWMA, and every FFmpeg-backed
  codec (XMA, AC3, AAC, ATRAC3/ATRAC3plus, …).

The vendored build cannot decode them, so a reader would only promise audio the
app cannot play. This is why FSB5 banks using Vorbis/MPEG/CELT/ATRAC9/Opus/XWMA
are rejected outright, BFSTM's Opus variants are not decoded, and GENH's
MPEG/FFmpeg/AAC ids are rejected by its codec gate (see
[Codec allow-list](#codec-allow-list)). Celeste's banks are a known example.

### Tier 2: the rest of vgmstream

The remaining ~350 format families are out of scope. The reader set grows **on
demand**, not for upstream parity. A Tier-2 extension is a known streamed
format, so the scanner reports it as skipped (see below) rather than silently
dropping it.

### Bytes-only and companion-file limits

A `Reader` receives one file's bytes, so containers that need a sibling cannot
be fully supported:

- ACB cue sheets whose `AwbFile` cell is empty reference an external `.awb`; the
  bytes-only reader returns `emptyList()` (logged as skipped) instead of
  guessing. ACBs that wrap a CPK are likewise unsupported.
- EA SCHl companion banks (BNK, ABK+AST, HDR+DAT, MPF/MUS) and the fixed-header
  meta are separate formats needing other files; only the standard header is
  ported.
- VAB `.vab_config` tuning sidecars are unreadable from file bytes, so tones use
  the default note 60 / fine 0 (vgmstream's own no-config fallback).
- DSP multi-channel/interleaved variants and ambiguous aliases (`.adp`,
  `.mdsp`, …) are deliberately not registered.
- AIX's embedded `AIXP` ADX payload is not descended into: length and loop come
  entirely from the segment table, so payload corruption yields a track where
  the native probe would fail. This is documented in the reader.

<a id="aix-is-one-track-not-one-per-cue"></a>
### AIX is one track, not one per cue

The original ticket described AIX as an HCA cue container with one track per
cue. That is not what upstream does: `meta/aix.c` builds one layered ADX stream
per segment and joins the segments with `layout_segmented`, producing exactly
**one** vgmstream per file. AIX has no name table and no per-cue skip path. The
reader follows upstream: one `RawTrack`, length from the segment sample counts,
loop points derived from the segment boundaries, title `TAG_UNKNOWN`. The
segments exist to make an intro/loop/end stream, not separate songs.

## Unknown streamed extensions

With no native probes, the scanner still needs to distinguish "a streamed format
we cannot read yet" from "a `.txt`/`.gif` in the folder":

- `StreamedExtensions.ALL` (in the readers module) mirrors the vendored build's
  extension list (`libvgmstream_get_extensions()` minus its generic "common"
  media formats). It is 702 unique extensions; `slb` appears twice upstream.
  The set deliberately overlaps registered readers (`adx`, `brstm`, `spc`,
  `vgm`, …); `forExtension` is checked first, so the overlap is inert.
- Scanner dispatch, in order:
  1. PSF family → PSF reader.
  2. `readers.forExtension(ext)` → that reader.
  3. no reader, but `isKnownStreamedExtension(ext)` → **warn** ("No Kotlin
     reader for streamed extension …"), increment `filesSkipped`, continue.
  4. no reader and unknown → debug-level skip, **not** counted (`.txt`, `.gif`,
     `.flac`, …).
- The skip count rides in `Progress.filesSkipped`, `ScannerState.Scanning` and
  `ScannerState.Complete` (trailing defaulted field), and is printed by the CLI
  and at scan end. A folder that yields only skipped files still reports them.
- `RealScanner.version = 2` guarantees every existing library is re-read once,
  so the report is produced for files that predate the cutover.

## Dev-only parity harness

The corpus check is not CI and needs no native library. It lives in
`:testing:perfbench` as `reader-parity`.

Capture a reference TSV from a **pre-cutover** library database — columns
`id, path, trackNumber, trackLengthMs, extension, title`, tab-separated, no
header:

```sh
DB=~/.local/share/chipbox/library.sqlite
for e in strm xa adx genh gms sad; do
  sqlite3 -readonly -separator $'\t' "$DB" \
    "select id,path,trackNumber,trackLengthMs,extension,title
       from track where extension='$e' order by id;"
done > /tmp/all618.tsv
```

Run the registered readers against it:

```sh
./gradlew :testing:perfbench:run --args="reader-parity --reference /tmp/all618.tsv"
# rows=618 exact=618 mismatch=0 unparsed=0
#   adx: exact=4  genh: 112  gms: 239  sad: 1  strm: 62  xa: 200
```

How it compares:

- It runs the reader for each row's extension, selects the track whose
  `trackNumber` matches the row, and compares
  `readerLengthMs + padding` with the stored length. Padding defaults to
  `2000 ms` (the scanner's zero-fade padding).
- `--no-padding` compares raw probe lengths when the reference was captured
  before padding.
- `--reader <ext>` narrows to one format; `--dir <corpus>` runs over a folder
  without a reference (reporting `parsed`/`unparsed` only).
- It prints exact/mismatch/unparsed totals overall and per extension, plus the
  first 20 offending paths.

Tier-1 formats have no corpus rows, so they are covered by synthetic unit tests
instead. Any future real-file capture should be added as another reference run,
not made a CI gate.

## Adding a format: checklist

1. **Confirm the vendored parser.** Read the meta under
   `cbox/native/vgmstream/Core/vgmstream/src/meta/` (plus its decoders). Decide
   whether the format fits the bytes-only boundary — if it needs a sibling file,
   a key server or an external codec, stop and document the exclusion.
2. **Check the codec.** Map every codec id the format can carry to
   `StreamedCodecs` / the built-in list. If the reader parses a codec id, gate
   it and return `emptyList()` for anything the build cannot decode. Extend
   `StreamedCodecs` (and its test) if a new in-scope codec needs an explicit
   list.
3. **Write the reader** in
   `cbox/common/readers/api/src/commonMain/kotlin/net/sigmabeta/chipbox/readers/`:
   a `Reader` subclass that takes `Hatchet`, validates magic/size first,
   returns `null` for a parse miss, catches underflow, and never throws. Use
   `ByteReader` (add shared endian/64-bit helpers there, not per format).
4. **Emit the shared shape.** Use `streamedLengthMs` for the length (loop
   policy, truncating ms) and `streamedRawTrack` for the track (1-based
   `subsongTrackNumber`, `fadeLengthMs = 0`, `Unknown` tags, extension-derived
   platform). Set a real stream/program name when the format carries one.
5. **Register it** in `Readers.kt`: one `val`, one `EXT_*` constant, one
   `forExtension` arm. Use `EA_SCHL_EXTENSIONS`-style membership only for long
   lists. Do not take an extension another reader owns; the first matching arm
   wins silently.
6. **Test hermetically.** Add `src/commonTest/.../<Format>ReaderTest.kt` with a
   synthetic byte builder and literal expectations annotated with the upstream
   arithmetic (or the probe/corpus value). Cover: registration/version, one
   normal file, looping and non-looping lengths, multi-stream numbering,
   unsupported codec → `emptyList`, and bad/truncated magic → `null`.
7. **Verify.** `./gradlew :cbox:common:readers:api:jvmTest ktlintCheck detekt`,
   and confirm `readers.readerVersionFor("<ext>")` reports the reader's current
   version (1 for a new format; bumped whenever a fix changes its tracks). If a real corpus
   exists, add it to a `reader-parity` run; if not, say plainly that coverage is
   synthetic.
8. **Document.** Update the readers module README's streamed-reader table and
   this document's format table/exclusions, and make sure the version is bumped
   when a later fix changes output.
9. **Unknown extensions.** If the vendored build knows the extension, ensure
   `StreamedExtensions.ALL` still matches it; regenerate the set after a
   vendored-source update (its KDoc has the command).

## Known deviations worth remembering

These are decisions, not bugs to "fix" silently:

- **AIX is one track per file** (segmented ADX), not one per cue — see above.
- **ACB is bytes-only**: an external AWB reference yields no tracks; HCA/ADX
  payload encryption does not affect metadata.
- **FSB5 PCM24/PCM32 are rejected** because the vendored parser fails them.
- **VAB titles come from the program name** (ticket 10), not upstream's
  `"%02d/%02d"` program/tone stream label. No VAB corpus exists locally, so this
  divergence is untested against the native probe.
- **Tier 1 is synthetic-only** locally; do not claim corpus parity for it.
- **Track numbers are 1-based** for streamed readers, 0-based for chiptune
  readers; this is load-bearing for playback and database identity.
- **Lengths are probe lengths, not container durations**, and `fadeLengthMs`
  is always `0` so the scanner's 2 s padding keeps the database unchanged.
