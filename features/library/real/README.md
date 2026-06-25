# `:features:library:real`

> The Library tab screen — a static six-item navigation menu into the rest of the library.

The implementation of the Library feature screen: a `ChipboxListViewModel`, its
`ListState`, its `Action` type, and the Compose `Route`. As a feature `:real`
module it's the code the app includes to actually render and run the screen. The
ViewModel is pure-navigation — it holds no data, just maps each menu tap to a
`NavigateTo` event for a sub-screen (Favorites, Playlists, Browse-by-*, All
Tracks).

## Contents

| File | What it is |
| --- | --- |
| `LibraryState.kt` | `data object LibraryState : ListState` — single-column state that renders the six static `IconNameListModel` menu rows (title + icon + click action) and the "Library" title bar. |
| `LibraryAction.kt` | `sealed class LibraryAction : ChipboxAction` — the six menu click actions (`FavoritesClicked`, `PlaylistsClicked`, `BrowseByGameClicked`, `BrowseByPlatformClicked`, `BrowseByArtistClicked`, `BrowseAllTracksClicked`). |
| `LibraryViewModel.kt` | `@Inject` `ChipboxListViewModel<LibraryState>`, Metro-contributed via `@ContributesIntoMap(AppScope)` + `@ViewModelKey`; `handleAction` maps each action to `NavigateTo(<destination route key>)`. |
| `LibraryRoute.kt` | `@Composable LibraryRoute(onEvent, modifier)` — resolves the VM with `metroViewModel()` and hands it to `ChipboxListEntry`. |

## Why depend on this module

Depend on `:features:library:real` only from the app wiring (`apps/android`,
`apps/jvm`) so the Library screen's ViewModel is contributed into the Metro graph
and `LibraryRoute` is available to `ChipboxScreens.kt`. Everything else should
depend on `:features:library:api` for the route key. This module `api`-exposes
`:features:library:api` and depends on each sub-screen's `:api`
(`favorites`, `playlists`, `browse-all-tracks`, `browse-by-artist`,
`browse-by-game`, `browse-by-platform`) purely for their route keys.

## Using it

```kotlin
// Registered in cbox/common/appui/api/.../ChipboxScreens.kt:
@Composable
fun LibraryRoute(onEvent: (ChipboxEvent) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: LibraryViewModel = metroViewModel()
    ChipboxListEntry(viewModel, onEvent, modifier)
}

// A menu tap dispatches an action; the VM turns it into navigation:
override fun handleAction(action: SageAction) {
    when (action) {
        LibraryAction.FavoritesClicked -> emit(NavigateTo(Favorites))
        // ...one arm per menu row
    }
}
```

## Module facts

- **Plugin:** `chipbox.feature.real` + `chipbox.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js` (`feature.real` transitively applies `sage.kmp.js`)
- **Source set:** `commonMain` (state/action/VM/route all pure-Kotlin); tests in `commonTest`
- **SAGE/module dependencies:** `:features:library:api` (api); `:cbox:common:ui:list:api`, `:cbox:common:appcomm:api`, `:cbox:common:strings:api`; route keys from `:features:favorites:api`, `:features:playlists:api`, `:features:browse-all-tracks:api`, `:features:browse-by-artist:api`, `:features:browse-by-game:api`, `:features:browse-by-platform:api`
