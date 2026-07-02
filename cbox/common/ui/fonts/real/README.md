# `:cbox:common:ui:fonts:real`

> The Compose binding for `ChipboxFont` — the 18 `.otf` assets plus the `Res.font.*`
> accessors and `toFontFamily()` extension.

The production (`:real`) implementation behind the `:api` enum: it holds the actual font
files and turns a `ChipboxFont` value into a Compose `FontFamily`. Apply this module only
where Compose UI is in scope (e.g. `cbox/common/ui/theme/api`'s `AppTheme`); everything
that merely carries a `ChipboxFont` through state/actions should depend on `:api` alone.

## Contents

| File / resource | What it is |
| --- | --- |
| `ChipboxFontBindings.kt` | `ChipboxFont.resource: FontResource` (maps each enum entry to its generated `Res.font.*`) and `@Composable ChipboxFont.toFontFamily(): FontFamily`. |
| `src/commonMain/composeResources/font/*.otf` | The 18 game-typeface fonts, loaded via Compose-Resources codegen: `earthbound`, `enix`, `final_fantasy_iv`/`_vi`/`_vii`, `mega_man_x`, `nds`, `nes`, `open_dyslexic`, `ps4`, `shining_force_lg`/`_sm`, `shinobi`, `sonic1`/`sonic3`, `star_fox_64`, `super_mario_64`, `tloz_lttp`. |

## Why depend on this module

Depend on `:cbox:common:ui:fonts:real` only from modules that render text with a brand
font — i.e. where a `FontFamily` is actually needed. It `api`-exports `:cbox:common:ui:fonts:api`,
so taking `:real` also gives you the `ChipboxFont` enum. Keeping it separate from `:api`
keeps the Compose-Resources runtime (and Skiko on Kotlin/JS) off the classpath of
consumers that only pass a `ChipboxFont` around.

## Using it

```kotlin
@Composable
fun BrandText(font: ChipboxFont, text: String) {
    Text(text = text, fontFamily = font.toFontFamily())
}

// Or grab the raw resource:
val resource: FontResource = ChipboxFont.METEOR.resource
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `sage.compose.kmp` + `compose.multiplatform`
  (the JetBrains Compose Gradle plugin, for `Res.font.*` codegen)
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (Kotlin + `composeResources/font/`)
- **SAGE/module dependencies:** `:cbox:common:ui:fonts:api` (`api`),
  `libs.jetbrains.compose.resources` (`api`)

### Build notes

- `androidResources { enable = true }` — AGP 9's KMP library plugin ships Android
  resource/asset processing off by default; enabling it is what packages the `.otf`
  files into the APK's `assets/composeResources/.../font/` (avoids `MissingResourceException`,
  CMP-9547).
- A `composeResourcesElementsJar` task exposes the prepared `composeResources` as a
  classpath-shaped jar so the screenshot (Paparazzi) modules can load the fonts off the
  JVM unit-test classpath (CMP packages them as Android assets only).
