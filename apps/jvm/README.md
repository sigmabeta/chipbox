# Chipbox — headless JVM app

A no-Android target that drives the real player pipeline
(`Emulator → RealGenerator/render-ahead cache → buffer manager → Speaker`)
and writes the decoded PCM to a WAV file. Used as the KMP-migration proof
and a headless harness for the shared player core.

It drives the **real native emulators** (not the `FakeEmulator` synth).
All seven are wired (`ALL_EMULATORS` in `Main.kt`); the generator picks one
per track by file extension.

## Build the native emulator libraries (one-time, host build)

The `.so`s are build artifacts (gitignored), not committed. They are the
same C/C++ source the Android modules compile with the NDK, built here for
the host. Requires `cmake`, a host C/C++ toolchain, and a JDK with JNI
headers (`include/jni.h`, `include/linux/jni_md.h`).

```bash
JDK=/path/to/jdk            # e.g. ~/.jdks/jbr-21.0.6
SHIM="$PWD/apps/jvm/native-build/hostshim"

# One-time android/log shim (psf's debug probes call __android_log_print).
mkdir -p "$SHIM/include/android"
cat > "$SHIM/include/android/log.h" <<'EOF'
#ifndef _HOSTSHIM_ANDROID_LOG_H
#define _HOSTSHIM_ANDROID_LOG_H
#ifdef __cplusplus
extern "C" {
#endif
enum { ANDROID_LOG_VERBOSE=2, ANDROID_LOG_DEBUG, ANDROID_LOG_INFO,
       ANDROID_LOG_WARN, ANDROID_LOG_ERROR, ANDROID_LOG_FATAL };
int __android_log_print(int prio, const char *tag, const char *fmt, ...);
#ifdef __cplusplus
}
#endif
#endif
EOF
printf '%s\n' '#include <stdarg.h>' '#include <stdio.h>' \
  'int __android_log_print(int p,const char*t,const char*f,...){va_list a;va_start(a,f);fprintf(stderr,"[%s] ",t?t:"?");int n=vfprintf(stderr,f,a);fputc(10,stderr);va_end(a);return n;}' \
  > "$SHIM/log.c"
gcc -fPIC -c "$SHIM/log.c" -o "$SHIM/log.o" && ar rcs "$SHIM/liblog.a" "$SHIM/log.o"

# Per-emulator: SRC dir under cbox/native, and the CMake target name.
#   gme=gme  psf=slopsf  ssf=ssf  usf=usf  2sf=twosf  vgm=vgm  gba=gba
HOSTDEF="-D__fastcall= -D__cdecl= -D__stdcall= -I$SHIM/include"
build() {  # $1=cbox/native subdir  $2=cmake target
  cmake -S "cbox/native/$1" -B "apps/jvm/native-build/$1" -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_FLAGS="-I$JDK/include -I$JDK/include/linux -fPIC $HOSTDEF" \
    -DCMAKE_CXX_FLAGS="-I$JDK/include -I$JDK/include/linux -fPIC $HOSTDEF" \
    -DCMAKE_SHARED_LINKER_FLAGS="-L$SHIM" \
    -DCMAKE_LIBRARY_OUTPUT_DIRECTORY="$PWD/apps/jvm/libs" &&
  cmake --build "apps/jvm/native-build/$1" --target "$2" -j
}
build gme gme;  build psf slopsf; build ssf ssf; build usf usf
build 2sf twosf; build vgm vgm;   build gba gba
```

Notes on host portability (the Android NDK build is unaffected):

- The host and NDK builds share the `cbox/native/*` CMake trees — the
  native code is no longer under `cbox/android` (two apps build it).
- `Gme.h` gained `#include <cstdint>` (NDK leaked it transitively).
- `-D__fastcall=` / `-D__cdecl=` / `-D__stdcall=` neutralise MSVC/x86
  calling-convention keywords NDK clang tolerates but host GCC rejects.
- The `android/log.h` shim + `liblog.a` satisfy psf's `__android_log_print`
  debug probes off-Android.

## Run

A track path is required (no default — fails fast with a usage message if
omitted). Quote the inner path so spaces survive Gradle's `--args`.

```bash
./gradlew :apps:jvm:run --args="'/path/to/track.psf'"
./gradlew :apps:jvm:run --args="'/path/to/track.spc' /out/dir"
```

`args[0]` (required) = any file a wired emulator supports; the generator
selects by extension. `args[1]` (optional) = output directory (default:
current directory). `*lib` neighbours of the track (psflib/gsflib/…) are
staged so mini-formats resolve their `_lib`. The `run` task sets
`-Djava.library.path=apps/jvm/libs`. Output:
`<outDir>/Chipbox Output Files/temp.wav`.

## Verification status (host, x86-64 Linux)

All seven decode real, audible audio end-to-end on the JVM target.

| Emulator | Lib | Playback verified |
|----------|-----|-------------------|
| GME (spc/nsf/gbs) | libgme.so | ✅ Chrono Trigger SPC, 32 kHz |
| PSF (psf/minipsf/psf2) | libslopsf.so | ✅ FF IX .psf + FF7 .minipsf (chain) |
| VGM (vgm/vgz) | libvgm.so | ✅ Genesis .vgz, 44.1 kHz |
| USF (usf/miniusf) | libusf.so | ✅ Star Fox 64 .miniusf, 32 kHz |
| 2SF (2sf/mini2sf) | libtwosf.so | ✅ Sonic Rush .mini2sf, 44.1 kHz |
| GBA (gsf/minigsf) | libgba.so | ✅ Iridion 3D .minigsf (mgba), 44.1 kHz |
| SSF/DSF | libssf.so | ✅ Panzer Dragoon .ssf + Skies .dsf (chain) |

The heavy CPU emulators (USF/2SF/GBA/SSF/DSF) decode slower than real time
on a cold cache, so the render-ahead reader logs a non-fatal "cache writer
fell behind" near the end — a full-length, audible WAV is still produced
(the second run is cache-served and instant). An earlier SSF SIGSEGV was a
harness bug (the `.ssflib` chain wasn't staged → bad loader state), fixed
by staging `*lib` siblings; it was never an upstream-core problem.
