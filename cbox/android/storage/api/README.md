# `:cbox:android:storage:api`

> Android DataStore-backed `Storage` implementation plus its Metro binding.

Android system glue (`:api`): provides the Android implementation of SAGE's key/value `Storage`
interface, backed by Jetpack DataStore Preferences, and the Metro binding that exposes it into
`AppScope`. This module holds both the impl (`AndroidDataStore`) and its `:di` wiring; the `:api`
role here is "platform implementation of a SAGE interface, plus binding".

## Contents

| File | What it is |
| --- | --- |
| `AndroidDataStore.kt` | `Storage` impl over `DataStore<Preferences>`. `saveString`/`saveInt` write on the disk dispatcher; `savedStringFlow`/`savedIntFlow` map the DataStore `data` flow. Logs each read/write at verbose. |
| `di/StorageModule.kt` | `@BindingContainer @ContributesTo(AppScope)` object. `@Provides @SingleIn(AppScope)` builds `AndroidDataStore` from a `preferencesDataStore(name = "chipbox_settings")` extension. `DataStore<Preferences>` is constructed inline (not its own binding) to sidestep a Metro 1.1.1 cross-module Provider-generation bug. |

## Why depend on this module

The app graph depends on this `:di`-bearing `:api` module so the SAGE `Storage` interface can be
resolved on Android (used by settings/debug-settings managers). Depend on the SAGE
`storage.common` module for the `Storage` type.

## Using it

```kotlin
// Resolved from the graph; settings managers consume it:
class SettingsManager @Inject constructor(storage: Storage) {
    val theme = storage.savedStringFlow("theme")
}
```

## Module facts

- **Plugin:** `sage.android` + `sage.di`
- **Targets:** Android only
- **Source set:** `src/main/java`
- **SAGE/module dependencies:** `sage.common.storage.common`, `sage.common.coroutines`, `sage.common.logging`, AndroidX DataStore Preferences
