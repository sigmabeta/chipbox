# `:features:library:screenshot`

> Paparazzi visual-regression goldens for the Library screen.

The screenshot-test companion for `:features:library:real`. As a `:screenshot`
module it's Android-only and never shipped: it wraps `LibraryState` in
`ListScreenPreview` and snapshots that preview across the `INTERESTING_DEVICES`
matrix (phone/tablet sizes × light/dark) so layout regressions surface in CI. It
renders the **state object**, not the ViewModel, so it carries no runtime/DI
dependencies.

## Contents

| File | What it is |
| --- | --- |
| `preview/Library.kt` | `@DevicePreviews @Composable Library(...)` — calls `ListScreenPreview(screenState = LibraryState, ...)`, driven by a synthetic `WidthClass` and theme flag. |
| `preview/LibraryScreenshots.kt` | `@RunWith(Parameterized::class)` Paparazzi test; `libraryScreen()` snapshots `Library(...)` once per `INTERESTING_DEVICES` config. |

## Why depend on this module

Nothing depends on `:features:library:screenshot`; it's a test-only leaf included
in `settings.gradle.kts` so its goldens run in CI. It depends on
`:features:library:real` only to reach `LibraryState`.

## Using it

```sh
./gradlew :features:library:screenshot:verifyPaparazziDebug   # check goldens
./gradlew :features:library:screenshot:testDebugUnitTest      # RE-RECORD goldens (overwrites LFS PNGs)
```

Prefer `scripts/paparazzi-diff.sh` over invoking the Gradle tasks directly. Use
`verify` unless you intend to re-record.

## Module facts

- **Plugin:** `chipbox.screenshot`
- **Targets:** Android only (Paparazzi runs on the JVM unit-test classpath)
- **Source set:** `src/main/java` (the preview) + `src/test/java` (the test); goldens under `src/test/snapshots/images` (git-LFS)
- **SAGE/module dependencies:** `:features:library:real`
