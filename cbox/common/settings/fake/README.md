# `:cbox:common:settings:fake`

> In-memory `ChipboxSettingsManager` test double with per-setting flows and call recording.

The `:fake` module for Chipbox settings: `FakeChipboxSettingsManager` backs each
setting from `:cbox:common:settings:api` with a `MutableStateFlow` tests can drive
directly, and records every `set*` call so a test can assert the ViewModel under
test dispatched the right write. For tests and previews only.

## Contents

| File | What it is |
| --- | --- |
| `FakeChipboxSettingsManager.kt` | Implements `ChipboxSettingsManager` over one `MutableStateFlow` per setting. Constructor takes seed values (defaulting to production defaults — `ThemeMode.DEFAULT`, `null` fonts, `ResamplerMode.DEFAULT`, the booleans `true`). Each `set*` writes the flow and appends to a `set*Calls` list for assertions. |

## Why depend on this module

Depend on `:cbox:common:settings:fake` from test source sets (and UI-test fakes)
that need a controllable `ChipboxSettingsManager` without `Storage`. Production
code depends on `:api` and is wired to `:real` via `:di`.

## Using it

```kotlin
val settings = FakeChipboxSettingsManager(initialThemeMode = ThemeMode.DARK)
viewModel.onPickLightTheme()
assertEquals(listOf(ThemeMode.LIGHT), settings.setThemeModeCalls)
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `:cbox:common:settings:api` (api), `kotlinx-coroutines-core` (impl)
