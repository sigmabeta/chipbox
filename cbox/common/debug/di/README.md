# `:cbox:common:debug:di`

> Metro wiring that binds `RealDebugSettingsManager` into `AppScope`.

The `:di` module for Chipbox debug settings: a single Metro `@BindingContainer`
contributed to `AppScope` that provides `DebugSettingsManager` from the `:real`
implementation, given the app's `Storage`. Include this in the app graph to make
debug settings injectable; everyone else depends on `:api`.

## Contents

| File | What it is |
| --- | --- |
| `DebugModule.kt` | `@BindingContainer @ContributesTo(AppScope::class)` object providing a `@SingleIn(AppScope::class)` `DebugSettingsManager` = `RealDebugSettingsManager(storage)`. |

## Why depend on this module

The app module depends on `:cbox:common:debug:di` so Metro can satisfy
`DebugSettingsManager` injections. Consumers should depend on
`:cbox:common:debug:api` for the type and never on `:di`. This module
transitively exposes both `:api` and `:real`.

## Using it

```kotlin
class DebugMenuViewModel @Inject constructor(
    private val debug: DebugSettingsManager, // provided by DebugModule
)
```

## Module facts

- **Plugin:** `sage.jvm` + `sage.di`
- **Targets:** JVM only (DI glue)
- **Source set:** `src/main/java`
- **SAGE/module dependencies:** `:cbox:common:debug:api`, `:cbox:common:debug:real` (api), `sage.common.storage.common` (impl)
