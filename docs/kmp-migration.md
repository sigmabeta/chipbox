# Kotlin Multiplatform migration — plan & status

Status: **Milestone 1 implemented.** JVM is the second target.
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
- **Headless JVM target**: new `:cbox:jvm:app` (`sage.jvm` +
  `application`). `Main.kt` wires `MemoryRepository` → `FakeEmulator` /
  `FakeGenerator` → `RealBufferManager` → `FileSpeaker`, mirroring the
  Director's start ordering (the speaker's consume loop must start only
  after the generator's first `Emitting` event — starting it earlier
  parks the consumer on a pre-`setSampleRate` channel the buffer manager
  swaps out).

### Verification

- `:cbox:common:player:common:api:build` — both KMP variants build.
- `:app:assembleDebug` — Android build still green against the KMP module.
- `:cbox:jvm:app:run` — exits 0; produces a valid WAV (RIFF/WAVE,
  stereo, 16-bit, 44100 Hz, ~3 s, audible).
- detekt clean on new/changed modules.

## Roadmap (not yet done)

1. **Real-time JVM audio.** An `expect`/`actual` (or factory) audio sink:
   Android `AudioTrack` vs a JVM `javax.sound.sampled.SourceDataLine`
   speaker, so the JVM target plays live instead of only writing WAV.
2. **Mass module conversion.** Move the rest of `cbox/common/player/*`
   and other shared modules onto `sage.kmp`; then progressively hoist
   pure-Kotlin code from `jvmSharedMain` into `commonMain`.
3. **Native emulators on desktop.** Cross-build the emulator `.so`s for
   Linux/macOS/Windows and a JVM `System.loadLibrary` path, for real
   PSF/GME/etc. playback off-Android (today the JVM path is
   `FakeEmulator` only).
4. **DI / content sources.** Replace Hilt with JVM-friendly wiring (or
   manual factories) and provide a real file-based `ContentSource` and
   repository for the JVM target beyond the in-memory shim.

## Known issues / out of scope

- `FileSpeaker` writes a WAV `data`-size header field that some parsers
  read as a longer duration than the actual PCM payload (the audio bytes
  are correct; only the header arithmetic is off). Pre-existing, not
  introduced by this work; header-trusting players could mis-seek.
