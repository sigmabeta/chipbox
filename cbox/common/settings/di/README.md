# `:cbox:common:settings:di`

> Metro wiring that binds `RealChipboxSettingsManager` into `AppScope`.

The `:di` module for Chipbox settings: a single Metro `@BindingContainer`
contributed to `AppScope` that provides `ChipboxSettingsManager` from the `:real`
implementation, given the app's `Storage`. Include this in the app graph to make
settings injectable; everyone else depends on `:api`.

## Contents

| File | What it is |
| --- | --- |
| `SettingsModule.kt` | `@BindingContainer @ContributesTo(AppScope::class)` object providing a `@SingleIn(AppScope::class)` `ChipboxSettingsManager` = `RealChipboxSettingsManager(storage)`. |

## Why depend on this module

The app module depends on `:cbox:common:settings:di` so Metro can satisfy
`ChipboxSettingsManager` injections. Feature/consumer modules should depend on
`:cbox:common:settings:api` for the type and never on `:di`. This module
transitively exposes both `:api` and `:real`.

## Using it

```kotlin
// In a ViewModel / class wired by Metro:
class SettingsViewModel @Inject constructor(
    private val settings: ChipboxSettingsManager, // provided by SettingsModule
)
```

## Module facts

- **Plugin:** `sage.jvm` + `sage.di`
- **Targets:** JVM only (DI glue)
- **Source set:** `src/main/java`
- **SAGE/module dependencies:** `:cbox:common:settings:api`, `:cbox:common:settings:real` (api), `sage.common.storage.common` (impl)
