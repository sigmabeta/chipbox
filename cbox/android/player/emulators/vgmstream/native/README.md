# `:cbox:android:player:emulators:vgmstream:native`

> Android CMake/JNI companion that builds `libvgmstream.so` — the vgmstream core (hundreds of game streamed-audio formats).

This is a `:native` module: an Android-only CMake/JNI companion. It compiles the
native decoder core (the vgmstream library) into a shared library
(`libvgmstream.so`) and exposes it to the JVM via JNI. It has **no Kotlin public
API** — the matching `:cbox:common:player:emulators:vgmstream:real` module
declares the `external` methods and calls `System.loadLibrary("vgmstream")` to
load this `.so` at runtime.

## Contents

This Gradle module is just a `build.gradle.kts` applying the
`chipbox.emulator.native` convention plugin. The C/C++ sources it compiles live
at the repo root under `cbox/native/vgmstream/` (shared by the Android,
JVM-host, and Emscripten/WASM builds):

| File / dir | What it is |
| --- | --- |
| `chipbox_vgmstream.cpp` / `chipbox_vgmstream.h` | Chipbox load/generate/teardown wrapper over `libvgmstream.h`. |
| `kotlin-jni.cpp` | JNI bridge mapping `…vgmstream.VgmstreamEmulator` natives onto the wrapper. |
| `Core/vgmstream/` | The vendored **vgmstream** tree, built core-only (no `VGM_USE_*` defines, so codecs needing external libs like FFmpeg/Vorbis #ifdef out). Built-in codecs still cover hundreds of formats (PCM, ADX, HCA, NGC_DSP/BRSTM, the IMA/MSADPCM/PSX/XA families, STRM, FSB, …). |
| `CMakeLists.txt` | Builds the `vgmcore` static lib then the `vgmstream` shared target; links `vgmcore` + `m`. |
| `Vgmstream_Web.cpp` | Emscripten/WASM entry point (used by the JS build, not Android). |

## Why depend on this module

The Android app variant depends on this module so the vgmstream `.so` is packaged
into the APK's `jniLibs`. You never call it directly: depend on
`:cbox:common:player:emulators:vgmstream:real` for the `Emulator` implementation;
that module loads the library this one produces. The convention plugin builds one
`.so` per ABI (`arm64-v8a`, `x86_64`) via a cacheable NDK task and feeds them in
as generated `jniLibs`.

## Using it

There is no Kotlin call surface here. The consumer side looks like:

```kotlin
// in :cbox:common:player:emulators:vgmstream:real
class VgmstreamEmulator : Emulator {
    companion object {
        init { System.loadLibrary("vgmstream") } // ← libvgmstream.so from this module
    }
    external fun loadTrackInternal(filename: String)
    // …
}
```

## Module facts

- **Plugin:** `chipbox.emulator.native` (applies `sage.android`, wires the NDK/CMake build of `cbox/native/vgmstream`)
- **Targets:** Android only
- **Source set:** none in this module; native sources at `cbox/native/vgmstream/` (vgmstream, C/C++, built via CMake)
- **SAGE/module dependencies:** none (Gradle deps); native build links the `vgmcore` static lib + libm, and includes the shared `native-common` JNI bridge header. Consumed by `:cbox:common:player:emulators:vgmstream:real`.
