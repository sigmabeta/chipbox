# Kotlin Multiplatform migration — plan & status

Status: **Milestone 8 implemented; Milestone 9 in progress (slices
1–5d done + 6a/6b/6c/6d/6e/6f-pre/6f/6g done — Milestone 9 slice 6
complete).** JVM is the second
target with a real library *and* a
Compose Multiplatform desktop window with real pixel-art fonts,
Metro-backed ViewModel scoping, and Voyager-driven navigation. All
seven native emulators decode end-to-end on host x86-64
(playback-verified), the headless app also runs without Gradle via a
generated launcher, the mass `sage.android`/`sage.jvm` → `sage.kmp`
conversion collapsed 61 chipbox modules onto single KMP modules serving
both variants (`cbox/jvm/` is empty, deleted), the Room storage stack
is now KMP (via Room 2.7+'s multiplatform support + the bundled SQLite
driver) and the JVM target uses the *same* Room database the Android
app does — driven by the *same* `RealScanner` via a `LibrarySource`
abstraction. `features/settings/real` + `features/library/real` are the first
chipbox features on `sage.kmp`; their VMs contribute into both
Android's and JVM's Metro graph.

The desktop window renders via Compose Multiplatform: shared color
palette + typography from `cbox/common/ui/theme/api`, real pixel-art
fonts via Compose Multiplatform resources from `cbox/common/ui/fonts/api`,
`metroViewModel<T>()` from `metrox-viewmodel-compose` after the Hilt →
Metro migration (`docs/metro-migration.md`), and Voyager navigation
(`Screen` interface + `LocalNavigator`). The sage submodule's entire
`common/*` library tree is now KMP-published and the types the Settings
ViewModel chain depends on (`SageAction`, `LCE`, `ListState`,
`ListStateActual`, `StringProvider`, `ListModel` hierarchy) all live in
commonMain.

The remaining `sage.android`-classified chipbox modules are
**genuinely Android-only system glue** (`R.*` resource modules, audio
service, SAF / ContentProvider) plus **the Compose UI surface**, which
used to be tagged as legitimately-Android end-state but is being
ported slice by slice to Compose Multiplatform — see Milestones 6, 7,
9. (`:di` modules were on this list pre-Metro; after Hilt + plain
Dagger were dropped at the Metro migration's Milestone 6, DI plays
nicely with `sage.kmp`.)

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
- **Slice 3 — typography structure + multiplatform `ChipboxTheme()`** (commit `b47f95d4`).
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
- **Slice 4 — Chipbox pixel fonts on JVM via CMP resources** (sage
  `39475a8d`, chipbox `3b1eacae`). The 18 `.otf` font files move from
  Android `R.font.*` resources to Compose Multiplatform resources. New
  `cbox/common/ui/fonts/api` KMP module on
  `sage.kmp + sage.compose.kmp + compose.multiplatform` (the JetBrains
  plugin, applied here for the `Res.font.*` codegen). ChipboxFont enum
  loses `fontResId: Int`, gains `resource: FontResource` + a
  `@Composable fun toFontFamily()` member wrapping CMP's
  `Font(FontResource)` factory. `cbox/android/ui/theme/api`'s
  `tokens/Fonts.kt` (the old Android-only `Font(resId)` extension)
  deletes; `ChipboxFontDefaults` moves into the shared theme module's
  commonMain. apps/jvm pulls the fonts module transitively via the
  theme module; `DesktopMain` passes `ChipboxFontDefaults.Brand/
  Plain.toFontFamily()` into `ChipboxTheme` with each font's
  `scaleFactor` folded in. Desktop window now renders in the real
  pixel fonts. Catalog wrinkle: JB Compose plugin 1.9.0 NoSuchMethod
  Errors on AGP 9's `KotlinMultiplatformAndroidComponentsExtension
  .onVariant` for sage.kmp modules; bumped the plugin to 1.9.3 while
  keeping the lib jars at 1.9.0 (material3 stable is stuck there;
  plugin manages its own internal lib version for codegen contract).
- **Slice 5 — multiplatform ViewModel scoping** (commit `42d39548`).
  New `cbox/common/ui/vm/api` KMP module exporting an
  `open class ChipboxViewModel` marker base (deliberately *not*
  `androidx.lifecycle.ViewModel` yet — the KMP lifecycle artifact's
  `onCleared`/scope shape lands when the first real Android port
  needs it), a `ViewModelProvider` interface,
  `LocalViewModelProvider` `staticCompositionLocalOf` carrying it,
  and `@Composable inline fun <reified T : ChipboxViewModel>
  chipboxViewModel(): T`. Demo: a tiny
  `HelloViewModel @Inject constructor(hatchet: Hatchet) :
  ChipboxViewModel()` in apps/jvm, surfaced via
  `JvmChipboxComponent.helloViewModel()`. `JvmViewModelProvider`
  dispatches `KClass<T>` to the component accessor via `when`. The
  desktop window now observes a `StateFlow` driven by a real
  Dagger-injected VM. The Android `actual` (Hilt bridge) is deferred
  until the first real Android consumer arrives.
- **Slice 6 — Voyager navigation on Desktop** (sage `1a474e3a`,
  chipbox `9d1268f6`). Picked Voyager over the AndroidX
  `navigation-compose` CMP fork after discovering the AndroidX fork
  (latest stable 2.9.8) publishes JVM variants whose
  `jvmStubsRuntimeElements-published` has *zero files* — compile-time
  stubs only, no actual desktop runtime. Voyager 1.0.1 stable has a
  real `voyager-navigator-desktop-1.0.1.jar`. New `HomeScreen` (data
  object Screen, wraps the slice-5 Hello+VM content plus a "Go to
  About" button) and `AboutScreen` (data object Screen, Text + Back
  button). `DesktopMain`'s root composable is now
  `Navigator(HomeScreen)` inside the existing ChipboxTheme +
  CompositionLocalProvider wrapping. Real feature screens follow the
  same shape: `data object`/`data class Screen` per route,
  `LocalNavigator.currentOrThrow` for stack manipulation. Per-screen
  ViewModel scoping isn't tied into Voyager's screen scope yet (every
  `chipboxViewModel<T>()` call still hits the JvmViewModelProvider
  directly); that integration lands alongside the first real Android
  bridge.

Explicit non-goals of this milestone (still pending, see Roadmap item 1):

- Image loading. Coil 3 is multiplatform now; the `:images` wrapper
  uses `SingletonImageLoader.get(context)` and needs a context-free
  entry on JVM.
- Strings. CMP `Res.string.*` vs keeping the `ChipboxStringId`
  indirection + a desktop `StringProvider` impl.
- Android-side actual of `chipboxViewModel<T>()` — a Hilt wrapper.
  Easy slice; deferred until the first real Android consumer needs it.

Verified each slice: `:apps:android:assembleDebug` green;
`:apps:jvm:compileKotlin`, `:apps:jvm:detekt`, ktlint, and per-module
`:check` clean; `:apps:jvm:run --args="gui"` opens a desktop window
that renders the real Chipbox palette + typography + pixel fonts, lets
you navigate Home → About → back, and shows a VM-supplied
`StateFlow<String>` updating via `collectAsState`.

## Milestone 7 — sage submodule KMP foundation (done)

Four slices that flip every `sage/common/*` library from `sage.jvm` to
`sage.kmp` and hoist their pure-Kotlin types into commonMain, so future
Chipbox commonMain modules can depend on them. This unblocks the
chipbox-side port of `ChipboxListViewModel` (the base class for every
Chipbox feature ViewModel) — its imports (`SageAction`, `LCE`,
`ListState`, `ListStateActual`, `StringProvider`, `ListModel`
hierarchy) all live in commonMain now.

- **Slice 1 — `common/logging` → sage.kmp** (sage commit `f9aba752`).
  First library converted. `Hatchet` interface + `BasicHatchet` +
  `BluntHatchet` are pure Kotlin (just `println` + a `when` on `Int`),
  so the three files move from `src/main/java` straight into
  `src/commonMain/kotlin`. Sets the bottom-of-chain dependency every
  other sage library uses for logging.
- **Slice 2 — bulk-convert sage/common/* to sage.kmp** (sage commit
  `05728b41`). 18 JVM-only modules flipped via a one-off Python
  script adapted from `scripts/kmpify.py`: plugin alias swapped,
  `androidLibrary { namespace }` added, project-level
  `dependencies { … }` block moved into
  `sourceSets.named("jvmSharedMain").dependencies { … }`. No source
  moves; `java.*` / Hilt / Moshi keep working from where they sit
  because `jvmSharedMain` covers both targets. Modules touched:
  `analytics, appinfo, connectivity, coroutines, debug, events,
  freeform, images, list, nav, pdf, perf, settings/environment,
  settings/general, storage/common, time, ui/components, ui/icons`.
  (appcomm + ui/strings landed in a follow-up slice once their
  JVM-family deps were cleaned up.) One real detekt regression caught
  inline (`StringGenerator.kt` had a 121-char line that `sage.jvm`'s
  detekt task graph apparently wasn't scanning — `sage.kmp`'s
  per-source-set tasks caught it).
- **Slice 3 — appcomm + ui/strings cleanup** (sage commit `a6e486ac`).
  `common/appcomm` drops `implementation(libs.hilt.core)` and both
  Hilt KSP processor lines — a grep of `common/appcomm/src/` shows
  zero Hilt symbols, the deps were vestigial. `GenericAction.kt`
  swaps Moshi for `kotlinx.serialization`: `@JsonClass(generateAdapter
  = true)` → `@Serializable`. GenericAction is sage's only Moshi
  user, has downstream consumers in VGLS, and kotlinx.serialization
  is already in the catalog and fully multiplatform. Net: appcomm
  becomes KSP-free, codegen-free, JVM-family-host-free.
  `common/ui/strings` drops a vestigial Moshi dep (zero source
  imports of it).
- **Slice 4 — hoist 59 pure-Kotlin files to commonMain** (sage commit
  `9ee7afae`). Every file in scope (appcomm, appinfo, list,
  ui/components, ui/strings) moves from `src/main/java` to
  `src/commonMain/kotlin`. Strict audit: any `import java.*` /
  `javax.*` / `android.*` keeps the file in jvmSharedMain. Only
  `StringGenerator.kt` (uses `java.util.Locale` / `Random`) stays.
  After this, types like `SageAction`, `LCE`, `ListState`,
  `ListStateActual`, `StringProvider`, the `ListModel` hierarchy, etc.
  are all reachable from any Chipbox commonMain module.

Why this matters for the Chipbox-side port: the original analysis for
porting `SettingsViewModel` flagged the long sage-side dependency
chain (`androidx.lifecycle.ViewModel` base + `ChipboxListViewModel`
super + `SageAction` / `LCE` / `ListState` / `ListStateActual` /
`StringProvider` / `ListModel` …) as needing KMP conversion before any
Chipbox feature could move. Milestone 7 closes the sage-side half of
that chain. The chipbox-side remainder — `ChipboxListViewModel` itself,
a strings story, the desktop folder picker, the Android-side
`chipboxViewModel<T>()` actual, and the Settings port itself — is now
unblocked.

Sage submodule cleanup not in scope: there's a parallel
`jvmSharedMain → commonMain` hoist opportunity in other sage common
modules (analytics, debug, events, freeform, nav, pdf, perf — anything
pure Kotlin) that didn't need to happen for the Settings port. Those
move when a future consumer needs them in commonMain.

## Milestone 8 — finish the sage commonMain hoist (done)

Final pass of the `jvmSharedMain` → `commonMain` hoist across the
remaining `sage/common/*` modules. 32 pure-Kotlin files move from
`src/main/java` to `src/commonMain/kotlin` (preserved with `git mv`,
no build.gradle.kts changes — the `sage.kmp` plugin picks up
commonMain automatically because `jvmSharedMain dependsOn commonMain`).

Modules hoisted (file counts):

- `analytics` (4): ActionUtils, Analytics, AnalyticsScreen, AnalyticsScreenId.
- `debug` (2): RenderOverlayProvider, ShowDebugProvider.
- `events` (1): EventDispatcherReal.
- `freeform` (2): FreeformState, FreeformStateActual.
- `nav` (2): ArgType, RouteDescriptor.
- `pdf` (1): PdfConfigById.
- `perf` (10): FrameInfo, FrameTimeStats, InvalidateInfo, InvalidateStats,
  PerfBackend, PerfMeasurer, PerfMeasurerImpl, PerfSpec, PerfStage, plus
  ScreenLoadStatus (its `EnumMap(PerfStage::class.java)` default param
  switched to `emptyMap()` — every consumer reads the map via
  `copy(stageDurationMillis = … + (stage to value))`, so the EnumMap
  vs LinkedHashMap distinction was inert).
- `coroutines` (2): CustomFlows, SageDispatchers.
- `images` (2): SourceInfo, PdfSize.
- `storage/common` (1): Storage (the doc previously listed `storage/common`
  as "genuine JVM-only"; the file is in fact a pure-Kotlin
  interface over `kotlinx.coroutines.flow.Flow` — reclassified).
- `settings/environment` (2): AppEnvironment, EnvironmentManager (depends
  on `storage.Storage`, which is now commonMain too).
- `settings/general` (2): DebugSettingsManager, GeneralSettingsManager.
- `connectivity` (2 of 4): NetworkStatus, NetworkStatusProvider.

What stays in `src/main/java` (still jvmSharedMain) — files with
`java.*` imports that don't have a trivial multiplatform-safe
rewrite:

- `connectivity/HttpException` + `NetworkUnavailableException` —
  extend `java.io.IOException` (caller contract).
- `common/ui/strings/StringGenerator` — `java.util.Locale`/`Random`
  (carry-over from Milestone 7 slice 4).
- All of `common/time` — `org.threeten.bp.*` (ThreeTenABP).

Compile noise observed (warnings, not errors): every module's pure
commonMain compilation prints `Opt-in requirement marker
kotlinx.coroutines.ExperimentalCoroutinesApi is unresolved` because
the `sage.kmp` convention plugin adds the opt-in arg unconditionally
and commonMain-without-coroutines compilations can't resolve the
marker class. Harmless — no generated-code impact. Cleanup would be
a one-liner in `SageKmpModulePlugin` (gate the opt-in to source sets
that depend on kotlinx-coroutines) but is out of scope here.

Verified: every touched sage module's `:build` green for both Android
+ JVM variants; chipbox `:apps:android:assembleDebug` +
`:apps:jvm:compileKotlin` unchanged. Zero downstream source changes
— the FQCN coordinates are stable; consumers continue to resolve the
types from whichever source set still sees them after the move.

## Milestone 9 — Settings port (in progress)

The eight-slice arc that ports the Settings feature to Compose
Multiplatform on the JVM target. The sage side (M7) and chipbox
KMP foundation (M8) are done; this milestone is the chipbox-side
remainder, sliced by dependency order.

- **Slice 1 — strings strategy + JVM `StringProvider` (done).**
  Picked the "keep the `ChipboxStringId` indirection" path over a
  full move to Compose Multiplatform `Res.string.*` — minimum blast
  radius, no call-site churn, the abstraction already lives in sage
  commonMain after M7. New `apps/jvm/.../strings/JvmStringProvider`
  is a generic `Map<SageStringId, String>`-backed impl: the four
  `getString*` overrides delegate to `String.format()` for the arg
  variants (the same `%s`/`%d` syntax Android's resource strings
  already use). Missing keys throw — wrong IDs are programming
  errors caught at first use, not silently empty UI. The chipbox
  strings map (`chipboxJvmStrings`) is auto-generated by
  `scripts/gen_jvm_strings.py` from the Android `strings-*.xml`
  files: every entry whose name matches a `ChipboxStringId` enum
  entry goes into the map (144 of 151 — the 7 skipped are
  notification-channel / activity-label / playback action strings
  used directly by name on Android, never via `StringProvider`).
  A new `JvmStringsModule` provides the `StringProvider` binding
  in `JvmChipboxComponent`; apps/jvm picks up
  `projects.cbox.common.strings.api` to see the enum. Doesn't move
  the door — if a later slice wants to flip to `Res.string.*`,
  every call site stays the same.

  Future strings maintenance: edit the Android `strings-*.xml`,
  re-run `scripts/gen_jvm_strings.py`. Same one-way-sync pattern
  as `ChipboxStrings.kt`'s `R.string` mapping.

- **Slice 2 — `ChipboxListViewModel` on commonMain (done).** The
  base ViewModel every Chipbox feature screen extends now lives in
  commonMain. Three prerequisite moves first:

  - **Hoist `cbox/common/appcomm/api` to commonMain.** Both files
    in scope (`ChipboxEvent`, `ChipboxAction`) are pure-Kotlin
    sealed/open classes — strict audit passes with no
    `java.*`/`android.*` imports. Same `git mv` pattern as
    Milestones 7/8.
  - **Add `androidx-lifecycle-viewmodel` to the sage catalog.**
    `androidx.lifecycle:lifecycle-viewmodel` 2.8+ publishes
    multiplatform artifacts that expose `ViewModel` +
    `viewModelScope` in commonMain; pinned to the existing
    `androidxLifecycle = 2.10.0` version. The existing
    `lifecycle-runtime-compose` / `lifecycle-viewmodel-compose`
    aliases stay Android-only.
  - **Promote the `ChipboxViewModel` marker** (M6 slice 5,
    `cbox/common/ui/vm/api`) to extend
    `androidx.lifecycle.ViewModel`. M6 slice 5 deliberately left
    it empty pending the first real Android consumer; this is
    that consumer. `cbox/common/ui/vm/api` now `api`-depends on
    the new lifecycle-viewmodel alias from commonMain.

  Then the conversion proper:

  - **`cbox/android/ui/list/api` → `sage.kmp + sage.compose.kmp`.**
    Plugin swap; namespace stays
    `net.sigmabeta.chipbox.common.ui.list.api`; deps split between
    `commonMain` (sage common types + appcomm + ui/vm +
    lifecycle-viewmodel) and `androidMain` (the Android sage
    list screens, chrome, lifecycle-compose helpers).
  - **`ChipboxListViewModel.kt` → `src/commonMain/kotlin/`.** Now
    extends `ChipboxViewModel` (which itself extends
    `androidx.lifecycle.ViewModel`), picks up `viewModelScope`
    from the multiplatform artifact, and reaches `ChipboxEvent`
    from the now-commonMain appcomm hoist. No body changes
    beyond the supertype + import.
  - **`ChipboxListEntry.kt` → `src/androidMain/kotlin/`.** The
    Compose scaffolding still binds to the Android-only
    `sage.android.ui.list` `ListScreen`/`GridScreen` (slice 5
    will KMP-ify those) and uses Android-only Compose helpers
    (`LocalConfiguration`, `collectAsStateWithLifecycle`), so it
    legitimately stays on the Android target until slice 5
    lifts the sage-list dependency.

  Verified: `:cbox:common:ui:list:api:build` green for both
  Android + JVM variants, `:apps:android:assembleDebug` green,
  `:apps:jvm:check` (incl. detekt) green. No downstream source
  changes — every existing Chipbox feature VM still extends
  `ChipboxListViewModel` from the same FQCN.

- **Slice 3 — Android actual of `chipboxViewModel<T>()` (done).**
  Now that `ChipboxViewModel` extends `androidx.lifecycle.ViewModel`
  (slice 2) and `ChipboxListViewModel` lives in commonMain, the
  Android side needs a `ViewModelProvider` that resolves a
  `KClass<out ChipboxViewModel>` through Hilt the same way
  `hiltViewModel<T>()` does internally.

  - **`ViewModelProvider.get()` is `@Composable` now.** The Android
    impl needs `LocalViewModelStoreOwner` / `LocalContext` out of
    composition to find the right scope and factory; the JVM impl
    just doesn't read them. `chipboxViewModel<T>()` loses its
    `@ReadOnlyComposable` annotation because the Android impl
    delegates to `viewModel(modelClass, factory)`, which produces
    composition state. `JvmViewModelProvider`'s override gains a
    matching `@Composable` annotation — its body unchanged.
  - **`apps/android/.../vm/AndroidHiltViewModelProvider`.** Pulls
    the active owner out of `LocalViewModelStoreOwner`, wraps its
    default factory in `androidx.hilt.navigation.HiltViewModelFactory`
    (a top-level function under a `@JvmName`-rewritten façade —
    artifact `hilt-navigation`, separate from the
    `hilt-navigation-compose` already on the classpath; the catalog
    now has the standalone `androidx-hilt-navigation` alias and
    apps/android pulls it directly), and asks
    `androidx.lifecycle.viewmodel.compose.viewModel(modelClass,
    viewModelStoreOwner, factory)` for the instance. Generic —
    any `@HiltViewModel`-annotated `ChipboxViewModel` resolves
    with zero per-VM wiring. Scope follows Hilt's normal rules:
    inside `NavHost { composable { ... } }` the owner is a
    `NavBackStackEntry` and the VM dies with the screen; otherwise
    the activity-rooted owner is used.
  - **`MainActivity` wraps `setContent` in
    `CompositionLocalProvider(LocalViewModelProvider provides …)`.**
    Single instance per activity (held in a `private val`); the
    provider has no state of its own, so an instance per
    composition isn't necessary.

  No existing call sites change — every Chipbox feature
  `Route.kt` still uses `hiltViewModel()`. The provider goes live
  the moment slice 6 ports `SettingsRoute` to commonMain and its
  `hiltViewModel()` becomes `chipboxViewModel()`.

  Verified: `:apps:android:assembleDebug` + `:apps:jvm:check` both
  green.

- **Slice 4 — `sage/android/ui/list` on commonMain (done).**
  `ListScreen`, `GridScreen`, and the `LocalListBottomInset`
  composition-local all hoist to `src/commonMain/kotlin` — the
  Compose APIs they use (`LazyColumn`, `LazyVerticalGrid`,
  `LazyVerticalStaggeredGrid`, `WindowInsets.navigationBars`,
  `Modifier.animateItem`) are all in Compose Multiplatform's
  foundation. Module plugin flips to `sage.kmp + sage.compose.kmp`;
  the namespace stays `net.sigmabeta.sage.android.ui.list` (M3
  precedent — KMP modules keep their pre-conversion FQCNs even when
  the path/name has "android" in it). Deps split: sage common types
  in commonMain; `androidx.navigation.compose` in androidMain
  (where `NavArgType.kt` legitimately stays — it pins to
  AndroidX's `NavType`, which has no multiplatform replacement until
  Voyager-side `ArgType` codecs land).

  Verified: `:sage:android:ui:list:build` green for both Android +
  JVM variants, `:cbox:common:ui:list:api:build` (the immediate
  downstream consumer, post-slice 2) green, `:apps:android:assembleDebug`
  + `:apps:jvm:check` green. With this, the entire
  `ChipboxListEntry` → `ListScreen`/`GridScreen` chain can move to
  commonMain in slice 6 — at that point `ChipboxListEntry.kt` itself
  promotes from androidMain to commonMain too (its remaining
  Android-only call, `collectAsStateWithLifecycle`, swaps to plain
  `collectAsState`).

- **Slice 5a — Settings feature value types on commonMain (done).**
  First sub-slice of the Settings feature port. Scope: hoist the
  rendering value types into a KMP-published `features/settings/api`
  so the desktop UI can build the same screen from the same types
  the Android UI uses. SettingsViewModel itself stays in
  `features/settings/real` as `sage.android` for now (Hilt
  processing on `com.android.kotlin.multiplatform.library` would
  require new plumbing in `SageDiAndroidModulePlugin` and isn't
  in scope here); the JVM-side VM impl is sub-slice 5b.

  - **`LibrarySource.addLibraryLocation(identifier: String)`** is
    now on the commonMain interface. The Android impl
    (`AndroidFileContentSource`) keeps its `addLibraryLocation(uri: Uri)`
    overload for the SAF-typed call sites and routes the new
    String overload through it; the JVM `LocalFileContentSource`
    gets a native impl that treats the identifier as a directory
    path. Settles "AndroidFileContentSource swaps to LibrarySource"
    from the roadmap.

  - **`features/settings/api` → `sage.kmp`.** Plugin swap; namespace
    stays `net.sigmabeta.chipbox.features.settings.api`. Three
    files move to `src/commonMain/kotlin/`:
    - `Settings.kt` — the `@Serializable data object` route key.
    - `SettingsAction.kt` — sealed action hierarchy extending
      `ChipboxAction` (commonMain since slice 2).
    - `SettingsState.kt` — the `ListState` subtype + per-row
      `ListModel` builders. All types it references already live
      in commonMain (sage common + cbox common types).

    Deps split into `commonMain { }`: `sage.common.list`,
    `sage.common.appcomm`, `sage.common.appinfo`,
    `sage.common.ui.{components,strings}`, `kotlinx-collections-immutable`,
    plus `cbox.common.{appcomm,strings,ui.fonts}` for the
    ChipboxStringId/ChipboxFont references the `ListModel`
    builders need.

  - **`SettingsViewModel` signature changes.** Replaces
    `AndroidFileContentSource` with `LibrarySource` (the .toUri()
    call disappears — the SAF URI string flows straight through to
    `librarySource.addLibraryLocation(identifier)`). Replaces
    `PlaybackStatusEntryPoint` with two `@Named`-injected params:
    `PLAYBACK_STATUS_AVAILABLE: Boolean` and
    `PLAYBACK_STATUS_DESTINATION: Any?`. The companion exposes the
    `@Named` keys as constants; Android Hilt module wires them
    from the real `PlaybackStatusEntryPoint.isAvailable` and the
    `PlaybackStatus` route. The JVM-side bindings (to constant
    `false` / `null`) come with sub-slice 5b's VM wiring.

  - **`apps/android/.../di/ChipboxModule`** adds `@Provides` for
    `LibrarySource` (binds `AndroidFileContentSource`), the
    `@Named` Boolean (delegates to `PlaybackStatusEntryPoint`),
    and the `@Named` destination object (`PlaybackStatus`). The
    app's `build.gradle.kts` pulls
    `features.playbackStatus.api` + `features.settings.real` so
    those types are visible to the Hilt module.

  Sub-slice 5b — the JVM-side `SettingsViewModel` instantiation
  (requires a JVM `Storage` impl so `ChipboxSettingsManager` /
  `DebugSettingsManager` work; plus a JVM `@Provides` for the two
  `@Named` keys; plus a `JvmChipboxComponent.settingsViewModel()`
  accessor + `JvmViewModelProvider` dispatch arm) — is the next
  piece. Doesn't block the existing Android build or any other
  Chipbox commonMain consumer reading `SettingsState`.

  Verified: `:features:settings:api:build` green for both Android
  + JVM variants, `:features:settings:real:build` (still
  `sage.android`) green, `:apps:android:assembleDebug` green,
  `:apps:jvm:check` green.

- **Slice 5b — JVM-side settings infrastructure (done, partial).**
  Originally scoped as "wire SettingsViewModel into JvmChipboxComponent
  + add `JvmViewModelProvider` dispatch arm." Hit a real blocker
  trying to KMP-ify `features/settings/real`: the
  `com.google.dagger.hilt.android` Gradle plugin rejects
  `com.android.kotlin.multiplatform.library` targets with "The Hilt
  Android Gradle plugin can only be applied to an Android project."
  And pulling the still-`sage.android` `features/settings/real`
  module into the `sage.jvm` `apps/jvm` classpath fails Gradle variant
  resolution — the M5 problem the original doc called out. So this
  sub-slice shipped the JVM-side infrastructure pieces that *don't*
  need the VM, deferring the actual VM wiring to a future slice:

  - `apps/jvm/.../JvmStorage`: in-memory `Storage` impl backed by a
    `ConcurrentHashMap` of `MutableStateFlow`s. Values do not survive
    process death; persistence (a `java.util.Properties` file or a
    kotlinx-serialization JSON snapshot) lands when there's a real
    desktop consumer of long-lived settings. Thread-safe.
  - `JvmStorageModule` provides the `Storage` binding.
  - `JvmSettingsManagersModule` provides the chipbox
    `ChipboxSettingsManager` (via `RealChipboxSettingsManager`) and
    `DebugSettingsManager` (via `RealDebugSettingsManager`) bindings
    using `Storage` — both `sage.kmp` modules, reachable from
    `apps/jvm` without variant trouble.
  - `JvmAppInfoModule` provides a constant `AppInfo` (debug=true,
    versionName "0.1.0-jvm", buildTimeMs = `System.currentTimeMillis()`,
    buildBranch "desktop"). The Android target builds this from
    Gradle-injected `BuildConfig`; the JVM target has no
    `BuildConfig` plumbing yet so the values are explicitly fake.
  - `JvmScannerModule` gains a `@Binds Scanner` (was only `RealScanner`).
  - `apps/jvm/build.gradle.kts` gains `sage.common.{appinfo,storage.common}`
    plus the chipbox `cbox.common.{debug.api,debug.real,settings.api,settings.real}`
    deps so the modules' references resolve.

  Slice 5c (the actual JVM-side VM wiring) needs one of:
  - A way to apply `hilt-android` to a `sage.kmp` module (custom
    Gradle plumbing, possibly a `SageDiKmpModulePlugin`).
  - A Hilt-free path for chipbox feature VMs — strip `@HiltViewModel`,
    register VMs via a chipbox-side `Map<KClass, ViewModelFactory>`
    on the Android side, dispatch through it from
    `AndroidHiltViewModelProvider`.
  - Kotlin-Inject (the multiplatform DI library) for the feature
    layer, keeping Hilt only for the app-level glue.

  Verified: `:apps:android:assembleDebug` + `:apps:jvm:check` both
  green.

- **Slice 5c — KMP-ify `features/settings/real` (done).** Picked
  option 2 above by way of the broader Hilt → Metro migration: chipbox
  is Metro-only after `docs/metro-migration.md` Milestone 6, so the
  Hilt-on-KMP plugin conflict that blocked this slice is gone. The
  conversion proper:

  - **Plugin swap**: `sage.android + sage.compose.android +
    sage.di.android + metro` → `sage.kmp + sage.compose.kmp + metro`.
    Namespace stays `net.sigmabeta.chipbox.features.settings.real`
    via `androidLibrary { namespace }`. Dropped the vestigial
    `metro { interop { includeDagger() } }` block — no Dagger
    annotations left after M6. Dropped the unused
    `cbox.android.contentsource.file.real` dep (SettingsViewModel
    only references the commonMain `LibrarySource` interface).
  - **Source-set placement**:
    - `SettingsViewModel.kt` stays in `src/main/java/...` (= `jvmSharedMain`
      after the `sage.kmp` plugin's source-set mapping). Uses
      `java.time.*` for the build-date formatter; commonMain would
      need a kotlinx-datetime swap — out of scope. Every other dep
      (`Repository`, `Scanner`, `LibrarySource`, `AppInfo`,
      `StringProvider`, `ChipboxSettingsManager`, `DebugSettingsManager`,
      `ChipboxListViewModel`, `ChipboxEvent`, `SettingsState`,
      `SettingsAction`) is already commonMain after M5a + Metro M4.
    - `SettingsRoute.kt` moved to `src/androidMain/kotlin/...` via
      `git mv`. Owns the SAF folder picker
      (`rememberLauncherForActivityResult` +
      `ActivityResultContracts.OpenDocumentTree`) and consumes
      `ChipboxListEntry` which is still androidMain (Milestone 9 slice
      6 below promotes it). Desktop gets its own route in a later slice.
  - **JVM wiring**: `apps/jvm/build.gradle.kts` adds
    `implementation(projects.features.settings.real)`. Metro's compile-
    time validation in `:apps:jvm:compileKotlin` proves the JVM
    `JvmChipboxGraph` resolves the full transitive chain
    `SettingsViewModel ← ChipboxSettingsManager / DebugSettingsManager /
    Repository / Scanner / LibrarySource / AppInfo / StringProvider /
    Hatchet` through the same `@ContributesIntoMap` aggregation Android
    uses. No new accessor needed on the graph — the VM is reached via
    `metroViewModel<SettingsViewModel>()` once a desktop SettingsRoute
    composable exists.
  - **No code change to `SettingsViewModel.kt` itself**: Metro
    annotations + constructor stayed identical (slice 5a + M5c already
    landed the right shape).

  Verified: `:features:settings:real:build` green for both Android +
  JVM variants; `:apps:android:assembleDebug` green; `:apps:jvm:compileKotlin
  + :jar + :standaloneScript` green; runtime smoke test via the
  generated `run-standalone.sh scan <empty-dir>` constructs the JVM
  graph (which now includes the SettingsViewModel multibinding
  contribution) and walks the dir without error. Detekt clean across
  the touched modules; ktlint clean on touched files (the two existing
  warnings on `apps/jvm/README.md` and `JvmMetroViewModelFactory.kt`
  predate this slice).

- **Slice 5d — Library feature on sage.kmp (done).** Second chipbox
  feature ported. Library is much simpler than Settings — no
  `java.time`, no SAF, no scanner/repo deps; just a pure-Kotlin VM
  that emits nav events for four downstream browse-* screens. The
  port walks six trivial modules:

  - **Four browse-*/api route-key modules**
    (`features/browse-{all-tracks,by-artist,by-game,by-platform}/api`)
    flip from `sage.android` to `sage.kmp`. Each owns a single
    `@Serializable data object` route key with `kotlinx-serialization`
    as its only dep; the file `git mv`'s from `src/main/java/...`
    into `src/commonMain/kotlin/...` so the route keys can be
    referenced from any commonMain consumer (including the eventual
    desktop nav graph). No source changes.
  - **`features/library/api`** — same shape, same conversion: the
    `Library` route-key data object hoists to commonMain.
  - **`features/library/real`** flips to
    `sage.kmp + sage.compose.kmp + metro`. Source-set placement:
    - `LibraryAction.kt`, `LibraryState.kt`, `LibraryViewModel.kt` →
      `src/commonMain/kotlin/...`. Pure Kotlin; every dep
      (`ChipboxAction`/`ChipboxEvent`/`ChipboxListViewModel`,
      `SageAction`, `Hatchet`, `StringProvider`, `ChipboxStringId`,
      `ListModel`/`MenuItemListModel`/`TitleBarModel`,
      `ColumnType`/`ListState`, `Icon`, the four Browse* route keys
      now in commonMain) is already commonMain after M7 + M9 slices
      1–2.
    - `LibraryRoute.kt` → `src/androidMain/kotlin/...`. Still
      androidMain because it consumes `ChipboxListEntry` which is
      androidMain (slice 6 below). Same desktop-route-in-apps/jvm
      pattern Settings uses.

    Unlike Settings, library/real keeps every browse-*/api dep — they
    KMP-ified in the same slice, so the screen-by-screen "drop the
    forward dep + snackbar-stub the nav action" workaround from the
    Metro M5 slicing rules isn't needed.

  - **JVM wiring**: `apps/jvm/build.gradle.kts` adds
    `implementation(projects.features.library.real)` alongside the
    settings line from slice 5c. `LibraryViewModel` contributes into
    `JvmChipboxGraph`'s ViewModel multibinding via
    `@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())`
    (already on the class before this slice — Metro M5d sweep).

  Verified: `:features:library:real:build` green for both Android +
  JVM variants; `:apps:android:assembleDebug` green; `:apps:jvm:compileKotlin
  + :jar + :standaloneScript` green; runtime smoke test via the
  standalone scan path constructs the graph cleanly. Detekt clean on
  all six touched modules; ktlint clean on touched .kt files.

  Caught at runtime test on the Android target: tapping into Library
  crashed with `Unknown model class … LibraryViewModel`. Initially
  misdiagnosed as a KMP-conversion-specific Metro packaging
  issue; tracing what actually populated `ChipboxAppGraph$Impl`
  revealed every non-Settings/non-ChipboxAppUi feature VM was missing
  from the graph since the Metro M5d sweep. The aggregation chain
  was broken at the second `implementation` hop; the LibraryViewModel
  crash was the first one to surface because the user happened to
  tap a Library route. Fix lives in the Metro migration as Milestone 7
  (see `docs/metro-migration.md`) — `cbox/android/appui/api`
  promotes its `features.X.real` + `playerStatus.api` deps to `api()`,
  and three `SavedStateHandle`-injecting VMs (Games-For-Platform,
  Game-Detail, Artist-Detail) refactor to the metrox-viewmodel
  `@AssistedFactory` pattern. Re-verified on device: Library, the
  three detail screens, and every Browse-* tab resolve cleanly.

- **Slice 6 — prep for the ui/components KMP flip (5 of 7 sub-slices
  done).** Slice 6's stated goal — promote `ChipboxListEntry` to
  commonMain so SettingsRoute can render the same composable on both
  targets — has a deeper transitive closure than the three Android-only
  imports the prior roadmap entry called out (`LocalConfiguration`,
  `collectAsStateWithLifecycle`, `LocalTitleBarController`). The entry
  also calls `model.Content(...)` from `cbox/android/ui/components/api`,
  a 30-file Compose UI surface module loaded with `stringResource`,
  `@Preview`, `android.content.res.Configuration`, `sage.android.bitmaps`,
  `sage.android.perf`, `android.os.Build`, and Coil's `LocalContext`.
  Slice 6 is a multi-sub-slice arc through that closure:

  - **6a — delete `sage/android/bitmaps`.** VGLS spritesheet leftover;
    sole external reference was `SheetConstants.MIN_WIDTH/ASPECT_RATIO`
    in chipbox `LoadingItem` (inlined as `PAGE_MIN_WIDTH` /
    `PAGE_ASPECT_RATIO` module constants). `cbox/android/images/api`
    declared `api(libs.sage.android.bitmaps)` but no source imported
    a symbol from it — vestigial. Catalog entry dropped, settings
    include dropped, module dir removed.
  - **6b — `sage/android/perf` → `sage.kmp + sage.compose.kmp`.** Single
    `Logging.kt` file moved straight to commonMain. Severity ints
    (`android.util.Log.INFO/WARN/ERROR`) replaced with hardcoded 4/5/6
    matching Hatchet's existing severity convention. `BuildConfig.DEBUG`
    gate became a runtime `var isPerfMeasurementEnabled` defaulting to
    true (right for JVM dev); AGP 9's KotlinMultiplatformAndroidLibraryExtension
    doesn't expose `buildConfig` generation, so the Android-side release
    gate is restored at runtime via
    `ChipboxApplication.onCreate { isPerfMeasurementEnabled = BuildConfig.DEBUG }`.
  - **6c — `LocalChipboxStringProvider` + drop `stringResource` from
    composables.** `stringResource(id)` is Android-only and was paired
    with the androidMain `.id() -> R.string.*` extension. Pattern is
    now the same `StringProvider` indirection sage VMs already use —
    composables read it from a `staticCompositionLocalOf<StringProvider>`
    in cbox/common/strings/api (which gains the sage.compose.kmp plugin
    layer + hoists `ChipboxStringId` to commonMain). Four `@Composable
    @ReadOnlyComposable` helpers (`ChipboxStringId.text()`,
    `text(arg)`, `textInt(arg)`, `text(first, second)`) wrap
    `StringProvider.getString*`. 7 composables swapped (6 ui/components
    files + SearchBar.kt). Provider wiring: MainActivity wraps
    `setContent` with `CompositionLocalProvider(LocalChipboxStringProvider
    provides appGraph.stringProvider)` alongside the existing
    `LocalMetroViewModelFactory provides ...`; DesktopMain mirrors
    the same pattern off the JvmChipboxGraph.
  - **6d — Coil3 `LocalContext` → `LocalPlatformContext`.** CrossfadeImage's
    two `LocalContext.current` reads swap to Coil 3.4.0's multiplatform
    `coil3.compose.LocalPlatformContext`. Behavior unchanged on Android
    (the PlatformContext type-aliases to `android.content.Context`
    there). Remaining Android-only barriers in CrossfadeImage —
    `BitmapGenerator` for the preview-only FakeImage branch + the
    `@Preview` sample suite — are preview-only and land in 6f.
  - **6e — `cbox/android/ui/chrome/api` → `sage.kmp + sage.compose.kmp`.**
    All three files (TitleBarController, ChromeController, ScreenChrome)
    hoist straight to commonMain — pure Compose runtime over the
    sage commonMain TitleBarModel. Namespace stays
    `net.sigmabeta.chipbox.common.ui.chrome.api` via `androidLibrary { namespace }`.
    Removes one of ChipboxListEntry's three direct barriers.
  - **6f-pre — `sage/android/ui/icons` → `sage.kmp + sage.compose.kmp`
    (added mid-slice).** The 6f plan as written assumed the icons
    module was already KMP-able, but production composables in
    `cbox/android/ui/components/api` call `Icon.vector()`,
    `SageMaterialVectors.IcCrossOut24dp(...)`, `IcFavoriteFilled`, etc.
    — all defined in the still-android-only icons module. The IcXX24dp
    files were already pure Compose UI vector primitives (`ImageVector`
    + `path { … }`); the only Android-only dep was
    `androidx.compose.material:material-icons-extended:1.7.8`, swapped
    for `org.jetbrains.compose.material:material-icons-extended:1.7.3`
    (JetBrains stopped publishing after 1.7.3; the older ABI stays
    binary-compatible with composeMultiplatform 1.9 since these files
    only depend on Compose UI vector primitives). The package
    (`androidx.compose.material.icons.*`) is identical between the two
    artifacts, so no source changes were needed. Also dropped the
    module's `src/main/res` (a `colors.xml` + `dimens.xml` with no
    consumers across sage or chipbox).
  - **6f — `cbox/android/ui/components/api` → `sage.kmp +
    sage.compose.kmp`.** Landed in two commits:
    - **Part 1: file split.** The 34 mixed production+@Preview files
      were split one-per-component: production composable stays in
      `src/main/java` (auto-mapped to `jvmSharedMain` by the KMP
      convention), `@Preview` helpers move to a sibling
      `src/androidMain/kotlin/*Previews.kt`. Same split for the four
      `subs/` files (CrossfadeImage, ElevatedPill, Flasher,
      LabeledThingy, LoadingThingy, Rating, MenuActionIcon). Three
      preview-only helpers (ChipboxTheme, Fullscreen, PreviewActionSink)
      `git mv`'d to androidMain. Dropped the `android.os.Build`
      version-gated shadow in ElevatedCircle / ElevatedPill /
      ElevatedRoundRect / WideItem — always-shadow. `ComposableMapping`
      swapped `javaClass.simpleName` for `::class.simpleName`. Inlined
      `dimensionResource(R.dimen.min_clickable_size)` in MenuActionIcon
      and `R.dimen.margin_side` in LoadingThingy as private dp
      constants. Replaced `MathUtils.lerp` with an inline `fun lerp`.
      `LoadingItem`: `java.util.Random` → `kotlin.random.Random`.
      Dropped the FakeImage / BitmapGenerator branch + the
      `LocalInspectionMode`-gated `forceGenBitmap` param from
      CrossfadeImage. Deleted three dead-code files: `utils/ImageUtils.kt`
      (`previewPlaceholder` with no callers), `subs/MenuButton.kt` (no
      callers), and `src/test/.../LabeledThingyPaparazziTest.kt` (the
      module never applied the paparazzi plugin, so the test hadn't
      compiled in a long time). Plugin still on `sage.android` at this
      point — preview srcDir wired manually via
      `android.sourceSets.main.kotlin.srcDirs(...)` so Android still
      sees them. Android-green.
    - **Part 2: plugin flip + final dep cleanup.** Build file converted
      to `sage.kmp + sage.compose.kmp` with `kotlin { androidLibrary {
      namespace } sourceSets { commonMain { … } androidMain { … } } }`.
      `src/main/res` `git mv`'d to `src/androidMain/res`. commonMain
      deps: `sage.common.ui.components` (api), `sage.common.appcomm`,
      `sage.common.images`, `sage.android.perf`, `sage.android.ui.icons`
      (now KMP via 6f-pre), `cbox.common.strings.api`,
      `cbox.common.ui.fonts.api`, `coil.kt.core`, `coil.kt.compose`.
      androidMain deps: `cbox.android.ui.theme.api` (preview-only via
      ChipboxTheme), `androidx.compose.ui.tooling.preview`. Dropped
      vestigial: `ksp`, `kotlin.reflect`, `libs.material`,
      `projects.cbox.android.images.api`,
      `projects.cbox.android.strings.api`. Source changes the JVM
      compile forced: `SubsectionHeader` dropped
      `Font(DeviceFontFamilyName("sans-serif-condensed"))` (no CMP
      equivalent — no portable system-font discovery API) for
      `FontFamily.SansSerif`; four preview files
      (ActionItem, HorizontalScroller, NotifListItem, SectionListItem)
      + `Fullscreen.kt` inlined `dimensionResource(R.dimen.margin_side)`
      as `16.dp` because AGP 9's
      `com.android.kotlin.multiplatform.library` doesn't generate an
      `R` class for androidMain `res/` out of the box (worth re-checking
      when AGP catches up). Previews don't need the responsive
      sw600dp/w600dp/w800dp overrides anyway.
  - **6g — promote `ChipboxListEntry` to commonMain.** Three swaps as
    planned: `LocalConfiguration.current.screenWidthDp` →
    `BoxWithConstraints { maxWidth.value }`; `collectAsStateWithLifecycle`
    → `collectAsState`; `model.Content(...)` resolves to the
    now-commonMain extension after 6f. `git mv` from androidMain to
    commonMain. The module's `androidMain { dependencies { … } }` block
    fell out entirely — sage.android.ui.list / chrome / components /
    fonts deps moved into commonMain (sage.android.ui.list was already
    KMP, so the build-file comment claiming ChipboxListEntry was pinned
    to androidMain was stale and got rewritten). SettingsRoute is still
    androidMain; promoting it is part of roadmap item 8.

## Roadmap (not yet done)

1. **Compose Multiplatform UI port: finish the Settings port.**
   Milestones 6 + 7 set every prerequisite (toolchain, palette,
   typography, fonts, ViewModel scoping, navigation, sage commonMain
   types). Milestone 9 slice 6 is complete (`ChipboxListEntry` lives in
   commonMain — see slices 6a–6g above). Items 7 + 8 below also landed
   this milestone — Settings is now a real, functioning screen on both
   targets. **Compose Multiplatform UI port: Settings = done.**

   7. **Desktop folder picker** — landed. `SettingsScreen.kt` on the
      JVM target intercepts `ChipboxEvent.PickFolder` and pops a
      `javax.swing.JFileChooser` in `DIRECTORIES_ONLY` mode, sending
      the selected absolute path back as `SettingsAction.FolderPicked`
      (mirrors the Android SAF `OpenDocumentTree` path through the
      same shared `SettingsViewModel`). Inlined in the screen file
      rather than a separate `expect`/`actual` `LibraryLocationPicker`
      — only one consumer today; factor out when a second one shows up.
      Note: `LocalFileContentSource.addLibraryLocation` expects a
      filesystem path, not a `file://` URI, so the action's `uri`
      field is platform-bag-of-bytes (consistent with the Android side
      sending a SAF URI string).
   8. **Wire `SettingsScreen` into Voyager** — landed. JVM
      `data object SettingsScreen : Screen` pulls `SettingsViewModel`
      via `metroViewModel<>()` and hosts `ChipboxListEntry` inside a
      Material3 `Scaffold` (TopAppBar + SnackbarHost). Event handling:
      `NavigateBack` → `navigator.pop()`, `OpenUrl` →
      `java.awt.Desktop.browse(URI(url))`, `ShowSnackbar` →
      `SnackbarHostState`, `CopyToClipboard` →
      `Toolkit.getDefaultToolkit().systemClipboard`, `PickFolder` →
      JFileChooser (see item 7). Each screen instantiates its own
      `TitleBarController` via `CompositionLocalProvider` (Android wires
      one at the `ChipboxAppUi` root). Also added
      `kotlinx-coroutines-swing` to the JVM runtime — Compose Desktop
      runs on the AWT event queue and `viewModelScope.launch` posts to
      `Dispatchers.Main`, which needs the Swing dispatcher impl to be
      registered.

   `SettingsRoute` (the Android-side composable) still lives in
   androidMain — the SAF `rememberLauncherForActivityResult` keeps it
   Android-only. The two SettingsScreens (Android + JVM) host the same
   commonMain `SettingsViewModel` through the same commonMain
   `ChipboxListEntry`; only the platform glue (folder picker, snackbar
   host, URL/clipboard sinks) differs.

   Subsequent feature ports follow the same shape. Image loading
   already works cross-platform: `:images` is on `sage.kmp` and the
   feature `:images:api` pulls Coil 3 (`coil-kt-core`,
   `coil-kt-compose`, `coil-kt-okhttp`), so artwork renders on the
   JVM target without a wrapper.

   **Android nav backend swapped to Voyager.** AndroidX
   `navigation-compose` (`NavHost` + `composable<T>` typesafe routes)
   was the Android backend; the JVM target used Voyager because the
   AndroidX CMP fork publishes JVM stubs with no runtime. Android now
   uses Voyager too: outer `Navigator(ChipboxTabsScreen)` for
   full-screen overlays (`NowPlayingScreen` covers the tab UI without
   needing `ChromeController` to hide bars), inner `TabNavigator` for
   `Library`/`Search`/`Settings` tabs, and a per-tab `Navigator`
   inside each `Tab.Content()` so deep stacks survive tab switches
   (parity with the old `popUpTo(start) + saveState + restoreState`
   pattern). `ChipboxEvent`s reach screens through
   `LocalChipboxEventSink` (in `cbox/android/ui/chrome/api`), provided
   once at the appui level and rebound inside each tab so
   `NavigateTo` / `NavigateBack` push/pop the local tab Navigator.
   Mapping from event payload → Voyager `Screen` lives in `screenFor()`
   in `ChipboxScreens.kt`; the existing per-feature `XxxRoute`
   composables are unchanged behind anonymous `Screen` wrappers.

   Three parameterised VMs (`GameDetail`/`ArtistDetail`/
   `GamesForPlatform`) were rewritten from
   `@Assisted SavedStateHandle` + `savedStateHandle.toRoute<T>()`
   (an AndroidX-nav extension) to `ManualViewModelAssistedFactory`
   with typed `@Assisted` args. Routes now take the typed arg as a
   composable parameter and call
   `assistedMetroViewModel<VM, Factory> { create(arg) }`.

   Three Voyager gotchas surfaced during the swap (all fixed):

   - **`Screen.key` defaults to the class name.** A `data class
     GameDetailDeepScreen(val gameId: Long)` instance for game 248
     and another for game 96 collide on the same Voyager screen key,
     share the same `ViewModelStore`, and reuse the same VM (with
     its `@Assisted gameId` baked in from the first push). Every
     parameterised `Screen` must override `key` to embed the args —
     see `ChipboxScreens.kt:GameDetailDeepScreen` etc.
   - **Voyager 1.0.1 throws on pop+recompose.**
     `AndroidScreenLifecycleOwner` tries to transition `DESTROYED`
     → `STARTED` against androidx.lifecycle 2.10.x (e.g. ArtistDetail
     → back → GameDetail recompose), crashing. Pin Voyager at a
     1.1.0 beta (`1.1.0-beta03` at time of writing); bump to stable
     when it lands.
   - **Locals provided inside the tabs root unmount with it.**
     `LocalChipboxEventSink` and the shared `SnackbarHostState` must
     be provided in `ChipboxAppUi` *inside* the outer `Navigator { … }`
     but *outside* `SlideTransition`; otherwise pushing
     `NowPlayingScreen` onto the outer Navigator unmounts the tabs
     root (which provided them) and the pushed screen throws on
     `LocalChipboxEventSink.current`. Similarly, the TopAppBar back
     arrow lives at the tabs-root scope where `LocalChipboxEventSink`
     is the *outer* sink — it can't reach the active tab's inner
     Navigator. `LocalActiveTabNavigator` (a `Stable` holder updated
     via `DisposableEffect` inside each tab's `TabNavigatorContent`)
     bridges that, so the chrome back arrow pops the current tab's
     deep stack.

   **App shell shared across Android + JVM.** Following the Voyager
   swap, `cbox/android/appui/api` was promoted to `sage.kmp` and the
   Android `MainActivity` + JVM `DesktopMain.kt` now both call the
   same `ChipboxAppUi(onOpenUrl, onCopyToClipboard)` composable.
   `apps/jvm/.../{HomeScreen,LibraryScreen,SettingsScreen,AboutScreen,
   HelloViewModel}.kt` are deleted — the JVM target gets the full
   Library/Search/Settings tab UI for free instead of its bespoke
   buttons-only home screen.

   Mechanics:
   - `Intent.ACTION_VIEW` / `ClipboardManager` (Android-only) became
     `onOpenUrl(String)` / `onCopyToClipboard(label, text)` callbacks
     in `ChipboxAppUi`; `MainActivity` fills them with the Android
     impls, `DesktopMain.kt` with `Desktop.browse(URI(url))` +
     `Toolkit.getDefaultToolkit().systemClipboard`.
   - `LocalConfiguration.current.screenWidthDp` (Android-only)
     replaced by `BoxWithConstraints { maxWidth }` in `PlayerStatus`
     and `ChipboxTabsScreen` — works on every Compose target.
   - `androidx.compose.material3.adaptive.navigation-suite` (Android
     library only) swapped for
     `org.jetbrains.compose.material3:material3-adaptive-navigation-suite`
     (JetBrains-published CMP twin — same package names, published
     for JVM/desktop too).
   - `androidx.lifecycle.compose.collectAsStateWithLifecycle` (Android
     only) swapped for `androidx.compose.runtime.collectAsState` in
     `PlayerStatus` + `ChipboxFreeformEntry` + `SearchRoute`.
   - 10 feature modules promoted to `sage.kmp`: every `:api`
     (artist-detail, game-detail, games-for-platform, now-playing,
     search), every `:real` that ships VMs reachable from `appui`
     (browse-all-tracks, browse-by-artist, browse-by-game,
     browse-by-platform, artist-detail, game-detail, games-for-platform,
     now-playing, search), plus `player-status` and `ui/freeform`.
     The conversions are mostly plugin swaps; two had small platform-
     conditional bits to remove (`@Preview(uiMode = Configuration
     .UI_MODE_NIGHT_YES)` in NowPlayingContent's preview functions —
     deleted; `Build.VERSION.SDK_INT >= Q` gate in SearchBar — minSdk
     is past Q, gate dropped).
   - `SettingsRoute` became `expect`/`actual`: androidMain keeps the
     SAF `OpenDocumentTree` launcher; jvmMain uses
     `javax.swing.JFileChooser` (the same JFileChooser plumbing the
     deleted `apps/jvm/SettingsScreen.kt` had — moved into the
     feature module so the shared `screenFor()` in `appui` resolves
     to the right platform impl). `LibraryRoute` moved fully to
     commonMain — no platform-specific bits.
   - `:player-status` converted (no `android.*` imports in any of
     its sources today — pure Compose).
   - JVM `JvmChipboxGraph` now `@Binds` `RealGenerator → Generator`
     and `SourceDataLineSpeaker → Speaker` so `DirectorModule`'s
     `Director = RealDirector(generator, speaker, …)` resolves for
     the feature VMs that now reach the JVM target through `appui`.
     `FileSpeaker` stays available as a concrete accessor for the
     headless CLI `play` mode (WAV output); only the desktop UI uses
     the live speaker. See roadmap item 2 below for the real-time
     audio details.

   The chrome (TopAppBar + NavigationSuiteScaffold + PlayerStatus
   overlay + TabNavigator + per-tab Navigator + system event sink)
   lives in commonMain. Both targets see identical Library/Search/
   Settings tabs, identical back-stack semantics, and identical
   NowPlaying overlay shape.

2. **Real-time JVM audio (done — `SourceDataLineSpeaker`).** A
   `javax.sound.sampled.SourceDataLine`-backed [Speaker] sibling of
   Android's `AudioTrack`-based `RealSpeaker`. Lives in
   `apps/jvm/.../SourceDataLineSpeaker.kt` (single consumer, no module
   shuffle yet); same lifecycle contract as the base — re-opens the
   line on sample-rate changes (setlist emulators differ),
   `SourceDataLine.flush()` on seek, `stop()`/`start()` on pause/
   resume, `drain()+stop()+close()` on teardown. Wired through
   `JvmChipboxGraph.@Binds SourceDataLineSpeaker → Speaker`;
   `JvmSpeakerModule.provideLiveSpeaker(…)` is the only `@Provides`
   added — `FileSpeaker` stays for the headless CLI `play` mode that
   writes WAVs.

   The cold-cache `GeneratorEvent.Error` wasn't addressed in this
   slice, but follow-up work (`Generator.fillBuffer()` +
   interruptible writer loop) made it rare in practice — see Known
   Issue #2.

   **JVM-side CMake wiring as a side-effect.** Android's
   `externalNativeBuild { cmake { … } }` (per `:native` module) builds
   the seven emulator `.so`s into the APK on `:assembleDebug`. The
   JVM target previously had no analog — the host-build was a
   one-time shell-script step from `apps/jvm/README.md`. That's
   addressed: `apps/jvm/build.gradle.kts` now registers
   `nativeHostShim` + `nativeEmulator<Target>` Exec tasks (one per
   emulator) and an aggregate `nativeLibs`. `:apps:jvm:run`
   `dependsOn(nativeLibs)`, so launching `gui` mode just-in-time
   builds anything missing. The CMake invocation matches the README
   script flag-for-flag (same hostshim, same `-D__fastcall=` /
   `-D__cdecl=` neutralization, same JNI include path).

   Configuration-cache compat: the source-writing for the hostshim
   (`android/log.h` + `log.c`) happens at Gradle configuration time
   rather than inside a `doLast` lambda; the lambda would have
   captured build-script references the configuration cache rejects.
   `gcc → log.o` and `ar → liblog.a` stay as plain `Exec` tasks (no
   lambdas, no captures). JDK with JNI headers is auto-detected:
   override via `-Pchipbox.jvm.nativeJdk=/path/to/jdk`. cmake path:
   `-Pchipbox.jvm.cmake`. Linux-only host build for now; macOS /
   Windows are roadmap item 3.

3. **Cross-platform native packaging.** All seven emulators wired and
   playback-verified on host x86-64 Linux; macOS/Windows `.dylib`/`.dll`
   builds remain. The distribution side is done: `:apps:jvm:installDist`
   / `distZip` / `distTar` produce a runnable layout with the start
   scripts (`bin/chipbox`, `bin/chipbox.bat`) wired to load the bundled
   native libs from `lib/native/` next to the per-module jars in `lib/`.
   Two enablers landed:

   - **Path-derived `archiveBaseName`.** Every module previously emitted
     a `real.jar` / `api.jar`, which collided in the flat `lib/`. The
     SAGE convention plugins (`sage.kmp`, `sage.jvm`) now set
     `archiveBaseName` from `project.path` on every `Jar` task — e.g.
     `:cbox:common:appui:api` → `cbox-common-appui-api-jvm.jar` —
     so all 158 jars on the classpath have unique names. Helper is
     `Project.configureUniqueArchiveBaseName()` in
     `sage-build-logic/.../components/ProjectExtensions.kt`.
   - **Start-script injection of `java.library.path`.** The
     `application` plugin's `applicationDefaultJvmArgs` doesn't reliably
     reach the JVM with `$APP_HOME` interpolation (Gradle's unix template
     escapes `$` through an xargs+sed pipeline before the shell ever sees
     it). Instead the `startScripts` task is post-processed to splice
     `-Djava.library.path=$APP_HOME/lib/native` into `exec "$JAVACMD"`
     directly (unix) and `-D"java.library.path=%APP_HOME%\lib\native"`
     into the `endlocal & "%JAVA_EXE%"` line (bat). The natives are
     bundled into `lib/native/` via the `distributions { main { contents
     { from(nativeLibsDirFile) { into("lib/native") } } } }` block.

   The old `:apps:jvm:standaloneScript` workaround (an out-of-tree
   `build/run-standalone.sh` with absolute jar paths in the classpath)
   is gone.
4. **Rest of the `jvmSharedMain` → `commonMain` hoist** (done — see
   Milestone 8). The remaining genuinely JVM-family-only code is now
   the four files surfaced by the strict-import audit: `perf/ScreenLoadStatus`
   (refactored — no longer JVM-only — see Milestone 8),
   `connectivity/HttpException` + `NetworkUnavailableException`
   (`java.io.IOException` super), `ui/strings/StringGenerator`
   (`java.util.Locale`/`Random`), and all of `common/time` (ThreeTenABP).

## Known issues / out of scope

