# `:cbox:android:ui:previews`

> Paparazzi/`@Preview` scaffolding — VM-free screen rendering, device configs, and a deterministic fake-model generator.

Android UI support module: shared helpers that let feature `:screenshot` modules and Compose
`@Preview`s render a screen's `ListState` through the real SAGE list pipeline without ViewModels
or DI, so Paparazzi snapshots are stable. Not an `:api`/`:di` module — it's a test/preview support
library consumed only by leaf `:features:*:screenshot` modules (so the Paparazzi runtime never
reaches the shipped app).

## Contents

| File | What it is |
| --- | --- |
| `ScreenPreview.kt` | `ListScreenPreview` (renders a `ListState` through `GridScreen`/`ListScreen` with VM-free `PreviewChrome`), `ScreenPreview` (for screens that own their chrome, e.g. Search), and `previewWidthClass()`. Provides `AppTheme`, `LocalInspectionMode`, a `BasicHatchet` logger, and the Chipbox `StringProvider`. |
| `DevicePreviews.kt` | `@DevicePreviews` multi-`@Preview` annotation: light/dark × phone/foldable/tablet device specs. |
| `PreviewTestUtils.kt` | Paparazzi `DeviceConfig` matrix (`INTERESTING_DEVICES`, light + dark) and `DeviceConfig.toWidthClass()` mapping. |
| `fake/FakeModelGenerator.kt` | Deterministic `Game`/`Track`/`Artist` generator seeded for stable goldens; owns its `Random`/`StringGenerator`. |

## Why depend on this module

A feature's `:screenshot` (Paparazzi) module depends on this to render its screens deterministically
and to fabricate stable fake models. Only those leaf modules consume it, so the dependency is a
plain `implementation` and the Paparazzi runtime has no path into the app.

## Using it

```kotlin
@Test
fun gamesScreen() = paparazzi.snapshot {
    ListScreenPreview(
        screenState = FakeModelGenerator().randomGames().toListState(),
        darkTheme = false,
        syntheticWidthClass = WidthClass.COMPACT,
    )
}
```

## Module facts

- **Plugin:** `sage.android` + `sage.compose.android`
- **Targets:** Android only
- **Source set:** `src/main/java`
- **SAGE/module dependencies:** `:cbox:android:ui:theme:api`, `:cbox:common:ui:components:api`, `:cbox:common:strings:real`/`:api`, `:cbox:common:models:api`, several `sage.common.*` list/ui/perf modules, Compose, `paparazzi.runtime`
