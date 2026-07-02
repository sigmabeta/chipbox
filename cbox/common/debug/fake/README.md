# `:cbox:common:debug:fake`

> In-memory `DebugSettingsManager` test double with per-toggle flows and call recording.

The `:fake` module for Chipbox debug settings: `FakeDebugSettingsManager` backs
each toggle and `*Source` from `:cbox:common:debug:api` with a `MutableStateFlow`
tests can drive directly, and records every `set*` call so a test can assert a
ViewModel toggled the right value. For tests and previews only.

## Contents

| File | What it is |
| --- | --- |
| `FakeDebugSettingsManager.kt` | Implements `DebugSettingsManager` over one `MutableStateFlow` per setting. Constructor takes seed values (defaulting to each enum's `DEFAULT` and `shouldShowDebug = false`). Each `set*` writes the flow and appends to a `set*Calls` list for assertions. |

## Why depend on this module

Depend on `:cbox:common:debug:fake` from test source sets (and UI-test fakes)
that need a controllable `DebugSettingsManager` without `Storage`. Production code
depends on `:api` and is wired to `:real` via `:di`.

## Using it

```kotlin
val debug = FakeDebugSettingsManager(initialShouldShowDebug = true)
viewModel.onSelectWavOutput()
assertEquals(listOf(SpeakerSource.FILE), debug.setSpeakerSourceCalls)
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `:cbox:common:debug:api` (api), `kotlinx-coroutines-core` (impl)
