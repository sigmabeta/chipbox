# `:features:favorites:screenshot`

> Paparazzi visual-regression companion for the Favorites screen.

The `:screenshot` module for the Favorites feature: an Android-only Paparazzi
test harness that snapshots the `:real` screen. It builds the UI from
`FavoritesState` objects (not the VM, so no runtime/DI), wraps them in
`ListScreenPreview`, and snapshots each across `INTERESTING_DEVICES`.

## Contents

| File | What it is |
| --- | --- |
| `preview/Favorites.kt` | `@DevicePreviews` composables `Favorites`/`FavoritesLoading`/`FavoritesEmpty`, each calling `ListScreenPreview(screenState = …)`. Populated, loading, and empty `FavoritesState`s are built from `FakeModelGenerator` data. |
| `preview/FavoritesScreenshots.kt` | `@RunWith(Parameterized::class)` Paparazzi test; `favoritesScreen`/`favoritesScreenLoading`/`favoritesScreenEmpty` snapshot the three previews over `INTERESTING_DEVICES`. |

## Why depend on this module

Nothing depends on `:screenshot` — it's a leaf test module that the app never
ships. It exists so changes to `:features:favorites:real` are caught as visual
diffs. Verify with `./gradlew verifyPaparazziDebug` (or `scripts/paparazzi-diff.sh`);
re-record goldens (overwrites the git-LFS images) with `testDebugUnitTest`.

## Using it

```kotlin
// A preview wraps a State object — no ViewModel, no DI:
@DevicePreviews
@Composable
internal fun Favorites(/* … */) {
    ListScreenPreview(screenState = favoritesState(), /* … */)
}

// The Parameterized test snapshots it per device config:
paparazzi.snapshot { Favorites(syntheticWidthClass = deviceConfig.toWidthClass()) }
```

## Module facts

- **Plugin:** `chipbox.screenshot`
- **Targets:** Android only (Paparazzi runs on the JVM unit-test classpath)
- **Source set:** `src/main/java` (previews) + `src/test/java` (Paparazzi tests)
- **SAGE/module dependencies:** `implementation` → `:features:favorites:real`
