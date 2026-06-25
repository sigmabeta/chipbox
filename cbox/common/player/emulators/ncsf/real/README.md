# `:cbox:common:player:emulators:ncsf:real`

> The `Emulator` backend for NCSF — Nintendo DS (SDAT) sound-program dumps.

The `:real` module: a JNI wrapper that plays the NCSF family (`.ncsf`,
`.minincsf`) by driving a native Nintendo DS SDAT sound emulator. It's a thin
`Emulator` subclass; the audio comes from the native core packaged by the
matching `:native` companion.

## Contents

| File | What it is |
| --- | --- |
| `NcsfEmulator.kt` | `object NcsfEmulator : Emulator()`. Loads `libncsf` via `System.loadLibrary("ncsf")`; declares extensions `ncsf` / `minincsf`; the load/generate/teardown/error/sample-rate methods are `external` JNI calls. |

## Why depend on this module

Depend on `:ncsf:real` to add NCSF playback to the `EmulatorProvider`. The
Android `.so` is built and packaged by the CMake companion
`:cbox:android:player:emulators:ncsf:native` (referenced as a `runtimeOnly`
androidMain dependency by the `chipbox.emulator.real` plugin); the JVM target
host-builds the same native tree. Depend on `:api` for the `Emulator` type.

## Using it

```kotlin
NcsfEmulator.loadNativeLib()       // System.loadLibrary("ncsf")
NcsfEmulator.loadTrack(track)      // track.path ends in .ncsf / .minincsf
val frames = NcsfEmulator.generateBuffer(buffer)
```

## Module facts

- **Plugin:** `chipbox.emulator.real` (applies `sage.kmp`)
- **Targets:** Android + JVM
- **Source set:** `src/main/java` (`jvmSharedMain`)
- **SAGE/module dependencies:** via the plugin — `:cbox:common:player:common:api`, `:cbox:common:player:emulators:api`, `:cbox:common:repository:api`; androidMain `runtimeOnly` `:cbox:android:player:emulators:ncsf:native`
