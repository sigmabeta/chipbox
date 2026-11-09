# `:features:artist-detail:real`

> The Artist Detail screen — ViewModel and Compose route.

> The screen's `State`/`Action` value types live in `:features:artist-detail:api`; this module holds the ViewModel, Route and runtime helpers.

The implementation of the artist-detail feature: a `ChipboxListViewModel` that
loads one artist (with its tracks and games) and the Compose entry point. As the `:real` module it provides the actual
screen; the app includes it so `ArtistDetail` route keys (from `:api`) can render.

## Contents

| File | What it is |
| --- | --- |
| `ArtistDetailViewModel.kt` | `@AssistedInject ChipboxListViewModel<ArtistDetailState>` — loads the artist, observes `director.metadataState()` for the playing track and `favorites.isArtistFavorite`; `handleAction` starts `ARTIST` sessions via `Director`, toggles favorite, and navigates to `GameDetail` / the `Playlists` picker / `TracksForArtist` (the view-all CTA). Nested `@AssistedFactory @ContributesIntoMap(AppScope) Factory`. |
| `ArtistDetailRoute.kt` | `@Composable ArtistDetailRoute(artistId, onEvent, modifier)` — resolves the VM via `assistedMetroViewModel { create(artistId) }` and renders `ChipboxListEntry`. |
| `ArtistDetailViewModelTest.kt` | `commonTest` coverage of the VM (Data → LCE fan, navigation, sessions). |

## Why depend on this module

The app depends on `:real` to make the screen available; it is registered in
`ChipboxScreens.kt` as `ArtistDetailDeepScreen(artistId)` under the key
`"ArtistDetail:$artistId"`. Other features should depend on `:api` (the route
key) and let the app wire `:real` — don't depend on `:real` just to navigate.

## Using it

```kotlin
// Inside ChipboxScreens.kt, mapping the ArtistDetail route key to its Compose UI:
ArtistDetailRoute(artistId = destination.id, onEvent = onEvent)

// Elsewhere, navigating here only needs the :api route key:
emit(NavigateTo(ArtistDetail(id = 42L)))
```

## Module facts

- **Plugin:** `chipbox.plugins.feature.real` + `chipbox.plugins.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (+ `commonTest`)
- **SAGE/module dependencies:** `api` `:features:artist-detail:api`; implementation `:cbox:common:ui:list:api`, `:cbox:common:appcomm:api`, `:cbox:common:strings:api`, `:cbox:common:repository:api`, `:cbox:common:favorites:api`, `:cbox:common:models:api`, `:cbox:common:player:common:api`, `:cbox:common:player:director:api`, `:features:game-detail:api`, `:features:playlists:api`, `:features:tracks-for-artist:api`. Tests: `:cbox:common:repository:fake`, `:cbox:common:favorites:fake`, `:cbox:common:player:director:fake`.
