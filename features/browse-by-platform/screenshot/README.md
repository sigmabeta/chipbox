# `:features:browse-by-platform:screenshot`

> Paparazzi visual-regression companion for `:features:browse-by-platform:real`.

The `:screenshot` module for the `browse-by-platform` feature: Android-only
Paparazzi previews and a parameterized test that snapshots the screen across a set
of devices. It wraps the `BrowseByPlatformState` object directly (no ViewModel),
producing golden images that guard the `:real` UI against regressions.

## Contents

| File | What it is |
| --- | --- |
| `preview/BrowseByPlatform.kt` | `@DevicePreviews` `BrowseByPlatform` and `BrowseByPlatformLoading` composables calling `ListScreenPreview(screenState = ...)`. Content uses `Platform.entries` minus `OTHER`; loading uses `LCE.Loading`. |
| `preview/BrowseByPlatformScreenshots.kt` | `@RunWith(Parameterized::class)` Paparazzi test snapshotting `browseByPlatformScreen` + `browseByPlatformScreenLoading` across `INTERESTING_DEVICES`. |

## Why depend on this module

Nothing depends on `:screenshot`; it's a leaf test companion. It exists so CI can
verify the rendered `browse-by-platform` screen against committed golden images
(git-LFS). It's Android-only because Paparazzi is.

## Using it

Run the goldens through the Paparazzi tasks:

```sh
./gradlew verifyPaparazziDebug   # check against committed goldens
./gradlew testDebugUnitTest      # re-record goldens (overwrites)
```

Use `verifyPaparazziDebug` unless you intend to re-record.

## Module facts

- **Plugin:** `chipbox.plugins.screenshot`
- **Targets:** Android only
- **Source set:** `src/main/java` + `src/test/java`
- **SAGE/module dependencies:** `implementation` `:features:browse-by-platform:real`
