# `:cbox:android:player:speaker:di`

> Metro wiring that constructs and selects the `Speaker` (real `AudioTrack` / file / text), with output-rate and resampler resolution.

DI module (`:di`): a Metro `@BindingContainer` that builds the `RealSpeaker` (opening at the
device's preferred output sample rate and applying the chosen resampler), plus the `FileSpeaker`
and `TextSpeaker` debug variants, then picks one from the debug "speaker source" setting. Holds no
impl of its own; it wires the `:real`/`:fake` speakers at the DI seam and resolves the
`ResamplerMode` from settings.

## Contents

| File | What it is |
| --- | --- |
| `SpeakerModule.kt` | `@BindingContainer @ContributesTo(AppScope)` object. Provides the external-storage `File` location, `FileSpeaker`, `TextSpeaker`, and `RealSpeaker`; the latter reads `AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE` (default 48 kHz) and resolves a `Resampler` from `ChipboxSettingsManager.getResamplerMode()` (OS mode → null, AudioFlinger resamples). `provideSpeaker` reads `DebugSettingsManager.getSpeakerSource()` once at graph build and returns `REAL`/`FILE`/`TEXT`. |

## Why depend on this module

The app graph depends on this `:di` module so a `Speaker` (the audio-output end of the playback
pipeline) can be resolved on Android. Output-rate and resampler choices are fixed at launch from
settings. Depend on `:cbox:common:player:speaker:api` for the `Speaker` type.

## Using it

```kotlin
// Resolved from the graph; the playback pipeline consumes it:
class SomePipeline @Inject constructor(speaker: Speaker)
```

## Module facts

- **Plugin:** `sage.android` + `sage.di`
- **Targets:** Android only
- **Source set:** `src/main/java`
- **SAGE/module dependencies:** `:cbox:common:player:speaker:api`, `:cbox:common:player:speaker:real`, `:cbox:common:player:speaker:fake`, `:cbox:common:settings:api`, `:cbox:common:player:resampler:api`, `:cbox:common:debug:api`
