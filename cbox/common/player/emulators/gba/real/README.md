# `:cbox:common:player:emulators:gba:real`

> The `Emulator` backend for GSF — Game Boy Advance sound-program dumps.

The `:real` module: a JNI wrapper that plays the GSF family (`.gsf`, `.minigsf`)
by driving a native Game Boy Advance (mGBA) sound emulator. It's a thin
`Emulator` subclass; the audio comes from the native core packaged by the
matching `:native` companion.

## Contents

| File | What it is |
| --- | --- |
| `GbaEmulator.kt` | `object GbaEmulator : Emulator()`. Loads `libgba` via `System.loadLibrary("gba")`; declares extensions `gsf` / `minigsf`; the load/generate/teardown/error/sample-rate methods are `external` JNI calls. |

## Why depend on this module

Depend on `:gba:real` to add GSF playback to the `EmulatorProvider`. The Android
`.so` is built and packaged by the CMake companion
`:cbox:android:player:emulators:gba:native` (referenced as a `runtimeOnly`
androidMain dependency by the `chipbox.emulator.real` plugin); the JVM target
host-builds the same native tree. Depend on `:api` for the `Emulator` type.

> This core is the one whose NULL-`m_core` SIGSEGV motivated the `hasLoadedTrack`
> guard in `Emulator.generateBuffer` (see `:api`'s `EmulatorLoadGuardTest`).

## Using it

```kotlin
GbaEmulator.loadNativeLib()        // System.loadLibrary("gba")
GbaEmulator.loadTrack(track)       // track.path ends in .gsf / .minigsf
val frames = GbaEmulator.generateBuffer(buffer)
```

## Module facts

- **Plugin:** `chipbox.emulator.real` (applies `sage.kmp`)
- **Targets:** Android + JVM
- **Source set:** `src/main/java` (`jvmSharedMain`)
- **SAGE/module dependencies:** via the plugin — `:cbox:common:player:common:api`, `:cbox:common:player:emulators:api`, `:cbox:common:repository:api`; androidMain `runtimeOnly` `:cbox:android:player:emulators:gba:native`
