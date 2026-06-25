# `:features:game-detail:screenshot`

> Paparazzi golden-image companion for the game-detail screen.

The visual-regression test module for `:features:game-detail:real`. As a
`:screenshot` module it renders the screen's `GameDetailState` through the preview
harness and snapshots it across a set of devices; the goldens guard the screen's
appearance. Android-only.

## Contents

| File | What it is |
| --- | --- |
| `preview/GameDetail.kt` | `@DevicePreviews` composables `GameDetail` and `GameDetailLoading` that call `ListScreenPreview(screenState = …)` with a `FakeModelGenerator`-built content state or an `LCE.Loading` state — wrapping the `GameDetailState` object, not the ViewModel. |
| `preview/GameDetailScreenshots.kt` | `@RunWith(Parameterized::class) GameDetailScreenshots` — snapshots `gameDetailScreen` and `gameDetailScreenLoading` across `INTERESTING_DEVICES`. |

## Why depend on this module

Nothing depends on `:screenshot`; it is a leaf test companion that depends on
`:real`. It exists so CI (and `verifyPaparazziDebug`) can catch unintended visual
changes to the game-detail screen.

## Using it

```sh
./gradlew verifyPaparazziDebug   # check the goldens against the current UI
./gradlew testDebugUnitTest      # re-record the goldens (overwrites; git-LFS)
```

## Module facts

- **Plugin:** `chipbox.plugins.screenshot` (Paparazzi)
- **Targets:** Android only
- **Source set:** `src/main/java` (previews) + `src/test/java` (tests)
- **SAGE/module dependencies:** `implementation` `:features:game-detail:real`
