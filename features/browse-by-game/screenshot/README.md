# `:features:browse-by-game:screenshot`

> Paparazzi visual-regression companion for `:features:browse-by-game:real`.

The `:screenshot` module pins the rendered look of the browse-by-game screen with
golden images. It's an Android-only Paparazzi companion to `:real`: it wraps the
`BrowseByGameState` object (not the ViewModel) in previews and snapshots them
across a range of devices so unintended UI changes are caught in CI.

## Contents

| File | What it is |
| --- | --- |
| `preview/BrowseByGame.kt` | `@DevicePreviews` `BrowseByGame` and `BrowseByGameLoading` composables, each calling `ListScreenPreview(screenState = ...)`. Loaded content comes from `FakeModelGenerator().randomGames()`; the loading variant uses `LCE.Loading`. |
| `preview/BrowseByGameScreenshots.kt` | `@RunWith(Parameterized::class)` test snapshotting `browseByGameScreen` and `browseByGameScreenLoading` across `INTERESTING_DEVICES`. |

## Why depend on this module

Nothing depends on `:screenshot` — it's a test-only leaf that depends on `:real`.
Its job is to fail the build when the screen's pixels drift. Add or update its
previews when you change the browse-by-game UI; recording the goldens is a
deliberate, separate step (see below).

## Using it

```sh
./gradlew verifyPaparazziDebug   # check the goldens against the current UI
./gradlew testDebugUnitTest      # RE-RECORD the goldens (overwrites; git-LFS)
```

## Module facts

- **Plugin:** `chipbox.plugins.screenshot`
- **Targets:** Android only
- **Source set:** `src/main/java` (previews) + `src/test/java` (Paparazzi tests)
- **SAGE/module dependencies:** `implementation` → `:features:browse-by-game:real`
