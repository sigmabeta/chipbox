# `:cbox:android:ui:theme:api`

> The Android theme surface — `AppTheme`, Material3 XML theme/colors, and font previews.

Android system glue (`:api`): the Android-side theming entry point. Re-exports the cross-platform
`AppTheme` and Chipbox color schemes (`:cbox:common:ui:theme:api`) plus the Compose Material3 /
Material Components dependencies and the platform XML theme + color resources every Android UI
needs. The `:api` role here is "the theme API + Android resources other UI modules build against".

## Contents

| File | What it is |
| --- | --- |
| `FontList.kt` | `@Preview` (`private`) rendering each `ChipboxFont` at `titleLarge`/`bodyMedium` over a long sample passage, driven by a `PreviewParameterProvider`. |
| `FontPreview.kt` | `@Preview`s (`private`) listing every `ChipboxFont` with name + description, light/dark. |
| `res/values/colors.xml` | The Material3 light + dark color palette (`md_theme_light_*` / `md_theme_dark_*`, purple seed). |
| `res/values/themes-base.xml` | `ChipboxBaseTheme` (`Theme.Material3.DayNight.NoActionBar`) + bottom-sheet styles. |
| `res/values/themes.xml`, `res/values-night/themes.xml` | `ChipboxAppTheme` binding the light / dark color attributes. |

`AppTheme` itself lives in the shared `:cbox:common:ui:theme:api` module and is re-exported via
`api()` so callers resolve it through this module's classpath.

## Why depend on this module

Android UI modules depend on this for `AppTheme` (the Compose theme wrapper), the
`ChipboxAppTheme` XML theme (set on the `Activity`), and the shared color schemes. It also pins
the Compose BOM, Material3, and Material Components so dependents don't re-declare them.

## Using it

```kotlin
AppTheme(darkTheme = isSystemInDarkTheme()) {
    // screen content
}
```

## Module facts

- **Plugin:** `sage.android` + `sage.compose.android`
- **Targets:** Android only
- **Source set:** `src/main/java` + Android `res/`
- **SAGE/module dependencies:** `:cbox:common:ui:theme:api` (re-exported), `:cbox:common:ui:fonts:api`, `sage.android.ui.themes`, Compose Material3, Material Components
