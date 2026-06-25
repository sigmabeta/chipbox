# `:cbox:common:player:emulators:usf:real`

> The `Emulator` backend for USF — Nintendo 64 sound-program dumps.

The `:real` module: a JNI wrapper that plays the USF family (`.usf`, `.miniusf`)
by driving a native Nintendo 64 sound emulator. It's a thin `Emulator` subclass;
the audio comes from the native core packaged by the matching `:native`
companion.

## Contents

| File | What it is |
| --- | --- |
| `UsfEmulator.kt` | `object UsfEmulator : Emulator()`. Loads `libusf` via `System.loadLibrary("usf")`; declares extensions `usf` / `miniusf`. `loadTrackInternal` logs then delegates to `external loadTrackInternalNative(path)`; generate/teardown/error/sample-rate are `external` JNI calls. |

## Why depend on this module

Depend on `:usf:real` to add USF playback to the `EmulatorProvider`. The Android
`.so` is built and packaged by the CMake companion
`:cbox:android:player:emulators:usf:native` (referenced as a `runtimeOnly`
androidMain dependency by the `chipbox.emulator.real` plugin); the JVM target
host-builds the same native tree. Depend on `:api` for the `Emulator` type.

## Using it

```kotlin
UsfEmulator.loadNativeLib()        // System.loadLibrary("usf")
UsfEmulator.loadTrack(track)       // track.path ends in .usf / .miniusf
val frames = UsfEmulator.generateBuffer(buffer)
```

## Module facts

- **Plugin:** `chipbox.emulator.real` (applies `sage.kmp`)
- **Targets:** Android + JVM
- **Source set:** `src/main/java` (`jvmSharedMain`)
- **SAGE/module dependencies:** via the plugin — `:cbox:common:player:common:api`, `:cbox:common:player:emulators:api`, `:cbox:common:repository:api`; androidMain `runtimeOnly` `:cbox:android:player:emulators:usf:native`
