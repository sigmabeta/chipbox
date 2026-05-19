# Kotlin Multiplatform migration — plan & status

Status: **Milestone 2 implemented.** JVM is the second target, now with a
real native emulator (GME) decoding end-to-end.
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

GME (`.spc/.nsf/.nsfe/.gbs`) now decodes end-to-end on the JVM target —
the `FakeEmulator`-only constraint of Milestone 1 is lifted for GME.

- **Native code is platform-neutral now.** All emulator C/C++ trees plus
  the shared `native-common` moved out of `cbox/android` into top-level
  `cbox/native/` (now built by two apps). The Android `real` modules point
  `externalNativeBuild` at `rootProject.file("cbox/native/<emu>/...")`.
- **Host-built native lib.** `libgme.so` is cross-built for the host from
  the same `cbox/native/gme` CMake tree the NDK uses (out-of-tree, JNI
  includes injected via `CMAKE_*_FLAGS`). Only portability change: a
  `#include <cstdint>` in `Gme.h` (NDK headers leaked it; host GCC is
  stricter — safe for the Android build too). See `apps/jvm/README.md`.
- **JVM emulator module.** New `:cbox:jvm:player:emulators:gme:real`
  (`sage.jvm`) carries a byte-identical `GmeEmulator` twin — the JNI
  symbols bind to that exact FQCN, so the Android and JVM wrappers must
  match. The Android module is unchanged.
- **Context-free real generator.** New
  `:cbox:jvm:player:generator:real` (`sage.jvm`): the Android
  `RealGenerator`'s only Android tie was `Context.cacheDir`; the actual
  work already lived in the pure-JVM `:cbox:common:player:cache:real`, so
  the staging / PCM-cache dirs are now plain `File` params.
- **File content source + repository.** `apps/jvm` gets a
  `FileContentSource` (path-is-the-identifier) and a one-track
  `SingleTrackRepository` (avoids `MemoryRepository`, which drops
  `RawTrack.source`/`extension` — the fields the real path needs).
- Verified: a 10 s (+2 s fade) Chrono Trigger SPC → 32 kHz stereo 16-bit
  WAV, mean ≈ −17 dB (audible, not silence).

### Milestone 2b — all emulators wired

All seven native emulators now have a `:cbox:jvm:player:emulators:<emu>:real`
twin module and are wired into `apps/jvm` (`ALL_EMULATORS`; the generator
picks by extension). `Main.kt` stages `*lib` siblings so mini-formats
resolve their `_lib` chain. Host portability needed only build-flag shims
(no Android-build impact): `-D__fastcall=`/`__cdecl=`/`__stdcall=` for
MSVC/x86 keywords, and an `android/log.h` + `liblog.a` shim for psf's
debug probes. Per-emulator status (host x86-64; see `apps/jvm/README.md`):

All seven are **playback-verified** on host x86-64 (real, audible WAV):
GME (SPC), PSF (.psf + .minipsf), VGM (.vgz), USF (.miniusf), 2SF
(.mini2sf), GBA/mgba (.minigsf), SSF + DSF (.ssf/.dsf w/ chain). The
heavy CPU cores (USF/2SF/GBA/SSF/DSF) decode slower than real time on a
cold cache — a non-fatal render-ahead "fell behind" is logged but the
full track still renders (second run is cache-served).

## Roadmap (not yet done)

1. **Real-time JVM audio.** An `expect`/`actual` (or factory) audio sink:
   Android `AudioTrack` vs a JVM `javax.sound.sampled.SourceDataLine`
   speaker, so the JVM target plays live instead of only writing WAV.
2. **Mass module conversion.** Move the rest of `cbox/common/player/*`
   and other shared modules onto `sage.kmp`; then progressively hoist
   pure-Kotlin code from `jvmSharedMain` into `commonMain`.
3. **More native emulators on desktop.** All seven wired and
   playback-verified (Milestone 2b); remaining: macOS/Windows
   `.so`/`.dylib`/`.dll` builds + a packaged `java.library.path`, and
   tuning the render-ahead window for the heavy cold-cache cores.
4. **DI / content sources.** Replace Hilt with JVM-friendly wiring (or
   manual factories) and a richer file-based repository for the JVM
   target beyond the single-track shim.

## Known issues / out of scope

- `FileSpeaker` writes a WAV `data`-size header field that some parsers
  read as a longer duration than the actual PCM payload (the audio bytes
  are correct; only the header arithmetic is off). Pre-existing, not
  introduced by this work; header-trusting players could mis-seek.
