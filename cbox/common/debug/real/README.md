# `:cbox:common:debug:real`

> Production `DebugSettingsManager` — debug toggles persisted through SAGE `Storage`.

The `:real` module for Chipbox debug settings: `RealDebugSettingsManager` backs
each toggle and `*Source` from `:cbox:common:debug:api` with a key/value entry in
the shared SAGE `Storage`. Enum sources persist by `.name` and parse back via
`fromStorageValue`; `shouldShowDebug` defaults to `false`.

## Contents

| File | What it is |
| --- | --- |
| `RealDebugSettingsManager.kt` | Implements `DebugSettingsManager` over `Storage`, with `savedStringFlow` reads and `saveString` writes. Storage keys are `const`s in its companion (`setting.debug.*`). |

## Why depend on this module

You normally do **not** depend on `:real` directly — depend on
`:cbox:common:debug:api` for the interface and let `:cbox:common:debug:di` bind
this implementation into `AppScope`. Depend on `:real` only to construct
`RealDebugSettingsManager` yourself with a `Storage`.

## Using it

```kotlin
val debug: DebugSettingsManager = RealDebugSettingsManager(storage)
debug.setShouldShowDebug(true)
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `:cbox:common:debug:api` (api), `sage.common.storage.common` (impl)
