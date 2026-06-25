# `:cbox:common:debug-info:di`

> Metro wiring that binds `RealDebugInfoManager` into `AppScope`.

The `:di` module for playback debug info: a single Metro `@BindingContainer`
contributed to `AppScope` that provides `DebugInfoManager` from the `:real`
implementation, given the player collaborators (`Director`, `Generator`,
`Speaker`, `BufferDebugSource`) and a `CoroutineScope`. Include this in the app
graph to make the diagnostic snapshot injectable; everyone else depends on `:api`.

## Contents

| File | What it is |
| --- | --- |
| `DebugInfoModule.kt` | `@BindingContainer @ContributesTo(AppScope::class)` object providing a `@SingleIn(AppScope::class)` `DebugInfoManager` = `RealDebugInfoManager(...)`. |

## Why depend on this module

The app module depends on `:cbox:common:debug-info:di` so Metro can satisfy
`DebugInfoManager` injections. Consumers (the debug PlaybackStatus screen) depend
on `:cbox:common:debug-info:api` for the type and never on `:di`. This module
transitively exposes both `:api` and `:real`.

## Using it

```kotlin
class PlaybackStatusViewModel @Inject constructor(
    manager: DebugInfoManager, // provided by DebugInfoModule
)
```

## Module facts

- **Plugin:** `sage.jvm` + `sage.di`
- **Targets:** JVM only (DI glue)
- **Source set:** `src/main/java`
- **SAGE/module dependencies:** `:cbox:common:debug-info:api`, `:cbox:common:debug-info:real` (api); `:cbox:common:player:director:api`, `:cbox:common:player:generator:api`, `:cbox:common:player:speaker:api`, `:cbox:common:player:buffer:api`, `kotlinx-coroutines-core` (impl)
