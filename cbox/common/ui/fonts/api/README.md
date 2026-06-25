# `:cbox:common:ui:fonts:api`

> Pure-Kotlin metadata for the 18 Chipbox pixel-art fonts — name, description, URL. No Compose.

A single enum, `ChipboxFont`, carrying display metadata for each game-typeface font.
This is the `:api` (public types) module and is deliberately **Compose-free**: the
actual Compose binding (`Res.font.*` accessors, `FontResource` lookup, `toFontFamily()`)
lives in the sibling `:real` module.

## Contents

| File | What it is |
| --- | --- |
| `ChipboxFont.kt` | `enum class ChipboxFont(fontName, description, url)` — 18 entries (e.g. `PLANETARY`, `METEOR`, `DOUBLE_TOUCH`, `DYSLEXIC`). Companion holds `DEFAULT_BRAND`/`DEFAULT_PLAIN` and `fromStorageValue(value, default)` for persistence round-tripping. |

## Why depend on this module

Depend on `:cbox:common:ui:fonts:api` wherever a `ChipboxFont` is only *carried* —
through actions, state, or persistence — without being rendered: e.g.
`SettingsAction.BrandFontSelected`, `ChipboxAppUiViewModel`, settings storage.

The api/real split exists to keep Compose-Resources (and, on Kotlin/JS, Skiko) off
those consumers' classpath. Loading the Compose-Resources runtime triggers Skiko at
class-init time, which doesn't resolve under Node and would break `jsTest`. Only modules
that actually render a font in Compose UI need `:real`.

## Using it

```kotlin
// Carry a selection through state / actions:
data class SettingsState(val brandFont: ChipboxFont = ChipboxFont.DEFAULT_BRAND)

// Round-trip a persisted value:
val font = ChipboxFont.fromStorageValue(
    value = prefs.getString("brand_font"),
    default = ChipboxFont.DEFAULT_BRAND,
)
val label = "${font.fontName}: ${font.description}"
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` (no `sage.compose.kmp` — pure Kotlin)
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** none (leaf)
