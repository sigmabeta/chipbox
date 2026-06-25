# `:cbox:common:player:emulators:gme:real`

> The `Emulator` backend for Game Music Emu — multi-system chiptune formats (NSF, SPC, GBS).

The `:real` module: a JNI wrapper around the Game Music Emu (GME) library, which
emulates several sound chips. Here it handles `gbs` (Game Boy), `nsf`/`nsfe`
(NES), and `spc` (SNES). It's a thin `Emulator` subclass; the audio comes from
the native core packaged by the matching `:native` companion.

## Contents

| File | What it is |
| --- | --- |
| `GmeEmulator.kt` | `object GmeEmulator : Emulator()`. Loads `libgme` via `System.loadLibrary("gme")`; declares extensions `gbs` / `nsf` / `nsfe` / `spc`. These are multi-track formats, so it overrides `setTrackNumber` and passes the sub-track index into `loadTrackInternalWithNumber(path, trackNumber)`; generate/teardown/error/sample-rate are `external` JNI calls. |

## Why depend on this module

Depend on `:gme:real` to add NSF/SPC/GBS playback to the `EmulatorProvider`. The
Android `.so` is built and packaged by the CMake companion
`:cbox:android:player:emulators:gme:native` (referenced as a `runtimeOnly`
androidMain dependency by the `chipbox.emulator.real` plugin); the JVM target
host-builds the same native tree. Depend on `:api` for the `Emulator` type.

## Using it

```kotlin
GmeEmulator.loadNativeLib()        // System.loadLibrary("gme")
GmeEmulator.loadTrack(track)       // setTrackNumber(track.trackNumber) selects the sub-song
val frames = GmeEmulator.generateBuffer(buffer)
```

## Module facts

- **Plugin:** `chipbox.emulator.real` (applies `sage.kmp`)
- **Targets:** Android + JVM
- **Source set:** `src/main/java` (`jvmSharedMain`)
- **SAGE/module dependencies:** via the plugin — `:cbox:common:player:common:api`, `:cbox:common:player:emulators:api`, `:cbox:common:repository:api`; androidMain `runtimeOnly` `:cbox:android:player:emulators:gme:native`
