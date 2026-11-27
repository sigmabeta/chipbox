# `:features:component-library:api`

> The route key and list state for the debug-only component gallery.

The `:api` half of the `component-library` feature. The catalog itself lives in SAGE
(`:common:ui:component-library`): every built-in renderer in list, grid and detail-style layouts,
live style controls, and the loading/empty/error states. Chipbox only supplies its own cards.
`:api` modules hold only types and stay KMP so the desktop UI builds the same screen.

## Contents

| File | What it is |
| --- | --- |
| `ComponentLibrary.kt` | `@Serializable data object ComponentLibrary` — the route key (reached from Settings' debug section, gated behind `shouldShowDebug`). |
| `real/ComponentLibraryState.kt` | `ComponentLibraryState(config) : ListState` — holds SAGE's `ComponentLibraryConfig`, reports `config.layout.columnType`, and returns `componentLibraryItems(config, chipboxSampleCards())`. |
| `real/ChipboxSampleCards.kt` | `chipboxSampleCards()` — Chipbox's own `ListModel`s for the gallery's "App rows" section: `NowPlayingHomeCardListModel` (playing, paused, buffering, error) and `ScanStatusCardListModel` (scanning, complete, failed). Plain English text (debug-only), synthetic artwork (the host forces preview art), ids from `componentLibraryExtraId`. |

Gallery actions are SAGE's `ComponentLibraryAction`; there is no Chipbox action type.

## Why depend on this module

Depend on `:features:component-library:api` to navigate to the gallery (e.g. `:features:settings:real`
emits `ChipboxEvent.NavigateTo(ComponentLibrary)`) or to map the key to a `Screen` in
`ChipboxScreens.kt`. `:features:component-library:real` implements the screen.

## Using it

```kotlin
emit(ChipboxEvent.NavigateTo(ComponentLibrary))
```

## Module facts

- **Plugin:** `chipbox.feature.api`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **Dependencies:** `libs.sage.common.ui.component.library` (re-exported), `:cbox:common:ui:components:api` (the Home cards), plus the public SAGE types the state exposes.
