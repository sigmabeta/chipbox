# `:cbox:common:player:emulators:ssf:real`

> The `Emulator` backend for SSF/DSF — Sega Saturn & Dreamcast sound-program dumps.

The `:real` module: a JNI wrapper that plays the SSF/DSF families (`.ssf`,
`.minissf`, `.dsf`, `.minidsf`) by driving a native Sega Saturn / Dreamcast sound
emulator. It's a thin `Emulator` subclass; the audio comes from the native core
packaged by the matching `:native` companion.

## Contents

| File | What it is |
| --- | --- |
| `SsfEmulator.kt` | `object SsfEmulator : Emulator()`. Loads `libssf` via `System.loadLibrary("ssf")`; declares extensions `ssf` / `minissf` / `dsf` / `minidsf`; the load/generate/teardown/error/sample-rate methods are `external` JNI calls. |

## Why depend on this module

Depend on `:ssf:real` to add SSF/DSF playback to the `EmulatorProvider`. The
Android `.so` is built and packaged by the CMake companion
`:cbox:android:player:emulators:ssf:native` (referenced as a `runtimeOnly`
androidMain dependency by the `chipbox.emulator.real` plugin); the JVM target
host-builds the same native tree. Depend on `:api` for the `Emulator` type.

## Using it

```kotlin
SsfEmulator.loadNativeLib()        // System.loadLibrary("ssf")
SsfEmulator.loadTrack(track)       // track.path ends in .ssf / .minissf / .dsf / .minidsf
val frames = SsfEmulator.generateBuffer(buffer)
```

## Module facts

- **Plugin:** `chipbox.emulator.real` (applies `sage.kmp`)
- **Targets:** Android + JVM
- **Source set:** `src/main/java` (`jvmSharedMain`)
- **SAGE/module dependencies:** via the plugin — `:cbox:common:player:common:api`, `:cbox:common:player:emulators:api`, `:cbox:common:repository:api`; androidMain `runtimeOnly` `:cbox:android:player:emulators:ssf:native`
