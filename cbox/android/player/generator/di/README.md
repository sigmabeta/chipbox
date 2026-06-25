# `:cbox:android:player:generator:di`

> Metro wiring that constructs and selects the audio `Generator` (real emulator vs synth).

DI module (`:di`): a Metro `@BindingContainer` that builds both the `RealGenerator` (driving the
real emulators, with Android `cacheDir`-backed playback/PCM cache paths) and the `SynthGenerator`,
then picks one from the debug "generator source" setting. Holds no impl of its own; it wires the
`:real` and `:fake` generators at the DI seam.

## Contents

| File | What it is |
| --- | --- |
| `GeneratorModule.kt` | `@BindingContainer @ContributesTo(AppScope)` object. `provideRealGenerator` constructs `RealGenerator` with `EmulatorProvider`, `ProducerBufferManager`, `Repository`, `ContentSourceRegistry`, and `cacheDir/playback` + `cacheDir/pcm-cache` paths on `FileSystem.SYSTEM`; `provideSynthGenerator` builds the in-process `SynthGenerator`; `provideGenerator` reads `DebugSettingsManager.getGeneratorSource()` once at graph build and returns `REAL` or `FAKE` (synth). |

## Why depend on this module

The app graph depends on this `:di` module so a `Generator` (the emulation half of the playback
pipeline) can be resolved on Android, with cache directories rooted in the app's `cacheDir`. The
selection is fixed at launch. Depend on `:cbox:common:player:generator:api` for the `Generator`
type.

## Using it

```kotlin
// Resolved from the graph; the playback pipeline consumes it:
class SomePipeline @Inject constructor(generator: Generator)
```

## Module facts

- **Plugin:** `sage.android` + `sage.di`
- **Targets:** Android only
- **Source set:** `src/main/java`
- **SAGE/module dependencies:** `:cbox:common:player:generator:api`, `:cbox:common:player:generator:real`, `:cbox:common:player:generator:fake`, `:cbox:common:debug:api`
