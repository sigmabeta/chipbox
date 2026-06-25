# `:cbox:android:player:emulators:psf:native`

> Android CMake/JNI companion that builds `libslopsf.so` — the PSF/PSF2 (PlayStation 1/2) emulator core.

This is a `:native` module: an Android-only CMake/JNI companion. It compiles the
native sound-chip core for the PSF formats (`psf`, `minipsf`, `psf2`, `minipsf2`)
into a shared library (`libslopsf.so`) and exposes it to the JVM via JNI. It has
**no Kotlin public API** — the matching `:cbox:common:player:emulators:psf:real`
module declares the `external` methods and calls `System.loadLibrary("slopsf")`
to load this `.so` at runtime.

## Contents

This Gradle module is just a `build.gradle.kts` applying the
`chipbox.emulator.native` convention plugin. The C/C++ sources it compiles live
at the repo root under `cbox/native/psf/` (shared by the Android, JVM-host, and
Emscripten/WASM builds):

| File / dir | What it is |
| --- | --- |
| `Psf.cpp` / `Psf.h` | Chipbox load/generate/teardown wrapper over the core. |
| `kotlin-jni.cpp` | JNI bridge mapping `…psf.PsfEmulator` natives onto the wrapper. |
| `slop/` | The vendored **"slopsf"** PS1/PS2 core (Neill Corlett's PSXCore0008), run purely via the HLE IOP-kernel path — no copyrighted BIOS blob. |
| `CMakeLists.txt` | Builds the `slopsf` target; links the shared `native-common` (psflib + PSF file I/O, transitively zlib) and `log`. |
| `Psf_Web.cpp` | Emscripten/WASM entry point (used by the JS build, not Android). |

## Why depend on this module

The Android app variant depends on this module so the PSF `.so` is packaged into
the APK's `jniLibs`. You never call it directly: depend on
`:cbox:common:player:emulators:psf:real` for the `Emulator` implementation; that
module loads the library this one produces. The convention plugin builds one
`.so` per ABI (`arm64-v8a`, `x86_64`) via a cacheable NDK task and feeds them in
as generated `jniLibs`.

## Using it

There is no Kotlin call surface here. The consumer side looks like:

```kotlin
// in :cbox:common:player:emulators:psf:real
class PsfEmulator : Emulator {
    companion object {
        init { System.loadLibrary("slopsf") } // ← libslopsf.so from this module
    }
    external fun loadTrackInternal(filename: String)
    // …
}
```

## Module facts

- **Plugin:** `chipbox.emulator.native` (applies `sage.android`, wires the NDK/CMake build of `cbox/native/psf`)
- **Targets:** Android only
- **Source set:** none in this module; native sources at `cbox/native/psf/` (slopsf / PSXCore0008, C/C++, built via CMake)
- **SAGE/module dependencies:** none (Gradle deps); native build links `cbox/native/native-common` (psflib + PSF I/O + zlib) and `log`. Consumed by `:cbox:common:player:emulators:psf:real`.
