# Kotlin Multiplatform migration — plan & status

Status: **Milestone 3 implemented.** JVM is the second target. All seven
native emulators decode end-to-end on it (playback-verified on host
x86-64), the headless app also runs without Gradle via a generated
launcher, and the mass `sage.android`/`sage.jvm` → `sage.kmp` conversion
has collapsed every module whose dependency closure is pure — **61
conversions in all** (33 `sage.jvm` shared modules, 20 `sage.android`
pure modules, plus the 7 emulator wrappers and the generator that
previously had cbox/jvm/* duplicates) — onto single KMP modules serving
both variants. `cbox/jvm/` is now empty (deleted); the remaining
single-target modules are genuinely Android-only and blocked on item 4.
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
references the same FQCNs, now resolved by the KMP modules' jvm variant.) Host portability needed only build-flag shims
(no Android-build impact): `-D__fastcall=`/`__cdecl=`/`__stdcall=` for
MSVC/x86 keywords, and an `android/log.h` + `liblog.a` shim for psf's
debug probes.

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
- **What stayed `sage.android`** (blocked on item 4): the Room/SAF chain
  (`database/all`, `repository/real|all`, `scanner/real|fake|all`,
  `contentsource/file/real|all`), every `:di` module (Hilt), the resource-
  touching `strings/api` and `ui/fonts/api` (`R.string.*` / `R.font.*`
  references — not visible as `import android.*`), and the real
  Android-only feature modules under `cbox/android/{appui,images,
  artworkprovider,player-status,player/speaker/real,ui/*}`. **Item 2 and
  item 4 are coupled:** the remaining KMP conversions need that storage
  layer (and Hilt) abstracted first.

A small inspectable Python helper (`scripts/kmpify.py`) drove the bulk
rewrite — kept around for the next pass.

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

## Roadmap (not yet done)

1. **Real-time JVM audio.** An audio sink (probably a factory, possibly
   `expect`/`actual`): Android `AudioTrack` vs a JVM
   `javax.sound.sampled.SourceDataLine` speaker, so the JVM target plays
   live instead of only writing WAV. Tune the render-ahead window at the
   same time — the heavy cores currently emit a terminal
   `GeneratorEvent.Error` on a cold cache, which the WAV harness
   survives but live playback would not.
2. **Finish the KMP conversion.** Blocked on item 4. Once Room/SAF/Hilt
   are abstracted, the rest of `cbox/android/*` collapses onto
   `sage.kmp` via the same `kmpify.py` pass. The progressive
   `jvmSharedMain` → `commonMain` hoist (the second half of the original
   item 2) is the deliberately-incremental remainder.
3. **Cross-platform native packaging.** All seven emulators wired and
   playback-verified on host x86-64 Linux. Remaining: macOS/Windows
   `.dylib`/`.dll` builds + a packaged `java.library.path`, and a real
   distribution (today blocked by the duplicate jar-basename
   `installDist` issue — see Known issues).
4. **DI / content sources / repository.** Replace Hilt with a
   JVM-friendly wiring (manual factories or another container), abstract
   Room behind a multiplatform repository interface, and abstract SAF
   behind the existing `ContentSource` so the JVM target gets a real
   file-scanning library instead of the `SingleTrackRepository` shim.
   This is the keystone that unblocks the rest of item 2.

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
- The JVM target's repository is the one-track `SingleTrackRepository`
  shim; there is no library/scan/persistence on JVM yet (roadmap item 4).
- Heavy emulator cores emit a terminal render-ahead `GeneratorEvent.Error`
  on a cold cache (see Milestone 2b) — survivable for the WAV harness,
  not for live playback until the render-ahead window is tuned.
