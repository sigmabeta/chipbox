# `:features:library:api`

> The Library tab's Voyager route key — the typed destination other features navigate to.

The public surface of the Library feature screen. As a feature `:api` module it
exports the `@Serializable` route key and the screen's State/Action types, so any feature can navigate to
the Library tab without depending on its implementation.

## Contents

| File | What it is |
| --- | --- |
| `Library.kt` | `@Serializable data object Library` — the Voyager route key for the Library tab. |
| `LibraryState.kt` | `data object LibraryState : ListState` — single-column state that renders the six static `IconNameListModel` menu rows (title + icon + click action) and the "Library" title bar. |
| `LibraryAction.kt` | `sealed class LibraryAction : ChipboxAction` — the six menu click actions (`FavoritesClicked`, `PlaylistsClicked`, `BrowseByGameClicked`, `BrowseByPlatformClicked`, `BrowseByArtistClicked`, `BrowseAllTracksClicked`). |

## Why depend on this module

Depend on `:features:library:api` when you need to navigate to the Library screen
(emit `ChipboxEvent.NavigateTo(Library)`) without pulling in the screen's
ViewModel/Compose impl. `cbox/common/appui/api/.../ChipboxScreens.kt` depends on
it to map the route key to a Voyager `Screen`; the app wires the actual screen via
`:features:library:real`.

## Using it

```kotlin
import net.sigmabeta.chipbox.features.library.Library
import net.sigmabeta.chipbox.appcomm.ChipboxEvent.NavigateTo

// from any ViewModel's handleAction:
emit(NavigateTo(Library))
```

## Module facts

- **Plugin:** `chipbox.feature.api`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js` (`feature.api` transitively applies `sage.kmp.js`)
- **Source set:** `commonMain`
- **SAGE/module dependencies:** the public dependencies of the State/Action types, declared in `build.gradle.kts`; `kotlinx.serialization` comes from the convention plugin
