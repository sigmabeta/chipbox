# `:cbox:common:history:di`

> Metro wiring for playback history — builds the repository and recorder into
> `AppScope`.

The `:di` module for playback history. Its Metro `@BindingContainer` provides the
`PlaybackHistoryRepository` (from the `HistoryDatabase` DAOs, or the fake when the
debug menu selects it) and the `PlaybackHistoryRecorder` (from the director).
The app graph includes this module; the `HistoryDatabase` itself is provided
per-platform (Android: `cbox/android/history/di`; JVM: `apps/jvm` history module),
mirroring how `ChipboxDatabase` is provided.

## Contents

| File | What it is |
| --- | --- |
| `HistoryModule.kt` | `@BindingContainer @ContributesTo(AppScope::class)`. Provides `PlaybackHistoryRepository` (REAL `RealPlaybackHistoryRepository` vs FAKE `FakePlaybackHistoryRepository`, chosen once at graph build from `DebugSettingsManager.getHistorySource()`) and `PlaybackHistoryRecorder` (`RealPlaybackHistoryRecorder`), both `@SingleIn(AppScope::class)`. |

## Why depend on this module

The app graph includes `:di` to get history wired into `AppScope`. It pulls in
`:cbox:common:history:real` and `:cbox:common:history:fake`; the debug switch
picks between them at graph build (applied on the next launch).

## Using it

```kotlin
// In the app graph — included transitively via @ContributesTo(AppScope::class).
// Consumers inject the bound types:
@Inject class HomeViewModel(repository: PlaybackHistoryRepository)
```

## Module facts

- **Plugin:** `sage.jvm` + `sage.di`
- **Targets:** JVM (the DI wiring is plain JVM; the bound impls are KMP)
- **Source set:** `src/main/java`
- **SAGE/module dependencies:** `:cbox:common:history:api`, `:cbox:common:history:real`, `:cbox:common:history:fake`, `:cbox:common:debug:api`, `:cbox:common:player:director:api`, `kotlinx-coroutines-core`, `sage.common.logging`
