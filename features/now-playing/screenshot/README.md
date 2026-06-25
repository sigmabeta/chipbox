# `:features:now-playing:screenshot`

> Paparazzi visual-regression goldens for the now-playing player.

The Paparazzi companion for the now-playing screen. This is the feature's
`:screenshot` module (Android-only): a set of `@DevicePreviews` composables that
render `NowPlayingContent` against hand-built `NowPlayingModel` samples, plus a
`@RunWith(Parameterized)` Paparazzi test that snapshots each preview across the
standard device matrix (light/dark, phones/tablets). It depends on `:real` and
adds no production code.

## Contents

| File | What it is |
| --- | --- |
| `preview/NowPlaying.kt` | `@DevicePreviews` composables — `NowPlayingPlaying`, `NowPlayingPaused`, `NowPlayingBuffering`, `NowPlayingError`, `NowPlayingLinks`, `NowPlayingTagDetail`, `NowPlayingArtists`, `NowPlayingControls`, `NowPlayingControlsFavorited` — each wrapping `NowPlayingContent` in `ScreenPreview` with a `sampleModel(...)`. |
| `preview/NowPlayingScreenshots.kt` | `@RunWith(Parameterized)` Paparazzi test; one `@Test` per preview, snapshotting across `INTERESTING_DEVICES`. |
| `src/test/snapshots/images/*.png` | Recorded golden images (git-LFS). |

## Why depend on this module

Nothing depends on `:screenshot` — it's a test-only leaf that the CI/Paparazzi
suite builds to guard the now-playing UI against unintended visual changes. It
exists separately from `:real` so the production screen carries no Paparazzi or
preview-sample weight. Editing the UI in `:real` means re-recording these goldens.

## Using it

Verify the goldens (does not record):

```sh
scripts/paparazzi-diff.sh
```

Each preview builds a `NowPlayingModel` directly, e.g.:

```kotlin
NowPlayingScreenshot(darkTheme, syntheticWidthClass, sampleModel(isPlaying = true))
```

## Module facts

- **Plugin:** `chipbox.plugins.screenshot`
- **Targets:** Android only
- **Source set:** `src/main/java` (previews) + `src/test/java` (Paparazzi test)
- **SAGE/module dependencies:** `:features:now-playing:real`;
  `cbox/common/player.common.api`; sage `images` and `ui.components` (the preview
  builds `NowPlayingModel` directly, referencing `SourceInfo`, `RepeatMode`, and
  `NameCaptionValueListModel`, none of which are transitive through `:real`).
