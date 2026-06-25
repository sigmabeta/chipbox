# `:cbox:android:player:emulators:ssf:native`

> Android CMake/JNI companion that builds `libssf.so` — the SSF/DSF (Saturn/Dreamcast) emulator core.

This is a `:native` module: an Android-only CMake/JNI companion. It compiles the
native sound-chip core for the SSF/DSF formats (`ssf`, `minissf`, `dsf`,
`minidsf`) into a shared library (`libssf.so`) and exposes it to the JVM via JNI.
It has **no Kotlin public API** — the matching
`:cbox:common:player:emulators:ssf:real` module declares the `external` methods
and calls `System.loadLibrary("ssf")` to load this `.so` at runtime.

## Contents

This Gradle module is just a `build.gradle.kts` applying the
`chipbox.emulator.native` convention plugin. The C/C++ sources it compiles live
at the repo root under `cbox/native/ssf/` (shared by the Android, JVM-host, and
Emscripten/WASM builds):

| File / dir | What it is |
| --- | --- |
| `Ssf.cpp` / `Ssf.h` | Chipbox load/generate/teardown wrapper over the core. |
| `kotlin-jni.cpp` | JNI bridge mapping `…ssf.SsfEmulator` natives onto the wrapper. |
| `Core/` | The vendored Saturn/Dreamcast sound core: ARM + Motorola 68k (`m68k/`) CPUs and the `satsound`/`dcsound`/`yam` sound chips. |
| `CMakeLists.txt` | Builds the `ssf` target; links the shared `native-common` (psflib + PSF file I/O, transitively zlib). |
| `Ssf_Web.cpp` | Emscripten/WASM entry point (used by the JS build, not Android). |

## Why depend on this module

The Android app variant depends on this module so the SSF `.so` is packaged into
the APK's `jniLibs`. You never call it directly: depend on
`:cbox:common:player:emulators:ssf:real` for the `Emulator` implementation; that
module loads the library this one produces. The convention plugin builds one
`.so` per ABI (`arm64-v8a`, `x86_64`) via a cacheable NDK task and feeds them in
as generated `jniLibs`.

## Using it

There is no Kotlin call surface here. The consumer side looks like:

```kotlin
// in :cbox:common:player:emulators:ssf:real
class SsfEmulator : Emulator {
    companion object {
        init { System.loadLibrary("ssf") } // ← libssf.so from this module
    }
    external fun loadTrackInternal(filename: String)
    // …
}
```

## Module facts

- **Plugin:** `chipbox.emulator.native` (applies `sage.android`, wires the NDK/CMake build of `cbox/native/ssf`)
- **Targets:** Android only
- **Source set:** none in this module; native sources at `cbox/native/ssf/` (Saturn/Dreamcast core, C/C++, built via CMake)
- **SAGE/module dependencies:** none (Gradle deps); native build links `cbox/native/native-common` (psflib + PSF I/O + zlib). Consumed by `:cbox:common:player:emulators:ssf:real`.
