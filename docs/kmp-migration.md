# Kotlin Multiplatform migration — plan & status

Status: **Milestone 6 implemented.** JVM is the second target with a real
library *and* a Compose Multiplatform desktop window. All seven native
emulators decode end-to-end on host x86-64 (playback-verified), the
headless app also runs without Gradle via a generated launcher, the mass
`sage.android`/`sage.jvm` → `sage.kmp` conversion collapsed 61 modules
onto single KMP modules serving both variants (`cbox/jvm/` is empty,
deleted), the Room storage stack is now KMP (via Room 2.7+'s multiplatform
support + the bundled SQLite driver) and the JVM target uses the *same*
Room database the Android app does — driven by the *same* `RealScanner`
via a `LibrarySource` abstraction (so the SAF / file walk is the only
platform implementation, not the metadata logic). The JVM target now also
opens a desktop window via Compose Multiplatform — placeholder Hello
composable rendering the real Chipbox color palette **and** the real
Chipbox typography structure (sizes, weights, line heights) out of a
new shared `sage.compose.kmp` module; only the custom pixel-art fonts
themselves are still Android-only (system font fallback on JVM). The remaining `sage.android`-classified modules
split between **genuinely Android-only system glue** (Hilt `:di`, `R.*`
resource modules, audio service, SAF / ContentProvider) and **the Compose
UI surface**, which used to be tagged as legitimately-Android end-state
but is now being ported slice by slice to Compose Multiplatform — see
Milestone 6.

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
- **What stayed `sage.android`** (and why):
  - **Genuinely Android-only system glue (correct end state, not
    transitional).** Every `:di` module — Hilt is Android-only DI glue,
    and the JVM app doesn't need it (it wires manually via a plain-Dagger
    `@Component`; see Milestone 5). The resource-touching `strings/api`
    and `ui/fonts/api` (`R.string.*` / `R.font.*` references). The audio
    service (`player/speaker/real` uses `AudioTrack`), the SAF /
    ContentProvider wrappers (`artworkprovider`, `contentsource/file/real`).
  - **Compose UI surface — being ported to Compose Multiplatform.**
    `cbox/android/{appui,images,player-status,ui/*}` plus the
    `features/*` Compose screens were earlier classified as legitimate
    end-state too, but that classification was wrong: Compose
    Multiplatform now runs on the JVM target, so these modules are
    candidates for a slice-by-slice port to `sage.compose.kmp` — see
    Milestone 6.
  - The scanner/SAF chain (`cbox/android/scanner/{real,fake,all}` +
    `cbox/android/contentsource/file/*`) was previously listed as a real
    KMP blocker; the `LibrarySource` abstraction in Milestone 5 lifted
    that — `scanner/real` is now `sage.kmp`. The SAF-wrapping
    `contentsource/file/real` legitimately stays Android (it's the
    Android impl of `LibrarySource`); the JVM gets `LocalFileContentSource`.

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

## Milestone 5 — SAF abstraction + JVM Dagger graph (done)

Two adjacent slices that closed the long-standing "real KMP blocker"
(scanner/SAF) and fleshed out the JVM's DI story.

- **SAF abstraction** (commit `96fcdb88`). New `LibrarySource` interface
  in `cbox/common/contentsource/api` (KMP) — adds `locations:
  StateFlow<List<LibraryLocationInfo>>` and `scanFiles(): Flow<LibraryFileInfo>`
  on top of `ContentSource`. `AndroidFileContentSource` implements it
  (projects SAF `Uri.toString()` to a platform-neutral `identifier`).
  `RealScanner` retypes against `LibrarySource` and moves to `sage.kmp` —
  every `file.uri.toString()` → `file.identifier`;
  `openInputStream(uri).use { readBytes() }` → `openBytes(identifier)`.
  The JVM target deletes `JvmLibraryScanner` (the ~80-LOC filename-only
  walker) and gains `LocalFileContentSource` (a `LibrarySource` walking
  `java.io.File`). The `scan` mode now persists real metadata-derived
  titles ("Wind Scene" not "109 Wind Scene") because it runs the same
  `RealScanner` Android does.
- **JVM Dagger graph** (commit `07893db1`). A plain-Dagger `@Component`
  (`apps/jvm/.../di/JvmChipboxComponent`) with nine `@Module` objects
  mirroring the Hilt modules shape-for-shape (Hatchet, Database with the
  bundled SQLite driver, Repository, ContentSource, Buffer, Emulators,
  Readers, Scanner, Generator, Speaker). Hilt itself stays Android-only;
  pulling the `cbox/.../di` modules into a `sage.jvm` app classpath
  fights Gradle variant resolution, so the JVM-side `@Module` classes
  duplicate the trivial provider methods. A few lines per binding, no
  cross-module plumbing required. `Main.kt`'s `scan`/`play` modes build
  the component once and pull singletons.

Verified: `:apps:android:assembleDebug` green; the JVM `scan` of an SPC
+ minipsf corpus persists tracks with metadata-derived titles; `play
<substring>` renders audible WAVs through the same pipeline.

## Milestone 6 — Compose Multiplatform desktop bootstrap (done)

First two slices of porting the Android Compose UI to the JVM target.
Proves the toolchain end-to-end and ports the lowest-risk shared piece
(the color palette) without yet committing to navigation, ViewModel
scoping, font/resource, or image-loading stories.

- **Slice 1 — toolchain bootstrap** (sage commit `7bf0089a`, chipbox
  commit `597e9ef0`). New `sage.compose.kmp` convention plugin mirrors
  `sage.compose.android` but layers on `sage.kmp` instead of
  `com.android.library`. Applies the Kotlin Compose compiler plugin
  (same one the Android UI uses) and adds JetBrains Compose runtime /
  foundation / material3 / ui libs to `commonMain`. Catalog gets
  `composeMultiplatform = "1.9.0"` (latest stable lib line — 1.10+ ship
  the Gradle plugin but the lib jars only go to 1.9.0 stable; CMP
  tolerates newer Kotlin because @Composable codegen lives in the
  Kotlin compiler plugin, not in CMP itself) plus four
  `jetbrains-compose-*` library aliases and the `compose-multiplatform`
  plugin alias. `apps/jvm` (still `sage.jvm`, not KMP) applies
  `compose.compiler` + `compose.multiplatform`, depends on the four
  catalog libs + `compose.desktop.currentOs` for the per-OS Skia native,
  and grows a `gui` arg mode that calls `runDesktop()` →
  `application { Window { HelloChipbox() } }`. `gui` is dispatched
  before `runBlocking` so the Compose event loop owns the main thread
  cleanly; the existing CLI dispatch moves into a private suspend
  `dispatch()`.
- **Slice 2 — shared color palette**. New `cbox/common/ui/theme/api`
  KMP module on `sage.kmp + sage.compose.kmp` — first real consumer of
  the new convention plugin. `Colors.kt` (the `md_theme_*` constants
  plus `ChipboxLight`, `ChipboxDark`, `ChipboxMenu` `ColorScheme`s)
  moves into `commonMain`; both the existing Android `AppTheme` and the
  JVM `DesktopMain` resolve them from there. The desktop bootstrap
  drops its inlined hex constants in favour of the real palette.
- **Slice 3 — typography structure + multiplatform `ChipboxTheme()`**.
  The typography tokens (`ChipboxTypeScaleTokens`,
  `ChipboxTypographyTokens`, the weight half of `ChipboxTypefaceTokens`)
  and the `buildChipboxTypography(...)` builder move to `commonMain`;
  the builder's signature changes from `(brand: ChipboxFont, plain:
  ChipboxFont, fontScale)` to `(brand: FontFamily, plain: FontFamily,
  brandScale: Float, plainScale: Float)` so callers fold the per-font
  `scaleFactor` in themselves. New `ChipboxTheme()` /
  `ChipboxThemeMenu()` composables in `commonMain` inline the previous
  `SageMaterial` wrapping (`isSystemInDarkTheme()` + scheme pick +
  `MaterialTheme(colorScheme, typography)`) — `isSystemInDarkTheme()`
  itself is multiplatform (it lives in `androidx.compose.foundation`)
  so no Android-only theme dep is needed on the JVM side. The Android
  `AppTheme()` becomes a thin wrapper that converts ChipboxFont →
  FontFamily via the existing `Font(resId)` factory and delegates to
  the shared `ChipboxTheme()`. `DesktopMain.kt` calls
  `ChipboxTheme { ... }` directly, picking up real Chipbox type
  sizes/weights/line heights with system-default fonts. One small
  `expect`/`actual` is needed for the base `TextStyle`: Android keeps
  its `PlatformTextStyle(includeFontPadding = false)` tweak (the legacy
  Android font-padding default), JVM gets plain `TextStyle.Default`.

Explicit non-goals of this milestone (each will land as its own slice
once the strategy is picked — see Roadmap item 1):

- Custom Chipbox fonts on JVM. The pixel-art `.otf`s ship as Android
  `R.font.*` resources today; the JVM theme falls back to
  `FontFamily.Default`. Needs either Compose-MP resources or an
  `expect`/`actual` `FontFamily` factory that resolves font names off
  the JVM classpath.
- Navigation library. `androidx.navigation.compose:2.9.8` is
  Android-only; the CMP fork / Voyager / Decompose all viable.
- ViewModel scoping on Desktop. `hiltViewModel()` is Android-only — a
  `chipboxViewModel<T>()` helper backed by Hilt on Android and by the
  plain-Dagger graph (Milestone 5) on Desktop is the obvious shape.
- Image loading. Coil 3 is multiplatform now; the `:images` wrapper
  uses `SingletonImageLoader.get(context)` and needs a context-free
  entry on JVM.
- Strings. CMP `Res.string.*` vs keeping the `ChipboxStringId`
  indirection + a desktop `StringProvider`.

Verified each slice: `:apps:android:assembleDebug` green;
`:apps:jvm:compileKotlin`, `:apps:jvm:detekt`, and
`:cbox:common:ui:theme:api:check` clean; `:apps:jvm:run --args="gui"`
opens a desktop window with the real Chipbox palette.

## Roadmap (not yet done)

1. **Compose Multiplatform UI port.** Multi-slice; Milestone 6 covers
   the first three (toolchain, palette, typography). Remaining slices
   roughly in order: pick a font-resource strategy and ship the
   pixel-art `.otf`s on the JVM classpath so `ChipboxFont` works on
   desktop; pick a navigation library (`androidx.navigation.compose:2.9.8`
   is Android-only; the CMP fork / Voyager / Decompose are all viable);
   pick a ViewModel-scoping pattern on Desktop (a
   `chipboxViewModel<T>()` helper backed by Hilt on Android and by the
   plain-Dagger graph on Desktop is the obvious shape); pick an
   image-loading story (Coil 3 is multiplatform now); pick a strings
   story (CMP `Res.string.*` vs keeping `ChipboxStringId` indirection);
   then port real feature modules (`appui`, `features/*`) slice by slice.
2. **Real-time JVM audio.** An audio sink (probably a factory, possibly
   `expect`/`actual`): Android `AudioTrack` vs a JVM
   `javax.sound.sampled.SourceDataLine` speaker, so the JVM target plays
   live instead of only writing WAV. Tune the render-ahead window at the
   same time — the heavy cores currently emit a terminal
   `GeneratorEvent.Error` on a cold cache, which the WAV harness
   survives but live playback would not.
3. **Cross-platform native packaging.** All seven emulators wired and
   playback-verified on host x86-64 Linux. Remaining: macOS/Windows
   `.dylib`/`.dll` builds + a packaged `java.library.path`, and a real
   distribution (today blocked by the duplicate jar-basename
   `installDist` issue — see Known issues).
4. **The `jvmSharedMain` → `commonMain` hoist.** Per-module audit/move
   pass: code in `src/main/java` (still under `jvmSharedMain` via the
   `sage.kmp` plugin's intermediate source set) that doesn't actually
   reach `java.*` migrates into `src/commonMain/kotlin`. Pure cleanup —
   no functional change, only a tighter contract on what `commonMain`
   may not use. Deliberately incremental.

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
