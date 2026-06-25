# `:features:browse-by-game:api`

> Route key for the Browse-by-Game screen.

The public contract of the browse-by-game feature: a single Voyager route key
that other modules navigate to. As the `:api` half of the api/real split, it
carries only the type — the screen implementation lives in `:real` — so callers
can target the destination without depending on the (much heavier) screen code.

## Contents

| File | What it is |
| --- | --- |
| `BrowseByGame.kt` | `@Serializable data object BrowseByGame` — the parameterless route key for the screen. |

## Why depend on this module

Depend on `:features:browse-by-game:api` when you need to navigate to the
browse-by-game screen — e.g. the Library tab references `BrowseByGame` as a
navigation destination. It's a leaf with no SAGE/module dependencies beyond
`kotlinx.serialization`, so depending on it costs nothing. The app wires the
actual screen via `:real` (registered in `ChipboxScreens.kt`); you only need
`:api` to point at it.

## Using it

```kotlin
// Navigate to the screen by emitting its route key.
onEvent(NavigateTo(BrowseByGame))
```

## Module facts

- **Plugin:** `chipbox.plugins.feature.api`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** none (leaf; only `kotlinx.serialization`)
