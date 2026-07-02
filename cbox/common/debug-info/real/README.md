# `:cbox:common:debug-info:real`

> Production `DebugInfoManager` — combines the player's diagnostic flows into one hot snapshot.

The `:real` module for playback debug info: `RealDebugInfoManager` `combine`s the
Director's track / playback / session streams with the generator, speaker, and
buffer diagnostic `StateFlow`s into a single `StateFlow<PlaybackDebugInfo>`,
`stateIn` a scope with `WhileSubscribed`. This is the production implementation
behind the debug PlaybackStatus screen.

## Contents

| File | What it is |
| --- | --- |
| `RealDebugInfoManager.kt` | Implements `DebugInfoManager`. Takes `Director`, `Generator`, `Speaker`, `BufferDebugSource`, and a `CoroutineScope`. The Director's three streams are `SharedFlow`s with no initial value, so each is seeded with `onStart { emit(null) }` to avoid stalling the combine until a session begins; the combined flow is shared with a 5s subscription timeout, seeded with an empty `PlaybackDebugInfo`. |

## Why depend on this module

You normally do **not** depend on `:real` directly — depend on
`:cbox:common:debug-info:api` for the interface and let
`:cbox:common:debug-info:di` bind this into `AppScope`. Depend on `:real` only to
construct `RealDebugInfoManager` yourself with its player collaborators.

## Using it

```kotlin
val manager: DebugInfoManager = RealDebugInfoManager(
    director, generator, speaker, bufferDebugSource, scope,
)
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `:cbox:common:debug-info:api` (api); `:cbox:common:models:api`, `:cbox:common:player:common:api`, `:cbox:common:player:director:api`, `:cbox:common:player:generator:api`, `:cbox:common:player:speaker:api`, `:cbox:common:player:buffer:api`, `kotlinx-coroutines-core` (impl)
