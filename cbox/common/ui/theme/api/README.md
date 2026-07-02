# `:cbox:common:ui:theme:api`

> The shared Chipbox Compose theme — color schemes, typography tokens, and the multiplatform `ChipboxTheme()` / `AppTheme()` entry points.

The `:api` module holding Chipbox's Material3 theme: the light/dark color
schemes, the type-scale + typography tokens and the `Typography` builder, and the
composable theme wrappers used by both the shared `ChipboxAppUi` (Android +
desktop) and Compose previews. Pure Compose, no Android-platform dependencies in
`commonMain`. Being `:api`, it is the public theme surface other UI modules
consume directly.

## Contents

**Theme entry points** — `AppTheme.kt` and `ChipboxTheme.kt`.
`AppTheme(brand, plain, fontScale, darkTheme, swapPrimaryAndSecondary, content)`
is the `ChipboxFont`-typed convenience: it converts the picked `ChipboxFont`s to
`FontFamily`s via `ChipboxFont.toFontFamily()` (hence the dep on `:fonts:real`,
not `:api`) and delegates to `ChipboxTheme`. `ChipboxTheme(brand, plain, scale,
darkTheme, swapPrimaryAndSecondary, content)` is the raw-`FontFamily` Material3
wrapper: `darkTheme` is `true`/`false`/`null` (follow OS via
`isSystemInDarkTheme()`); it recolors the ripple state-layer to `colors.primary`
and builds typography via `buildChipboxTypography`. **`swapPrimaryAndSecondary`**
is a debug-build marker — `ChipboxAppUi` sets it to `AppInfo.isDebug` so debug
(and the always-debug desktop/web) builds are instantly distinguishable.

**Color palette** — `Colors.kt`. The full Material3 token set as `Color` `val`s
across light/dark × default/medium-contrast/high-contrast, assembled into the
`ChipboxLight` (`lightColorScheme`) and `ChipboxDark` (`darkColorScheme`)
schemes. Also `ColorScheme.withPrimarySecondarySwapped()` — exchanges the
primary ↔ secondary *roles* (with on-colors and containers, so text stays
legible), driving `swapPrimaryAndSecondary`.

**Typography + tokens** — `Typography.kt` plus `tokens/`. `buildChipboxTypography(brand,
plain, scale)` produces a Material3 `Typography`, copying each slot from
`ChipboxTypographyTokens` and overriding family/size/line-height; `brand` is used
for display/headline/title-large, `plain` for the rest, and `scale` multiplies
sizes and line heights uniformly. `tokens/` holds `ChipboxTypeScaleTokens`
(sizes/weights/line-heights/tracking — the `*Font` members are placeholders
always overridden), `ChipboxTypographyTokens` (per-slot `TextStyle`s),
`ChipboxTypefaceTokens` (`FontWeight`s), and `ChipboxFontDefaults`
(`Brand = ChipboxFont.DEFAULT_BRAND`, `Plain = ChipboxFont.DEFAULT_PLAIN`,
public so `apps/jvm` can reach them).

**Platform default text style** — `tokens/ChipboxDefaultTextStyle.{android,jvm,js}.kt`,
the `actual`s for the `expect fun chipboxDefaultTextStyle(): TextStyle` declared
in `ChipboxTypographyTokens.kt`. Android returns `TextStyle.Default` with
`PlatformTextStyle(includeFontPadding = false)` (suppresses the legacy Android
font-padding default); JVM and JS have no such knob, so both return plain
`TextStyle.Default`. (Declared as a function, not an `expect val`, because
`expect val` is still Beta.)

## Why depend on this module

Depend on `:cbox:common:ui:theme:api` to wrap your Compose UI in the Chipbox
theme or to reach the schemes/tokens directly. The app shells call `AppTheme {}`
(or `ChipboxTheme {}` on JVM with `FontFamily.Default`); previews and screenshot
tests use the same entry points so goldens match production styling.

## Using it

```kotlin
AppTheme(darkTheme = userPickedDark, swapPrimaryAndSecondary = AppInfo.isDebug) {
    // ...your app content; MaterialTheme.colorScheme / .typography are now Chipbox's...
}
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `sage.compose.kmp`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (theme, colors, typography, tokens) with `androidMain` / `jvmMain` / `jsMain` `actual`s for `chipboxDefaultTextStyle()`.
- **SAGE/module dependencies:** `:cbox:common:ui:fonts:real` (api — for `ChipboxFont` + `toFontFamily()`). No SAGE libs.
