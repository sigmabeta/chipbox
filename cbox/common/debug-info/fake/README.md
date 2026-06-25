# `:cbox:common:debug-info:fake`

> In-memory `DebugInfoManager` test double tests can push snapshots into.

The `:fake` module for playback debug info: `FakeDebugInfoManager` backs
`debugInfo()` with a `MutableStateFlow<PlaybackDebugInfo>` and exposes an `emit()`
so a test can push a snapshot that the PlaybackStatus screen's ViewModel folds
into its state. For tests and previews only.

## Contents

| File | What it is |
| --- | --- |
| `FakeDebugInfoManager.kt` | Implements `DebugInfoManager` over a `MutableStateFlow`, seeded with an empty `PlaybackDebugInfo` (matching production's "no measurements yet" baseline). `emit(info)` updates the flow. |

## Why depend on this module

Depend on `:cbox:common:debug-info:fake` from test source sets (and UI-test fakes)
that drive the PlaybackStatus screen without the real player pipeline. Production
code depends on `:api` and is wired to `:real` via `:di`.

## Using it

```kotlin
val manager = FakeDebugInfoManager()
manager.emit(PlaybackDebugInfo(track = someTrack))
// the screen's view-model, subscribed in init, now reflects the snapshot
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `:cbox:common:debug-info:api` (api), `kotlinx-coroutines-core` (impl)
