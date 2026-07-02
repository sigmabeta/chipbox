# `:features:component-library:api`

> Route keys for the debug-only in-app gallery of reusable Chipbox UI components.

The `:api` half of the `component-library` feature: it exports the Voyager route
keys (`@Serializable` destinations) and the `LibraryMode` enum that other modules
reference to navigate to the gallery. `:api` modules hold only types — interfaces,
route keys, value enums — and stay KMP so the desktop UI builds the same screen.

## Contents

| File | What it is |
| --- | --- |
| `ComponentLibrary.kt` | `@Serializable data object ComponentLibrary` — the landing-menu route key (reached from Settings' debug section, gated behind `shouldShowDebug`). |
| `ComponentLibraryMode.kt` | `@Serializable data class ComponentLibraryMode(val mode: LibraryMode)` — a single parameterized destination backing all three gallery modes; `mode` is threaded into the ViewModel via assisted injection. |
| `LibraryMode.kt` | `@Serializable enum class LibraryMode { LIST, GRID, COLUMNS }` — which layout the gallery renders. |

## Why depend on this module

Depend on `:features:component-library:api` when you need to navigate to the
gallery (e.g. `:features:settings:real` emits `ChipboxEvent.NavigateTo(ComponentLibrary)`)
or to map the route key to a `Screen` in `ChipboxScreens.kt`. The app wires the
implementation via `:features:component-library:real`, which itself depends on
this module for the route keys and `LibraryMode`.

## Using it

```kotlin
// Open the gallery menu from another screen's ViewModel:
emit(ChipboxEvent.NavigateTo(ComponentLibrary))

// Open a specific mode directly:
emit(ChipboxEvent.NavigateTo(ComponentLibraryMode(LibraryMode.GRID)))
```

## Module facts

- **Plugin:** `chipbox.feature.api`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js` (the `feature.api` plugin transitively applies `sage.kmp.js`)
- **Source set:** `commonMain`
- **SAGE/module dependencies:** none (leaf — only `kotlinx.serialization`, provided by the plugin)
