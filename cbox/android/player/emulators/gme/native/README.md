# `:cbox:android:player:emulators:gme:native`

> Android CMake/JNI companion that builds `libgme.so` — the Game Music Emu core (NSF/GBS/SPC).

This is a `:native` module: an Android-only CMake/JNI companion. It compiles the
native sound-chip core for the GME-handled formats (`gbs`, `nsf`, `nsfe`, `spc`)
into a shared library (`libgme.so`) and exposes it to the JVM via JNI. It has
**no Kotlin public API** — the matching `:cbox:common:player:emulators:gme:real`
module declares the `external` methods and calls `System.loadLibrary("gme")` to
load this `.so` at runtime.

## Contents

This Gradle module is just a `build.gradle.kts` applying the
`chipbox.emulator.native` convention plugin. The C/C++ sources it compiles live
at the repo root under `cbox/native/gme/` (shared by the Android, JVM-host, and
Emscripten/WASM builds):

| File / dir | What it is |
| --- | --- |
| `Gme.cpp` / `Gme.h` | Chipbox load/generate/teardown wrapper over the core. |
| `kotlin-jni.cpp` | JNI bridge mapping `…gme.GmeEmulator` natives onto the wrapper. |
| `gme/` | The vendored **Game Music Emu (libgme)** library — Game Boy (GBS), NES (NSF/NSFe) and SNES (SPC) cores. |
| `CMakeLists.txt` | Builds the `gme` target; links `z` (zlib) and the shared header-only JNI bridge (`chipbox_jni_bridge`). |
| `Gme_Web.cpp` | Emscripten/WASM entry point (used by the JS build, not Android). |

## Why depend on this module

The Android app variant depends on this module so the GME `.so` is packaged into
the APK's `jniLibs`. You never call it directly: depend on
`:cbox:common:player:emulators:gme:real` for the `Emulator` implementation; that
module loads the library this one produces. The convention plugin builds one
`.so` per ABI (`arm64-v8a`, `x86_64`) via a cacheable NDK task and feeds them in
as generated `jniLibs`.

## Using it

There is no Kotlin call surface here. The consumer side looks like:

```kotlin
// in :cbox:common:player:emulators:gme:real
class GmeEmulator : Emulator {
    companion object {
        init { System.loadLibrary("gme") } // ← libgme.so from this module
    }
    external fun loadTrackInternal(filename: String)
    // …
}
```

## Module facts

- **Plugin:** `chipbox.emulator.native` (applies `sage.android`, wires the NDK/CMake build of `cbox/native/gme`)
- **Targets:** Android only
- **Source set:** none in this module; native sources at `cbox/native/gme/` (libgme, C/C++, built via CMake)
- **SAGE/module dependencies:** none (Gradle deps); native build links zlib + the shared `chipbox_jni_bridge` header. Consumed by `:cbox:common:player:emulators:gme:real`.
