# `:cbox:android:player:emulators:gba:native`

> Android CMake/JNI companion that builds `libgba.so` — the GSF (Game Boy Advance) emulator core, backed by mGBA.

This is a `:native` module: an Android-only CMake/JNI companion. It compiles the
native sound-chip core for the GSF format (`gsf` / `minigsf`) into a shared
library (`libgba.so`) and exposes it to the JVM via JNI. It has **no Kotlin
public API** — the matching `:cbox:common:player:emulators:gba:real` module
declares the `external` methods and calls `System.loadLibrary("gba")` to load
this `.so` at runtime.

## Contents

This Gradle module is just a `build.gradle.kts` applying the
`chipbox.emulator.native` convention plugin. The C/C++ sources it compiles live
at the repo root under `cbox/native/gba/` (shared by the Android, JVM-host, and
Emscripten/WASM builds):

| File / dir | What it is |
| --- | --- |
| `Gba.cpp` / `Gba.h` | Chipbox load/generate/teardown wrapper over the core. `loadFile` pre-rolls ~0.5 s (30 frames) so the GSF driver can program its mixing rate, then fixes the track's output rate at the settled rate, capped at 65536 Hz. The pre-roll audio is kept as the start of the track. |
| `GbaOutput.cpp` / `.h` | Output stage: frames the core made at the output rate pass straight through; any other rate (the 32768 Hz start before the driver switches, a later switch, a 131072/262144 Hz driver above the cap) is resampled to it. Passing through instead of resampling everything to 44.1 kHz is what lifted the old ~13.5× real-time cap. |
| `GbaResampler.cpp` / `.h` | Faster drop-in for mGBA's stereo sinc resampler with bit-identical output, used by `GbaOutput` for the segments it has to resample. The upstream path used to take ~70% of GSF render time. |
| `kotlin-jni.cpp` | JNI bridge mapping `…gba.GbaEmulator` natives onto the wrapper. |
| `src/`, `include/`, `version.cmake` | The vendored **mGBA** core, built in `LIBMGBA_ONLY` mode (static lib, GBA+GB cores, frontends/deps disabled). |
| `CMakeLists.txt` | mGBA's own build script; builds the `gba` target and links the shared `native-common` (psflib, PSF file I/O and the shared `chipbox_env.h` debug-switch header). |
| `Gba_Web.cpp` | Emscripten/WASM entry point (used by the JS build, not Android). |

## Why depend on this module

The Android app variant depends on this module so the GSF `.so` is packaged into
the APK's `jniLibs`. You never call it directly: depend on
`:cbox:common:player:emulators:gba:real` for the `Emulator` implementation; that
module loads the library this one produces. The convention plugin builds one
`.so` per ABI (`arm64-v8a`, `x86_64`) via a cacheable NDK task and feeds them in
as generated `jniLibs`.

## Using it

There is no Kotlin call surface here. The consumer side looks like:

```kotlin
// in :cbox:common:player:emulators:gba:real
class GbaEmulator : Emulator {
    companion object {
        init { System.loadLibrary("gba") } // ← libgba.so (mGBA) from this module
    }
    external fun loadTrackInternal(filename: String)
    // …
}
```

## Module facts

- **Plugin:** `chipbox.emulator.native` (applies `sage.android`, wires the NDK/CMake build of `cbox/native/gba`)
- **Targets:** Android only
- **Source set:** none in this module; native sources at `cbox/native/gba/` (mGBA, C/C++, built via CMake)
- **SAGE/module dependencies:** none (Gradle deps); native build links `cbox/native/native-common` (psflib + PSF I/O + the shared `chipbox_env.h` header). Consumed by `:cbox:common:player:emulators:gba:real`.
