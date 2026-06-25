# `:features:browse-all-tracks:screenshot`

> Paparazzi visual-regression companion for the browse-all-tracks screen.

The `:screenshot` module: Android-only Paparazzi previews and golden-image tests
for `:real`. It renders `BrowseAllTracksState` directly (wrapping the state
object, not the ViewModel) so the list UI can be snapshotted deterministically
across devices.

## Contents

| File | What it is |
| --- | --- |
| `preview/BrowseAllTracks.kt` | `@DevicePreviews` `BrowseAllTracks` and `BrowseAllTracksLoading` composables calling `ListScreenPreview(screenState = …)`. Content state is built from `FakeModelGenerator` tracks with a `playingTrackId`; the loading state uses `LCE.Loading`. |
| `preview/BrowseAllTracksScreenshots.kt` | `@RunWith(Parameterized::class)` Paparazzi test snapshotting `browseAllTracksScreen` and `browseAllTracksScreenLoading` across `INTERESTING_DEVICES`. |

## Why depend on this module

Nothing depends on `:screenshot` — it's a leaf test companion that depends on
`:real`. It exists only to catch unintended visual changes to the
browse-all-tracks screen; the goldens are the regression baseline.

## Using it

```sh
./gradlew verifyPaparazziDebug   # check goldens (does NOT record)
./gradlew testDebugUnitTest      # re-record/overwrite goldens (git-LFS)
```

## Module facts

- **Plugin:** `chipbox.plugins.screenshot`
- **Targets:** Android only
- **Source set:** `src/main/java` (previews) + `src/test/java` (Paparazzi tests)
- **SAGE/module dependencies:** `implementation` → `:features:browse-all-tracks:real`
