# `:cbox:android:player:emulators:usf:native`

> Android CMake/JNI companion that builds `libusf.so` — the USF (Nintendo 64) emulator core, backed by a Mupen64Plus-derived cached interpreter.

This is a `:native` module: an Android-only CMake/JNI companion. It compiles the
native sound-chip core for the USF format (`usf` / `miniusf`) into a shared
library (`libusf.so`) and exposes it to the JVM via JNI. It has **no Kotlin
public API** — the matching `:cbox:common:player:emulators:usf:real` module
declares the `external` methods and calls `System.loadLibrary("usf")` to load
this `.so` at runtime.

## Contents

This Gradle module is just a `build.gradle.kts` applying the
`chipbox.emulator.native` convention plugin. The C/C++ sources it compiles live
at the repo root under `cbox/native/usf/` (shared by the Android, JVM-host, and
Emscripten/WASM builds):

| File / dir | What it is |
| --- | --- |
| `Usf.cpp` / `Usf.h` | Chipbox load/generate/teardown wrapper over the core. |
| `kotlin-jni.cpp` | JNI bridge mapping `…usf.UsfEmulator` natives onto the wrapper. |
| `r4300/`, `rsp*/`, `rdp/`, `ri/`, `si/`, `vi/`, `ai/`, `pi/`, `memory/`, `main/`, `usf/` | The vendored **lazyusf / Mupen64Plus-derived** N64 core. ARM Android builds the cached-interpreter configuration (`r4300/empty_dynarec.c`, no x86 recompilers). |
| `CMakeLists.txt` | Builds the `usf` target; links the shared `native-common` (psflib + PSF file I/O, transitively zlib). |
| `Usf_Web.cpp` | Emscripten/WASM entry point (used by the JS build, not Android). |

## Why depend on this module

The Android app variant depends on this module so the USF `.so` is packaged into
the APK's `jniLibs`. You never call it directly: depend on
`:cbox:common:player:emulators:usf:real` for the `Emulator` implementation; that
module loads the library this one produces. The convention plugin builds one
`.so` per ABI (`arm64-v8a`, `x86_64`) via a cacheable NDK task and feeds them in
as generated `jniLibs`.

## Using it

There is no Kotlin call surface here. The consumer side looks like:

```kotlin
// in :cbox:common:player:emulators:usf:real
class UsfEmulator : Emulator {
    companion object {
        init { System.loadLibrary("usf") } // ← libusf.so from this module
    }
    external fun loadTrackInternal(filename: String)
    // …
}
```

## Module facts

- **Plugin:** `chipbox.emulator.native` (applies `sage.android`, wires the NDK/CMake build of `cbox/native/usf`)
- **Targets:** Android only
- **Source set:** none in this module; native sources at `cbox/native/usf/` (lazyusf / Mupen64Plus-derived N64 core, C/C++, built via CMake)
- **SAGE/module dependencies:** none (Gradle deps); native build links `cbox/native/native-common` (psflib + PSF I/O + zlib). Consumed by `:cbox:common:player:emulators:usf:real`.
