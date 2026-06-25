# `:cbox:common:player:emulators:vgm:real`

> The `Emulator` backend for VGM — logged register writes to various FM/PSG sound chips.

The `:real` module: a JNI wrapper that plays the VGM family (`.vgm`, `.vgz`) by
driving a native VGM player, which re-emulates the many sound chips a VGM log can
target (YM2612, SN76489, etc.). It's a thin `Emulator` subclass; the audio comes
from the native core packaged by the matching `:native` companion.

## Contents

| File | What it is |
| --- | --- |
| `VgmEmulator.kt` | `object VgmEmulator : Emulator()`. Loads `libvgm` via `System.loadLibrary("vgm")`; declares extensions `vgm` / `vgz`. `loadTrackInternal` logs then delegates to `external loadTrackInternalNative(path)`; generate/teardown/error/sample-rate are `external` JNI calls. |

## Why depend on this module

Depend on `:vgm:real` to add VGM playback to the `EmulatorProvider`. The Android
`.so` is built and packaged by the CMake companion
`:cbox:android:player:emulators:vgm:native` (referenced as a `runtimeOnly`
androidMain dependency by the `chipbox.emulator.real` plugin); the JVM target
host-builds the same native tree. Depend on `:api` for the `Emulator` type.

## Using it

```kotlin
VgmEmulator.loadNativeLib()        // System.loadLibrary("vgm")
VgmEmulator.loadTrack(track)       // track.path ends in .vgm / .vgz
val frames = VgmEmulator.generateBuffer(buffer)
```

## Module facts

- **Plugin:** `chipbox.emulator.real` (applies `sage.kmp`)
- **Targets:** Android + JVM
- **Source set:** `src/main/java` (`jvmSharedMain`)
- **SAGE/module dependencies:** via the plugin — `:cbox:common:player:common:api`, `:cbox:common:player:emulators:api`, `:cbox:common:repository:api`; androidMain `runtimeOnly` `:cbox:android:player:emulators:vgm:native`
