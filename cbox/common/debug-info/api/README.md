# `:cbox:common:debug-info:api`

> Live playback diagnostics — `DebugInfoManager` and the combined `PlaybackDebugInfo` snapshot.

The `:api` module for playback debug info: it declares `DebugInfoManager` (a
single `StateFlow<PlaybackDebugInfo>`) and the `PlaybackDebugInfo` data class that
aggregates the Director's track / playback / session view with the per-component
diagnostics (generator, speaker, buffer). This is the data source for the debug
PlaybackStatus screen. Depend on this for the types; the app wires `:real` via
`:di`.

## Contents

| File | What it is |
| --- | --- |
| `DebugInfoManager.kt` | Interface exposing `debugInfo(): StateFlow<PlaybackDebugInfo>` — one combined diagnostic snapshot. |
| `PlaybackDebugInfo.kt` | Aggregate snapshot: `track`, `playback` (`ChipboxPlaybackState`), `session`, plus `GeneratorDebugInfo` / `SpeakerDebugInfo` / `BufferDebugInfo` (all nullable, default empty). |

## Why depend on this module

Depend on `:cbox:common:debug-info:api` from the debug PlaybackStatus screen's
ViewModel to observe the combined snapshot. It re-exports the player `:api`
modules whose diagnostic types appear in `PlaybackDebugInfo` (models, player
common/director/generator/speaker/buffer). The app injects
`RealDebugInfoManager` (`:real`) via `:di`; tests use `FakeDebugInfoManager`
(`:fake`).

## Using it

```kotlin
class PlaybackStatusViewModel(manager: DebugInfoManager) {
    val info: StateFlow<PlaybackDebugInfo> = manager.debugInfo()
}
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies (api):** `:cbox:common:models:api`, `:cbox:common:player:common:api`, `:cbox:common:player:director:api`, `:cbox:common:player:generator:api`, `:cbox:common:player:speaker:api`, `:cbox:common:player:buffer:api`, `kotlinx-coroutines-core`
