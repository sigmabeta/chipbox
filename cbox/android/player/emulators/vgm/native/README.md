# `:cbox:android:player:emulators:vgm:native`

> Android CMake/JNI companion that builds `libvgm.so` — the VGM/VGZ multi-chip core, backed by libvgm.

This is a `:native` module: an Android-only CMake/JNI companion. It compiles the
native sound-chip core for the VGM formats (`vgm` / `vgz`) into a shared library
(`libvgm.so`) and exposes it to the JVM via JNI. It has **no Kotlin public API**
— the matching `:cbox:common:player:emulators:vgm:real` module declares the
`external` methods and calls `System.loadLibrary("vgm")` to load this `.so` at
runtime.

## Contents

This Gradle module is just a `build.gradle.kts` applying the
`chipbox.emulator.native` convention plugin. The C/C++ sources it compiles live
at the repo root under `cbox/native/vgm/` (shared by the Android, JVM-host, and
Emscripten/WASM builds):

| File / dir | What it is |
| --- | --- |
| `Vgm.cpp` / `Vgm.h` | Chipbox load/generate/teardown wrapper over the core. |
| `kotlin-jni.cpp` | JNI bridge mapping `…vgm.VgmEmulator` natives onto the wrapper. |
| `emu/`, `player/`, `utils/`, `libs/`, `cmake/` | The vendored **libvgm** library, built core-only as static libs (`vgm-emu`, `vgm-player`, `vgm-utils`): all chip cores + zlib loaders, no audio drivers/threading/charset conversion. |
| `cpconv_stub.c` | No-op charset-conversion stub (GD3 metadata is parsed in Kotlin, not here). |
| `CMakeLists.txt` | Builds the `vgm` target; links the libvgm static libs, `z` (zlib) and the shared `chipbox_jni_bridge`. |
| `Vgm_Web.cpp` | Emscripten/WASM entry point (used by the JS build, not Android). |

## Why depend on this module

The Android app variant depends on this module so the VGM `.so` is packaged into
the APK's `jniLibs`. You never call it directly: depend on
`:cbox:common:player:emulators:vgm:real` for the `Emulator` implementation; that
module loads the library this one produces. The convention plugin builds one
`.so` per ABI (`arm64-v8a`, `x86_64`) via a cacheable NDK task and feeds them in
as generated `jniLibs`.

## Using it

There is no Kotlin call surface here. The consumer side looks like:

```kotlin
// in :cbox:common:player:emulators:vgm:real
class VgmEmulator : Emulator {
    companion object {
        init { System.loadLibrary("vgm") } // ← libvgm.so from this module
    }
    external fun loadTrackInternal(filename: String)
    // …
}
```

## Module facts

- **Plugin:** `chipbox.emulator.native` (applies `sage.android`, wires the NDK/CMake build of `cbox/native/vgm`)
- **Targets:** Android only
- **Source set:** none in this module; native sources at `cbox/native/vgm/` (libvgm, C/C++, built via CMake)
- **SAGE/module dependencies:** none (Gradle deps); native build links the libvgm static libs, zlib, and the shared `chipbox_jni_bridge` header. Consumed by `:cbox:common:player:emulators:vgm:real`.
