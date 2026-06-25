# `:features:browse-by-artist:api`

> The Voyager route key for the Browse-by-Artist grid.

The `:api` half of the Browse-by-Artist feature: a single parameterless route
key that other modules navigate to. Being the `:api` module, it carries only the
public type needed to reach the screen — the implementation lives in `:real`.

## Contents

| File | What it is |
| --- | --- |
| `BrowseByArtist.kt` | `@Serializable data object BrowseByArtist` — the parameterless Voyager route key, in package `net.sigmabeta.chipbox.features.browsebyartist`. |

## Why depend on this module

Depend on `:api` when you need to *navigate to* the artist grid without pulling
in the screen implementation — e.g. the Library tab references
`BrowseByArtist` as a navigation target. The app wires the actual screen via
`:real`; `cbox/common/appui` maps this key to its `Screen` in `ChipboxScreens.kt`.
Because the route takes no arguments, there's nothing to construct beyond the
object itself.

## Using it

```kotlin
onEvent(ChipboxEvent.NavigateTo(BrowseByArtist))
```

## Module facts

- **Plugin:** `chipbox.plugins.feature.api`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** none (leaf; only `kotlinx.serialization`)
