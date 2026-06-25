# `:cbox:common:player:emulators:vgmstream:real`

> The `Emulator` (and scan-time prober) backend for vgmstream — hundreds of *streamed* game-audio formats.

The `:real` module: a JNI wrapper around the vendored vgmstream library, which
decodes hundreds of streamed (prerecorded) game-audio formats — ADX, HCA,
NGC_DSP/BRSTM, STRM, FSB, the IMA/MSADPCM/PSX/XA families, and more. This is a
different category from chipbox's synthesized chiptune cores, so the emulator is
registered LAST and dedicated chiptune backends win any shared extension. It also
provides the scan-time subsong prober. The audio/native code comes from the core
packaged by the matching `:native` companion.

## Contents

| File | What it is |
| --- | --- |
| `VgmstreamEmulator.kt` | `object VgmstreamEmulator : Emulator()`. Loads `libvgmstream` via `System.loadLibrary("vgmstream")`. `supportedFileExtensions` is lazily queried from the native library (`getSupportedExtensionsInternal`, the single source of truth, minus generic wav/ogg/mp3). Many formats pack multiple subsongs, so it overrides `setTrackNumber` and passes the index into `loadTrackInternalWithNumber(path, trackNumber)`; generate/teardown/error/sample-rate are `external` JNI calls. |
| `VgmstreamProbe.kt` | `object VgmstreamProbe : VgmstreamProber` (the `:api` interface). Shares the native lib and extension set with `VgmstreamEmulator`. `probe(bytes, extension)` stages the bytes to a temp file (vgmstream decodes by path), calls `external probeInternal(path)` which returns `"sampleRate\tlengthMs\tstreamName"` lines, and maps them to `VgmstreamSubsong`s (1-based subsong index). |

## Why depend on this module

Depend on `:vgmstream:real` to add streamed-format playback to the
`EmulatorProvider`, and to provide the scanner's `VgmstreamProber`. The Android
`.so` is built and packaged by the CMake companion
`:cbox:android:player:emulators:vgmstream:native` (referenced as a `runtimeOnly`
androidMain dependency by the `chipbox.emulator.real` plugin); the JVM target
host-builds the same native tree. Depend on `:api` for the `Emulator`,
`VgmstreamProber`, and `VgmstreamSubsong` types.

## Using it

```kotlin
// Playback (subsong rides in as the track number):
VgmstreamEmulator.loadNativeLib()  // System.loadLibrary("vgmstream")
VgmstreamEmulator.loadTrack(track)
val frames = VgmstreamEmulator.generateBuffer(buffer)

// Scan time — enumerate subsongs:
val subsongs: List<VgmstreamSubsong> = VgmstreamProbe.probe(fileBytes, "adx")
```

## Module facts

- **Plugin:** `chipbox.emulator.real` (applies `sage.kmp`)
- **Targets:** Android + JVM
- **Source set:** `src/main/java` (`jvmSharedMain`)
- **SAGE/module dependencies:** via the plugin — `:cbox:common:player:common:api`, `:cbox:common:player:emulators:api`, `:cbox:common:repository:api`; androidMain `runtimeOnly` `:cbox:android:player:emulators:vgmstream:native`
