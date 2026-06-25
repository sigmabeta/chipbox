# `:features:playlist-detail:screenshot`

> Paparazzi visual-regression goldens for the playlist-detail screen.

The Android-only `:screenshot` companion to `:features:playlist-detail:real`: a set
of `@DevicePreviews` composables driving `PlaylistDetailState` through
`ListScreenPreview`, plus a parameterized Paparazzi test that snapshots each across
the interesting devices and themes.

## Contents

| File | What it is |
| --- | --- |
| `preview/PlaylistDetail.kt` | `@DevicePreviews` composables for each state — content, empty, editing, renaming, confirming-delete, and not-found — each calling `ListScreenPreview(screenState = ...)`. |
| `preview/PlaylistDetailScreenshots.kt` | `@RunWith(Parameterized)` Paparazzi test; one `@Test` per preview, parameterized over `INTERESTING_DEVICES`. |

## Why depend on this module

Nothing depends on this module — it is a leaf test companion. It exists only to
host the Paparazzi goldens (under `src/test/snapshots/`) and guard the
playlist-detail UI against unintended visual changes. It depends on
`:features:playlist-detail:real` to render the real screen against its state.

## Using it

```kotlin
// Verify the goldens (does NOT re-record). Prefer scripts/paparazzi-diff.sh.
./gradlew verifyPaparazziDebug
```

## Module facts

- **Plugin:** `chipbox.screenshot`
- **Targets:** Android only
- **Source set:** `src/main/java` (previews), `src/test/java` (Paparazzi test)
- **SAGE/module dependencies:** `:features:playlist-detail:real`
