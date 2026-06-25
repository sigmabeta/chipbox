# `:cbox:common:player:emulators:psf:real`

> The `Emulator` backend for PSF/PSF2 — PlayStation 1 & 2 sound-program dumps.

The `:real` module: a JNI wrapper that plays the PSF family (`.psf`, `.minipsf`,
`.psf2`, `.minipsf2`) by driving a native PlayStation sound emulator. It's a thin
`Emulator` subclass; the audio comes from the native core packaged by the
matching `:native` companion.

## Contents

| File | What it is |
| --- | --- |
| `PsfEmulator.kt` | `object PsfEmulator : Emulator()`. Loads `libslopsf` via `System.loadLibrary("slopsf")`; declares extensions `psf` / `minipsf` / `psf2` / `minipsf2`. Overrides `getDiagnostics` (`external`) to surface non-fatal IOP HLE warnings from PSF2; load/generate/teardown/error/sample-rate are `external` JNI calls. |

## Why depend on this module

Depend on `:psf:real` to add PSF/PSF2 playback to the `EmulatorProvider`. The
Android `.so` is built and packaged by the CMake companion
`:cbox:android:player:emulators:psf:native` (referenced as a `runtimeOnly`
androidMain dependency by the `chipbox.emulator.real` plugin); the JVM target
host-builds the same native tree. Depend on `:api` for the `Emulator` type.

## Using it

```kotlin
PsfEmulator.loadNativeLib()        // System.loadLibrary("slopsf")
PsfEmulator.loadTrack(track)       // track.path ends in .psf / .minipsf / .psf2 / .minipsf2
val frames = PsfEmulator.generateBuffer(buffer)
val warnings = PsfEmulator.getDiagnostics()   // e.g. PSF2 IOP HLE warnings, or null
```

## Module facts

- **Plugin:** `chipbox.emulator.real` (applies `sage.kmp`)
- **Targets:** Android + JVM
- **Source set:** `src/main/java` (`jvmSharedMain`)
- **SAGE/module dependencies:** via the plugin — `:cbox:common:player:common:api`, `:cbox:common:player:emulators:api`, `:cbox:common:repository:api`; androidMain `runtimeOnly` `:cbox:android:player:emulators:psf:native`
