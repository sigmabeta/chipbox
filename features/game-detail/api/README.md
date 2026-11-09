# `:features:game-detail:api`

> The navigation route key for the game-detail screen.

The public surface of the game-detail feature: a single `@Serializable` Voyager
route key carrying the game's database id. As an `:api` module it holds only the
types other features need to navigate here — the screen implementation lives in
`:features:game-detail:real`.

## Contents

| File | What it is |
| --- | --- |
| `GameDetail.kt` | `@Serializable data class GameDetail(val id: Long)` — the route key / destination for the game-detail screen, carrying the game's DB id. |
| `GameDetailAction.kt` | Sealed `GameDetailAction : ChipboxAction()`: `TrackClicked(position)`, `ArtistClicked(id)`, `PlayAllClicked`, `ShuffleAllClicked`, `AddToFavoritesClicked`, `AddToPlaylistClicked`. |
| `GameDetailState.kt` | `GameDetailState : ListState()` holding `LCE<Game>`/`LCE<List<Track>>`/`LCE<List<Artist>>` plus `playingTrackId`, `isFavorite`, `notFound`. Builds the hero image, CTA rows, a read-only "Details" section of `LabelValue` rows (release date / genre / copyright / Japanese title — only present fields render; section hidden when none), an artists `HorizontalScroller`, and the songs section. Songs collapse to label/value rows for a single-artist game, name+caption rows when multiple; shows an empty state on `notFound`. Column type `Staggered(320dp)`. |

## Why depend on this module

Depend on `:api` whenever a feature needs to navigate to the game-detail screen
without pulling in its implementation. Several features already do — `artist-detail`,
`browse-by-game`, and `games-for-platform` each emit `NavigateTo(GameDetail(id))`.
The app wires the actual screen via `:real`; only `:real` should depend on it.

## Using it

```kotlin
// From another feature's ViewModel: open the game-detail screen for a given game.
emit(NavigateTo(GameDetail(id = game.id)))
```

## Module facts

- **Plugin:** `chipbox.plugins.feature.api`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** the public dependencies of the State/Action types, declared in `build.gradle.kts`; `kotlinx.serialization` comes from the convention plugin
