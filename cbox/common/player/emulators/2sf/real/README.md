# `:cbox:common:player:emulators:2sf:real`

> The `Emulator` backend for 2SF — Nintendo DS sound-program dumps.

The `:real` module: a JNI wrapper that plays the 2SF family (`.2sf`, `.mini2sf`)
by driving a native Nintendo DS sound emulator. It's a thin `Emulator` subclass;
the audio comes from the native core packaged by the matching `:native`
companion.

## Contents

| File | What it is |
| --- | --- |
| `TwosfEmulator.kt` | `object TwosfEmulator : Emulator()`. Loads `libtwosf` via `System.loadLibrary("twosf")`; declares extensions `2sf` / `mini2sf`; the load/generate/teardown/error/sample-rate methods are `external` JNI calls. |

## Why depend on this module

Depend on `:2sf:real` to add 2SF playback to the `EmulatorProvider`. The
Android `.so` is built and packaged by the CMake companion
`:cbox:android:player:emulators:2sf:native` (referenced as a `runtimeOnly`
androidMain dependency by the `chipbox.emulator.real` plugin); the JVM target
host-builds the same native tree. Depend on `:api` for the `Emulator` type.

## Using it

```kotlin
TwosfEmulator.loadNativeLib()       // System.loadLibrary("twosf")
TwosfEmulator.loadTrack(track)      // track.path ends in .2sf / .mini2sf
val frames = TwosfEmulator.generateBuffer(buffer)
```

## Module facts

- **Plugin:** `chipbox.emulator.real` (applies `sage.kmp`)
- **Targets:** Android + JVM
- **Source set:** `src/main/java` (`jvmSharedMain`)
- **SAGE/module dependencies:** via the plugin — `:cbox:common:player:common:api`, `:cbox:common:player:emulators:api`, `:cbox:common:repository:api`; androidMain `runtimeOnly` `:cbox:android:player:emulators:2sf:native`
