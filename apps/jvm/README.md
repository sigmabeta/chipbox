# Chipbox — desktop JVM app

Compose Multiplatform desktop port of the Android UI. Shares the same
`ChipboxAppUi` composable (Library / Search / Settings tabs, NowPlaying
overlay, per-tab back stacks) as Android via `cbox/android/appui/api`
(now `sage.kmp`).

It drives the **real native emulators** (not the `FakeEmulator` synth) —
GBA / GME / PSF / SSF / USF / 2SF / VGM, picked by file extension at
playback time.

## Build the native emulator libraries

The `.so`s are gitignored build artifacts. CMake / a host C-C++ toolchain
/ a JDK with JNI headers (`include/jni.h` + `include/linux/jni_md.h`) need
to be available; `apps/jvm/build.gradle.kts` registers Gradle tasks that
invoke CMake with the same flags this README used to document:

```bash
./gradlew :apps:jvm:nativeLibs     # builds all 7
./gradlew :apps:jvm:nativeEmulatorGme   # just one
```

`:apps:jvm:run` (below) already depends on `nativeLibs`, so launching the
desktop UI just-in-time builds anything missing.

Overrides:
- `-Pchipbox.jvm.cmake=/path/to/cmake` — defaults to `$PATH` cmake, then
  the Android SDK's bundled copy.
- `-Pchipbox.jvm.nativeJdk=/path/to/jdk` — defaults to a JDK probed from
  `~/.jdks` / `/usr/lib/jvm` with JNI headers present (Gradle's own
  `java.home` is often the Android Studio JBR, which strips headers).

Notes on host portability (the Android NDK build is unaffected):
- The host and NDK builds share the `cbox/native/*` CMake trees — the
  native code is no longer under `cbox/android` (two apps build it).
- `Gme.h` gained `#include <cstdint>` (NDK leaked it transitively).
- `-D__fastcall=` / `-D__cdecl=` / `-D__stdcall=` neutralise MSVC/x86
  calling-convention keywords NDK clang tolerates but host GCC rejects.
- The `android/log.h` shim + `liblog.a` satisfy psf's / mGBA's
  `__android_log_print` debug probes off-Android. The shim filters by
  priority (default WARN) so DMA / BIOS trace lines don't spam stderr;
  raise with `CHIPBOX_NATIVE_LOG_LEVEL=debug` (or `verbose|info|warn|
  error|fatal`, or `2`–`7`) when chasing an emulator bug.

## Run

```bash
./gradlew :apps:jvm:run
```

Opens the Compose Desktop window. Scan a library via Settings → Add
folder; play via Library → Browse by … → tap a track. WAV-rendering
CLI modes that used to live here (`scan` / `play` / single-file) were
removed when this became a gui-only app.

The library DB + render cache live under `<user.dir>/.chipbox-jvm/`.

## Verification status (host, x86-64 Linux)

All seven decode real, audible audio end-to-end on the JVM target via
the live `SourceDataLineSpeaker`.

| Emulator | Lib | Playback verified |
|----------|-----|-------------------|
| GME (spc/nsf/gbs) | libgme.so | ✅ Chrono Trigger SPC, 32 kHz |
| PSF (psf/minipsf/psf2) | libslopsf.so | ✅ FF IX .psf + FF7 .minipsf (chain) |
| VGM (vgm/vgz) | libvgm.so | ✅ Genesis .vgz, 44.1 kHz |
| USF (usf/miniusf) | libusf.so | ✅ Star Fox 64 .miniusf, 32 kHz |
| 2SF (2sf/mini2sf) | libtwosf.so | ✅ Sonic Rush .mini2sf, 44.1 kHz |
| GBA (gsf/minigsf) | libgba.so | ✅ Iridion 3D .minigsf (mgba), 44.1 kHz |
| SSF/DSF | libssf.so | ✅ Panzer Dragoon .ssf + Skies .dsf (chain) |
