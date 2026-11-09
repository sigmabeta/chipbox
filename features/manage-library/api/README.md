# `:features:manage-library:api`

> The navigation key for the "manage library folders" screen.

The public surface of the manage-library feature: the Voyager route key and the screen's State/Action types.
This is the `:api` module — it holds the `@Serializable` destination that other
features depend on to navigate here, without pulling in the screen's
implementation.

## Contents

| File | What it is |
| --- | --- |
| `ManageLibrary.kt` | `@Serializable data object ManageLibrary` — the route key. `cbox/common/appui/api`'s `screenFor(...)` maps it to the Voyager `ManageLibraryScreen`. |
| `ManageLibraryState.kt` | `ManageLibraryState(folders) : ListState` — the "Add folder" CTA plus one row per library folder (or an empty state). |
| `ManageLibraryAction.kt` | `ManageLibraryAction : ChipboxAction` — `AddFolderClicked`, `FolderPicked(uri)`, `FolderClicked(identifier)`. |

## Why depend on this module

Depend on `:features:manage-library:api` when you need to navigate to the
manage-library screen (e.g. via `ChipboxEvent.NavigateTo(ManageLibrary)`) but
don't want a compile dependency on the screen itself. The app wires the screen
implementation via `:features:manage-library:real`; everyone else only needs the
key.

## Using it

```kotlin
import net.sigmabeta.chipbox.features.managelibrary.ManageLibrary

// Anywhere with a ChipboxEvent sink:
onEvent(ChipboxEvent.NavigateTo(ManageLibrary))
```

## Module facts

- **Plugin:** `chipbox.plugins.feature.api`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js` (the
  `feature.api` plugin transitively applies `sage.kmp.js`)
- **Source set:** `commonMain`
- **SAGE/module dependencies:** the public dependencies of the State/Action types, declared in `build.gradle.kts`; `kotlinx.serialization` comes from the convention plugin
