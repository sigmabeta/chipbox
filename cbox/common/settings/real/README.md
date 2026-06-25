# `:cbox:common:settings:real`

> Production `ChipboxSettingsManager` — settings persisted through SAGE `Storage`.

The `:real` module for Chipbox settings: `RealChipboxSettingsManager` backs each
setting from `:cbox:common:settings:api` with a key/value entry in the shared
SAGE `Storage`, exposing reactive `savedStringFlow` reads and `saveString`
writes. This is the production implementation the app runs against.

## Contents

| File | What it is |
| --- | --- |
| `RealChipboxSettingsManager.kt` | Implements `ChipboxSettingsManager` over `Storage`. Enum settings persist by `.name` and parse back via `fromStorageValue`; the boolean toggles (`shuffleSkipsShortTracks`, `volumeNormalizationEnabled`, `fadeInEnabled`) default to `true` when unset. Storage keys are `const`s in its companion (`setting.*`). |

## Why depend on this module

You normally do **not** depend on `:real` directly — depend on
`:cbox:common:settings:api` for the interface and let `:cbox:common:settings:di`
bind this implementation into `AppScope`. Depend on `:real` only when you need to
construct `RealChipboxSettingsManager` yourself (e.g. a custom entry point) and
supply a `Storage`.

## Using it

```kotlin
val settings: ChipboxSettingsManager = RealChipboxSettingsManager(storage)
settings.setResamplerMode(ResamplerMode.LINEAR)
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `:cbox:common:settings:api` (api), `sage.common.storage.common` (impl)
