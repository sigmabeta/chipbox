# `:cbox:android:player:emulators:2sf:native`

> Android CMake/JNI companion that builds `libtwosf.so` — the 2SF (Nintendo DS) emulator core.

This is a `:native` module: an Android-only CMake/JNI companion. It compiles the
native sound-chip core for the 2SF format (`2sf` / `mini2sf`) into a shared
library (`libtwosf.so`) and exposes it to the JVM via JNI. It has **no Kotlin
public API** — the matching `:cbox:common:player:emulators:2sf:real` module
declares the `external` methods and calls `System.loadLibrary("twosf")` to load
this `.so` at runtime.

## Contents

This Gradle module is just a `build.gradle.kts` applying the
`chipbox.emulator.native` convention plugin. The C/C++ sources it compiles live
at the repo root under `cbox/native/2sf/` (shared by the Android, JVM-host, and
Emscripten/WASM builds):

| File / dir | What it is |
| --- | --- |
| `2sf.cpp` / `2sf.h` | Chipbox load/generate/teardown wrapper over the core. |
| `kotlin-jni.cpp` | JNI bridge mapping `…twosf.TwosfEmulator` natives onto the wrapper (uses the shared `chipbox_jni_bridge.h`). |
| `Core/vio2sf/…` | The vendored **vio2sf** core (a DeSmuME-derived NDS emulator). |
| `CMakeLists.txt` | Builds the `twosf` target; links the shared `native-common` (psflib + PSF file I/O, transitively zlib). |
| `Twosf_Web.cpp` | Emscripten/WASM entry point (used by the JS build, not Android). |

## Why depend on this module

The Android app variant depends on this module so the 2SF `.so` is packaged into
the APK's `jniLibs`. You never call it directly: depend on
`:cbox:common:player:emulators:2sf:real` for the `Emulator` implementation; that
module loads the library this one produces. The convention plugin builds one
`.so` per ABI (`arm64-v8a`, `x86_64`) via a cacheable NDK task and feeds them in
as generated `jniLibs`.

## Using it

There is no Kotlin call surface here. The consumer side looks like:

```kotlin
// in :cbox:common:player:emulators:2sf:real
class TwosfEmulator : Emulator {
    companion object {
        init { System.loadLibrary("twosf") } // ← libtwosf.so from this module
    }
    external fun loadTrackInternal(filename: String)
    // …
}
```

## Module facts

- **Plugin:** `chipbox.emulator.native` (applies `sage.android`, wires the NDK/CMake build of `cbox/native/2sf`)
- **Targets:** Android only
- **Source set:** none in this module; native sources at `cbox/native/2sf/` (C/C++, built via CMake)
- **SAGE/module dependencies:** none (Gradle deps); native build links `cbox/native/native-common` (psflib + PSF I/O + zlib). Consumed by `:cbox:common:player:emulators:2sf:real`.
