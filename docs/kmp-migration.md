# Kotlin Multiplatform migration — plan & status

Status: **Milestone 4 implemented.** JVM is the second target. All seven
native emulators decode end-to-end on it (playback-verified on host
x86-64), the headless app also runs without Gradle via a generated
launcher, the mass `sage.android`/`sage.jvm` → `sage.kmp` conversion
collapsed 61 modules onto single KMP modules serving both variants
(`cbox/jvm/` is empty, deleted), the Room storage stack is now KMP
(via Room 2.7+'s multiplatform support + the bundled SQLite driver),
and the JVM target uses the *same* Room database the Android app does
via a `scan`/`play` CLI — not the one-track `SingleTrackRepository`
shim. The remaining `sage.android`-classified modules split between
**genuinely Android-only** (Hilt `:di` glue, `R.*` resource modules,
Compose feature modules — all legitimate end-state, not transitional)
and **one real KMP blocker**: the scanner/SAF chain.
Scope of this doc: how Chipbox moves from Android-only to Kotlin
Multiplatform, what's already done, and the remaining milestones.

## Goal

Run the Chipbox player core unchanged on both Android and a plain JVM,
so the decode → cache → playback pipeline can be driven headlessly
(tests, desktop tools, CI) and, eventually, as a desktop app.

## Starting point

The codebase already separated platforms **module-per-platform** via
SAGE convention plugins — ~43 pure-JVM modules (`sage.jvm`, no Android
imports), including the entire `cbox/common/player/*` pipeline, vs the
Android modules (`sage.android`). No `kotlin.multiplatform` plugin was
used anywhere. The migration adopts the *literal* KMP plugin so a single
artifact carries both an Android and a JVM variant (and, later,
`expect`/`actual` for genuine platform seams) instead of duplicating
module trees.

### Key constraint

With only `androidTarget()` + `jvm()` (both JVM-family), `commonMain`
still cannot use `java.*`. Most existing "pure-JVM" code uses
`ConcurrentHashMap`, `String.format`, `java.io.RandomAccessFile`,
`java.nio.ByteBuffer`, etc. So the architecture uses a **custom
intermediate source set**, `jvmSharedMain`/`jvmSharedTest`, that both
`androidMain` and `jvmMain` `dependsOn`, where `java.*` is available. It
is mapped to the legacy `src/main/java` / `src/test/java` directories,
so a module adopts KMP with **no file moves and no code changes**. Pure
code can be hoisted into `commonMain` incrementally afterward.

The `sage` build logic and version catalog live in the `sage` git
**submodule**, so KMP infra changes there are a separate commit/PR from
the chipbox changes that consume them.

## Milestone 1 — foundation + headless JVM proof (done)

- **Version catalog** (`sage/gradle/libs.versions.toml`): added
  `kotlin-multiplatform` and `sage-kmp` aliases.
- **`sage.kmp` convention plugin** (`SageKmpModulePlugin` in
  `sage/sage-build-logic`): applies `org.jetbrains.kotlin.multiplatform`
  + AGP 9's `com.android.kotlin.multiplatform.library` (note: the old
  `com.android.library` is rejected together with the KMP plugin as of
  AGP 9), wires `jvm()` + the Android library target at JVM 17 with the
  same detekt / warnings-as-errors / coroutines opt-in as the other
  conventions, and defines the `jvmSharedMain`/`jvmSharedTest`
  intermediate source set described above. Per-module `namespace` is set
  in the module build file via `kotlin { androidLibrary { namespace } }`.
  Shared Android-library defaults were extracted in `KotlinAndroid.kt`.
- **First module converted**: `cbox/common/player/common/api` now uses
  `sage.kmp`. It builds both `jvm` and `android` variants and is consumed
  unchanged by the JVM-only `speaker:api` and by the Android app.
- **Headless JVM target**: new `:apps:jvm` (`sage.jvm` +
  `application`). `Main.kt` wires `MemoryRepository` → `FakeEmulator` /
  `FakeGenerator` → `RealBufferManager` → `FileSpeaker`, mirroring the
  Director's start ordering (the speaker's consume loop must start only
  after the generator's first `Emitting` event — starting it earlier
  parks the consumer on a pre-`setSampleRate` channel the buffer manager
  swaps out).

### Verification

- `:cbox:common:player:common:api:build` — both KMP variants build.
- `:apps:android:assembleDebug` — Android build still green against the KMP module.
- `:apps:jvm:run` — exits 0; produces a valid WAV (RIFF/WAVE,
  stereo, 16-bit, 44100 Hz, ~3 s, audible).
- detekt clean on new/changed modules.

## Milestone 2 — first real native emulator on JVM (done)

GME (`.spc/.nsf/.nsfe/.gbs`) was the first real native emulator to decode
end-to-end on the JVM target, lifting Milestone 1's `FakeEmulator`-only
constraint. Milestone 2b below extends this to all seven emulators; this
section documents the pattern GME established.

- **Native code is platform-neutral now.** All emulator C/C++ trees plus
  the shared `native-common` moved out of `cbox/android` into top-level
  `cbox/native/` (now built by two apps). The Android `real` modules point
  `externalNativeBuild` at `rootProject.file("cbox/native/<emu>/...")`.
- **Host-built native lib.** `libgme.so` is cross-built for the host from
  the same `cbox/native/gme` CMake tree the NDK uses (out-of-tree, JNI
  includes injected via `CMAKE_*_FLAGS`). Only portability change: a
  `#include <cstdint>` in `Gme.h` (NDK headers leaked it; host GCC is
  stricter — safe for the Android build too). See `apps/jvm/README.md`.
- **JVM emulator module.** A new `:cbox:jvm:player:emulators:gme:real`
  (`sage.jvm`) carried a byte-identical `GmeEmulator` twin — the JNI
  symbols bind to that exact FQCN, so the Android and JVM wrappers must
  match. (This intermediate twin was collapsed onto a single `sage.kmp`
  `:real` module in Milestone 3; the twin no longer exists.) The Android
  Kotlin wrapper itself was unchanged; only its build script's CMake path
  and `Gme.h` were touched (the relocation + `<cstdint>` above).
- **Context-free real generator.** A new
  `:cbox:jvm:player:generator:real` (`sage.jvm`): the Android
  `RealGenerator`'s only Android tie was `Context.cacheDir`; the actual
  work already lived in the pure-JVM `:cbox:common:player:cache:real`, so
  the staging / PCM-cache dirs are now plain `File` params. (Also
  collapsed onto `sage.kmp` in Milestone 3.)
- **File content source + repository.** `apps/jvm` gets a
  `FileContentSource` (path-is-the-identifier) and a one-track
  `SingleTrackRepository` (avoids `MemoryRepository`, which drops
  `RawTrack.source`/`extension` — the fields the real path needs).
- Verified: a 10 s (+2 s fade) Chrono Trigger SPC → 32 kHz stereo 16-bit
  WAV, mean ≈ −17 dB (audible, not silence).

### Milestone 2b — all emulators wired

All seven native emulators got a `:cbox:jvm:player:emulators:<emu>:real`
twin module and were wired into `apps/jvm` (`ALL_EMULATORS`; the generator
picks by extension). `Main.kt` stages `*lib` siblings so mini-formats
resolve their `_lib` chain. (The twin modules were collapsed onto single
`sage.kmp` `:real` modules in Milestone 3; the `apps/jvm` wiring still
references the same FQCNs, now resolved by the KMP modules' jvm variant.)
Host portability needed only build-flag shims (no Android-build impact):
`-D__fastcall=`/`__cdecl=`/`__stdcall=` for MSVC/x86 keywords, and an
`android/log.h` + `liblog.a` shim for psf's debug probes.

All seven are **playback-verified** on host x86-64 (real, audible WAV):
GME (SPC), PSF (.psf + .minipsf), VGM (.vgz), USF (.miniusf), 2SF
(.mini2sf), GBA/mgba (.minigsf), SSF + DSF (.ssf/.dsf w/ chain) — see the
per-emulator table in `apps/jvm/README.md`. The heavy CPU cores
(USF/2SF/GBA/SSF/DSF) decode slower than real time on a cold cache: the
render-ahead reader times out and the generator emits a terminal
`GeneratorEvent.Error` ("cache writer fell behind reader"). The headless
WAV harness still ends up with the full track (the speaker drains the
queue before the error propagates, and the second run is cache-served and
instant) — but in a live player that event would interrupt playback, so
the render-ahead window still needs tuning for these cores.

## Milestone 3 — mass KMP conversion (done, within constraints)

The duplicated `cbox/jvm/*` tree introduced for Milestone 2 served its
purpose and is now gone. Every module whose dependency closure is pure
moved onto `sage.kmp`; the rest stays single-target until item 4 lifts
the genuinely-Android dependencies (Room / SAF / resources / Hilt).

- **Twin collapse (8 modules).** The 7 emulator wrappers and the
  generator collapsed pairs of `cbox/android/.../<emu>:real` +
  `cbox/jvm/.../<emu>:real` into one `sage.kmp` `:real` module each.
  Because AGP 9.2's `KotlinMultiplatformAndroidLibraryExtension` exposes
  **no `externalNativeBuild` DSL**, the CMake/NDK trigger for each
  emulator was split out into a thin Android-only `:native` companion
  module (no Kotlin). The Android app pulls `<emu>:native` so the `.so`
  is packaged into the APK; the JVM target host-builds the same
  `cbox/native/<emu>` tree into `apps/jvm/libs`. The generator collapse
  needed no `expect`/`actual`: the Android `Context.cacheDir` was just
  caller-supplied configuration, and the Android Hilt module now derives
  the staging / PCM-cache `File`s at the call site.
- **`sage.jvm` bulk (33 modules).** Every pure-JVM `cbox/common/*`
  shared module converted to `sage.kmp` — the bulk of the original
  "Mass module conversion" item. Namespaces synthesised from the
  **module Gradle path** including the `common.` segment (deriving from
  the source package collided with parallel `sage.android` modules in
  the manifest merge; the path-derived form keeps them globally unique).
- **`sage.android` pure subset (20 modules).** Every `sage.android`
  module whose transitive closure is now KMP/pure — the 7 emulator
  `:api` + 7 emulator `:all`, plus leaf `colors`, `contentsource/file`,
  `database`, `image-loading`, `repository`, `scanner` `:api` modules.
- **What stayed `sage.android`**, split honestly between two reasons:
  - **Genuinely Android-only (correct end state, not transitional).**
    Every `:di` module — Hilt is Android-only DI glue, and the JVM app
    doesn't need it (it wires manually in `Main.kt`); the `:di` modules
    are app-level Android plumbing, not player-core code, so they're
    correctly Android-flavored. The resource-touching `strings/api` and
    `ui/fonts/api` (`R.string.*` / `R.font.*` references — not visible
    as `import android.*`; resources don't exist on JVM). The real
    Android feature modules under `cbox/android/{appui,images,
    artworkprovider,player-status,player/speaker/real,ui/*}` (Compose
    UI, audio service, image loading — all genuinely Android).
  - **Blocked on item 4** (the only real remaining KMP work):
    `cbox/android/scanner/{real,fake,all}` and
    `cbox/android/contentsource/file/{real,all}`. The Android scanner
    uses SAF (`DocumentsContract`, `Uri`, `Context`) and the file
    `ContentSource` wraps SAF. Abstracting that behind a `LibrarySource`
    interface lets both Android (SAF impl) and JVM (`java.io.File` impl)
    drive `RealScanner`'s metadata/PSF-chain logic from one module.
    Until then, the JVM has its own `JvmLibraryScanner` doing minimal
    file-walking (no metadata extraction).

  (Earlier doc revisions claimed Room/database modules and `:di` were
  also blocked. Room is now KMP — see Milestone 4. `:di`/Hilt is not
  blocking anything: per the migration's stated goal of running the
  player core on both targets, Hilt is app glue, not player core.)

A small inspectable Python helper (`scripts/kmpify.py`) drove the bulk
rewrite — kept around for the next pass.

## Milestone 4 — Room is KMP, JVM has a real library (done)

The JVM target stops being a one-track WAV writer and becomes a real
music library, sharing the **same Room schema** the Android app uses.

- **Room storage stack on `sage.kmp`** (commit `e6bb188d`). Room 2.7+
  is multiplatform, so `@Database` / `@Dao` / `@Entity` work for both
  the android and jvm variants from one module. The conversions:
  `cbox/common/entities/api`, `cbox/android/database/real`,
  `cbox/android/repository/real`, `cbox/android/database/all`.
  `database/real` applies KSP locally with `kspAndroid` / `kspJvm`
  configurations for `room-compiler`, declares
  `androidx.sqlite:sqlite-bundled` on `jvmMain` only (Android keeps the
  framework SQLite via the existing `databaseBuilder(Context, …)`
  overload). `ChipboxDatabase` gained `@ConstructedBy(ChipboxDatabase
  Constructor::class)` + the matching `expect object … :
  RoomDatabaseConstructor<ChipboxDatabase>`; Room's KSP generates the
  per-target `actual` impls.
- **Suspend cascade absorbed.** Room KMP requires every non-`Flow` DAO
  method to be `suspend` on non-Android targets. ~30 DAO methods + 4
  `Repository` interface methods (`getTrack`,
  `getTracksForGame|Artist|Platform`) became `suspend`;
  `DatabaseRepository` rewritten with a small `suspendMap` helper for
  the `Iterable.map`-with-suspending-transform spots; `RealDirector`
  private wrappers became `suspend`. Every existing call site was
  already in a coroutine context, so zero call-site changes were
  needed across `Generator`, `LibraryBrowser`, and `RealDirector`.
- **JVM library wiring** (commit `8d9b3717`). `apps/jvm` builds the
  database with `Room.databaseBuilder<ChipboxDatabase>(name = …)
  .setDriver(BundledSQLiteDriver())`, constructs a `DatabaseRepository`
  for it, and exposes three `Main.kt` modes:
    - `scan <music-dir>` — walks via the new `JvmLibraryScanner`,
      persists tracks/games into `.chipbox-jvm/library.sqlite`.
    - `play <track-id|title-substring>` — resolves a track from the DB
      and renders to WAV through the existing native pipeline.
    - `<file-path> [output-dir]` — legacy single-file path (no DB),
      still backed by `SingleTrackRepository` for one-off renders.
  The scanner is minimal (~80 LOC, one `RawGame` per folder, no
  metadata extraction — track titles are filenames). Sharing
  `RealScanner`'s richer metadata/PSF logic with the JVM is the
  remaining item-4 work (the SAF abstraction below).
- Verified: `:apps:android:assembleDebug` green; the JVM `scan`+`play`
  loop indexed a small SPC/minipsf corpus and rendered audible WAVs
  for both (the `psflib` chain auto-staged from the persisted
  `RawTrack.chainFiles`).

### Diagnoses corrected during this work

A few things I asserted earlier turned out to be wrong; recording them so
the doc and reader stay honest:

- **No module needs `expect`/`actual` today.** Both candidates I flagged
  (the generator's cache dir; an audio sink later) turn out to be
  caller-supplied config or factory choices, not platform implementation
  seams. A genuine `expect`/`actual` may still appear for real-time
  audio (item 1), but it's not certain.
- **`runtimeOnly(:native)` does propagate jniLibs** through a transitive
  library dependency to the consuming app's APK — my initial fear was
  unfounded. The runtime crash that surfaced was a pre-existing missing
  `:usf:di` line in `apps/android/build.gradle.kts`, not a packaging
  failure (commit `56fa46db`).
- **The convertibility closure must consider Android resources.** The
  initial check grepped `^import android.*`; that misses `R.string.*` /
  `R.font.*` references (the `R` class is generated in the module's own
  package, no import line). Caught at compile, fixed by reverting two
  files (commit `7936ba06`). Future kmpify runs should grep
  `\bR\.(string|drawable|font|color|...)` as an Android marker too.
- **Hilt is not a KMP-conversion blocker.** Earlier roadmap framing
  said "replace Hilt to unblock item 2." On second look: Hilt is
  Android app-wiring glue (entry points, app/activity components),
  not player-core code. The `:di` modules being `sage.android` is
  their natural end state — they're not transitional artifacts
  blocking anything. The headless JVM target instead carries its own
  plain-Dagger `@Component` (`apps/jvm/.../di/JvmChipboxComponent`)
  with sibling `@Module` classes that mirror each Hilt module's
  bindings — Hatchet, Database (bundled SQLite driver), Repository
  (`DatabaseRepository`), ContentSource (`LocalFileContentSource`),
  Buffer, Emulators (7 native + `EmulatorProvider`), Readers,
  Scanner, Generator, Speaker. The trivial provider methods are
  duplicated rather than shared (the cbox `:di` modules apply Hilt's
  Android plugin and don't expose a JVM variant; dragging them into
  a `sage.jvm` app's classpath fights Gradle variant resolution).
  Hilt stays entirely intact on Android; the JVM gets typesafe DI
  parity without it.

## Roadmap (not yet done)

1. **Real-time JVM audio.** An audio sink (probably a factory, possibly
   `expect`/`actual`): Android `AudioTrack` vs a JVM
   `javax.sound.sampled.SourceDataLine` speaker, so the JVM target plays
   live instead of only writing WAV. Tune the render-ahead window at the
   same time — the heavy cores currently emit a terminal
   `GeneratorEvent.Error` on a cold cache, which the WAV harness
   survives but live playback would not.
2. **Finish the KMP conversion** (smaller than before). After Room
   joined KMP in Milestone 4, the only remaining genuine blocker is the
   scanner/SAF chain (see item 4). After that lands,
   `cbox/android/scanner/*` and `cbox/android/contentsource/file/*`
   collapse onto `sage.kmp` via the next `kmpify.py` pass. The other
   modules still tagged `sage.android` (`:di`, resources, Compose UI,
   audio service) are *legitimately* Android-only end states, not
   transitional. The progressive `jvmSharedMain` → `commonMain` hoist
   is the deliberately-incremental remainder.
3. **Cross-platform native packaging.** All seven emulators wired and
   playback-verified on host x86-64 Linux. Remaining: macOS/Windows
   `.dylib`/`.dll` builds + a packaged `java.library.path`, and a real
   distribution (today blocked by the duplicate jar-basename
   `installDist` issue — see Known issues).
4. **SAF abstraction so JVM and Android share `RealScanner`.** Today
   the Android scanner depends concretely on `AndroidFileContentSource`
   (SAF / `DocumentsContract` / `Uri`); the JVM has its own minimal
   `JvmLibraryScanner` (~80 LOC, no metadata extraction). Introduce a
   `LibrarySource` interface (`locations` + `scanFiles` + `openBytes`)
   in `cbox/common/contentsource/api` with two impls — the existing
   Android SAF one and a new `LocalFileContentSource` walking
   `java.io.File`. Retype `RealScanner` against `LibrarySource` so both
   apps run the same scanner code (metadata via the KMP `Readers`,
   PSF `_lib` chain resolution, m3u overlays). After this lands, the
   scanner/contentsource chain becomes `sage.kmp` (closing item 2).
   (Hilt replacement is *not* needed — see "Diagnoses corrected".)

## Known issues / out of scope

- `FileSpeaker` writes a WAV `data`-size header field that some parsers
  read as a longer duration than the actual PCM payload (the audio bytes
  are correct; only the header arithmetic is off). Pre-existing, not
  introduced by this work; header-trusting players could mis-seek.
- Dozens of modules share a jar basename (`real.jar` / `api.jar`), which
  collide in the `application` plugin's flat distribution `lib/`, so
  `installDist` / `distZip` are unusable. The `:apps:jvm:standaloneScript`
  task is the workaround: it emits `build/run-standalone.sh` with an
  explicit classpath of full, unique jar paths (no Gradle at runtime). A
  proper fix would give every module a path-derived archive name.
- The JVM target's library scanner (`JvmLibraryScanner`) is minimal:
  one `RawGame` per folder, filename = track title, no tag/header
  metadata extraction, `trackNumber = 0` for every track so
  multi-subtrack formats (NSF/GBS) are addressed as one track. The
  richer `RealScanner` (`Readers`-driven metadata + PSF chain
  resolution + m3u overlays) ships on Android but isn't reused yet —
  roadmap item 4 introduces the `LibrarySource` abstraction that lets
  both apps run it.
- Heavy emulator cores emit a terminal render-ahead `GeneratorEvent.Error`
  on a cold cache (see Milestone 2b) — survivable for the WAV harness,
  not for live playback until the render-ahead window is tuned.
