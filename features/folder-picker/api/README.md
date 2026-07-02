# `:features:folder-picker:api`

> The Voyager route key for the in-app folder picker screen — depend on it to navigate there.

The public surface other features import to push the in-app folder picker. This
is the `:api` half of a feature-screen split: it exports only the
`@Serializable` route-key destination, so navigation callers stay decoupled from
the screen's implementation (the ViewModel, Compose `Route`, and filesystem
helpers all live in `:real`).

## Contents

| File | What it is |
| --- | --- |
| `FolderPicker.kt` | `@Serializable data object FolderPicker` — the Voyager route key for the picker screen. |

## Why depend on this module

Depend on `:features:folder-picker:api` when a screen needs to navigate to the
in-app folder browser. It's a tiny, near-leaf module: it carries the route key
and nothing heavy, so depending on it doesn't pull in Compose, Metro, or any
filesystem code. `SettingsRoute` (JVM/desktop) and `ManageLibraryRoute` push it,
and `HomeViewModel` references it; `ChipboxScreens.kt` in `cbox/common/appui/api`
maps the `FolderPicker` key to its `Screen`. The app wires the actual screen by
including `:features:folder-picker:real` (whose `expect`/`actual` `FolderPickerRoute`
the appui module renders for the mapped `Screen`).

## Using it

`FolderPicker` is a destination value passed to navigation — typically emitted as
a `ChipboxEvent.NavigateTo`:

```kotlin
import net.sigmabeta.chipbox.features.folderpicker.FolderPicker

// from a ViewModel handling an action:
emit(ChipboxEvent.NavigateTo(FolderPicker))
```

`ChipboxScreens.screenFor(FolderPicker)` then resolves it to the Voyager `Screen`
that hosts `FolderPickerRoute`.

## Module facts

- **Plugin:** `chipbox.feature.api`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js` (the `feature.api` plugin transitively applies `sage.kmp.js`)
- **Source set:** `commonMain`
- **SAGE/module dependencies:** none (leaf; only `kotlinx.serialization` from the plugin)
