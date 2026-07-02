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
- **SAGE/module dependencies:** none (leaf; only `kotlinx.serialization` from the plugin)
