# `:cbox:android:player:emulators:ncsf:native`

> Android CMake/JNI companion that builds `libncsf.so` — the NCSF (Nintendo DS SDAT) emulator core.

This is a `:native` module: an Android-only CMake/JNI companion. It compiles the
native sound-chip core for the NCSF format (`ncsf` / `minincsf`) into a shared
library (`libncsf.so`) and exposes it to the JVM via JNI. It has **no Kotlin
public API** — the matching `:cbox:common:player:emulators:ncsf:real` module
declares the `external` methods and calls `System.loadLibrary("ncsf")` to load
this `.so` at runtime.

## Contents

This Gradle module is just a `build.gradle.kts` applying the
`chipbox.emulator.native` convention plugin. The C/C++ sources it compiles live
at the repo root under `cbox/native/ncsf/` (shared by the Android, JVM-host, and
Emscripten/WASM builds):

| File / dir | What it is |
| --- | --- |
| `ncsf.cpp` / `ncsf.h` | Chipbox load/generate/teardown wrapper plus the PSF→SDAT glue. |
| `kotlin-jni.cpp` | JNI bridge mapping `…ncsf.NcsfEmulator` natives onto the wrapper. |
| `Core/sseqplayer/…` | The vendored **SSEQ Player** (a software Nitro Composer SDAT synthesizer, by Naram Qashat / CyberBotX). |
| `CMakeLists.txt` | Builds the `ncsf` target; links the shared `native-common` (psflib + PSF file I/O, transitively zlib). |
| `Ncsf_Web.cpp` | Emscripten/WASM entry point (used by the JS build, not Android). |

## Why depend on this module

The Android app variant depends on this module so the NCSF `.so` is packaged into
the APK's `jniLibs`. You never call it directly: depend on
`:cbox:common:player:emulators:ncsf:real` for the `Emulator` implementation; that
module loads the library this one produces. The convention plugin builds one
`.so` per ABI (`arm64-v8a`, `x86_64`) via a cacheable NDK task and feeds them in
as generated `jniLibs`.

## Using it

There is no Kotlin call surface here. The consumer side looks like:

```kotlin
// in :cbox:common:player:emulators:ncsf:real
class NcsfEmulator : Emulator {
    companion object {
        init { System.loadLibrary("ncsf") } // ← libncsf.so from this module
    }
    external fun loadTrackInternal(filename: String)
    // …
}
```

## Module facts

- **Plugin:** `chipbox.emulator.native` (applies `sage.android`, wires the NDK/CMake build of `cbox/native/ncsf`)
- **Targets:** Android only
- **Source set:** none in this module; native sources at `cbox/native/ncsf/` (SSEQ Player, C/C++, built via CMake)
- **SAGE/module dependencies:** none (Gradle deps); native build links `cbox/native/native-common` (psflib + PSF I/O + zlib). Consumed by `:cbox:common:player:emulators:ncsf:real`.
