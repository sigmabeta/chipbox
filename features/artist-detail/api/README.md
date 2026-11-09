# `:features:artist-detail:api`

> The Artist Detail route key — the typed destination other features navigate to.

The public surface of the artist-detail feature: a single Voyager route key
carrying the artist's database id. As the `:api` module it holds only types other
modules need at compile time — no implementation — so features can navigate to the
screen without depending on the screen itself.

## Contents

| File | What it is |
| --- | --- |
| `ArtistDetail.kt` | `@Serializable data class ArtistDetail(val id: Long)` — the Chipbox screen destination for one artist, identified by DB id. |
| `ArtistDetailState.kt` | `ArtistDetailState : ListState()` — LCE-wrapped `artist`/`tracks`/`games`, `playingTrackId`, `isFavorite`, `notFound`. Builds the hero image, CTA rows (play / shuffle / favorite toggle / add-to-playlist), a games `HorizontalScroller`, and a songs section; shows an empty state when `notFound`. Uses `ColumnType.Staggered(320dp)`. The songs section renders as **one non-lazy column** (so it can move between grid columns as a single unit), so it renders at most `SONGS_PREVIEW_COUNT` (50) rows and appends a "View all N songs" CTA when the artist has more — this is the fix for the main-thread freeze on prolific composers (see `arch-docs/artist-detail-loading-stall.md`). |
| `ArtistDetailAction.kt` | `ArtistDetailAction : ChipboxAction()` — `TrackClicked(position)`, `GameClicked(id)`, `PlayAllClicked`, `ShuffleAllClicked`, `AddToFavoritesClicked`, `AddToPlaylistClicked`, `ViewAllSongsClicked`. |

## Why depend on this module

Depend on `:api` when you need to navigate to the artist-detail screen but not
render it. Browse-by-artist and game-detail both depend on `:api` only to emit
`NavigateTo(ArtistDetail(id))`; the app wires `:real` to actually show the screen.
Keeping the route key in a leaf `:api` module avoids a dependency cycle between
features that link to each other.

## Using it

```kotlin
// From another feature's ViewModel: jump to a specific artist.
emit(NavigateTo(ArtistDetail(id = artist.id)))
```

## Module facts

- **Plugin:** `chipbox.plugins.feature.api`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** the public dependencies of the State/Action types, declared in `build.gradle.kts`; `kotlinx.serialization` comes from the convention plugin
