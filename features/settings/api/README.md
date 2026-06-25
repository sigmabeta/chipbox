# `:features:settings:api`

> The settings-screen surface: route key, action hierarchy, and the state→`ListModel` renderer.

The `:api` half of the `settings` feature. Unlike most features (which keep
`State`/`Action` in `:real`), `settings` puts its whole UI-model surface here: the
Voyager route key, the full `SettingsAction` hierarchy, and `SettingsState` — the
immutable state that renders every settings row. All `commonMain`, so the desktop
UI builds the same screen from the same types the Android UI does. Only
`SettingsViewModel` (which needs the runtime managers) lives in `:real`.

## Contents

| File | What it is |
| --- | --- |
| `Settings.kt` | `@Serializable data object Settings` — the route key. |
| `SettingsAction.kt` | `sealed class SettingsAction : ChipboxAction()` — every interaction: dropdown expand, theme/resampler/font/debug-source selections, the audio toggles, library actions (add folder, manage, rescan, clear), about-section taps (licenses, build-date, GitHub), and the debug navigation rows (playback status, error log, crash log, component library). |
| `SettingsState.kt` | `SettingsState : ListState` — holds all persisted/derived settings values and renders them into sections (appearance, audio, library, about, and a `shouldShowDebug`-gated debug section) via `toListItems`. Encodes the row logic: single-open-dropdown, rescan inline-spinner vs. status-row, add-folder-vs-manage-library swap, `LCE` loading rows for clear operations. |

## Why depend on this module

Depend on `:features:settings:api` for the route key (to navigate to or register
the screen), for the `SettingsAction` types (the `:real` ViewModel and any tests
dispatch them), and for `SettingsState` (the renderer, shared by both platforms
and exercised in screenshot/UI tests). The app wires the ViewModel via
`:features:settings:real`.

## Using it

```kotlin
// Render the screen's rows from a state instance (as ChipboxListEntry does):
val rows: List<ListModel> = SettingsState(themeMode = ThemeMode.DARK)
    .toListItems(stringProvider)

// Navigate to the screen:
emit(ChipboxEvent.NavigateTo(Settings))
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `kotlin.serialization` (raw KMP plugins, not `feature.api` — this module declares its own dependencies and an Android namespace)
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `sage.common.appcomm`, `sage.common.list`, `sage.common.appinfo`, `sage.common.ui.components`, `sage.common.ui.strings`; `:cbox:common:appcomm:api`, `:cbox:common:settings:api`, `:cbox:common:debug:api`, `:cbox:common:strings:api`, `:cbox:common:ui:fonts:api`; `kotlinx.serialization.core`, `kotlinx.collections.immutable`
