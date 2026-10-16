# `:features:tracks-for-artist:screenshot`

> Paparazzi golden-image companion for the tracks-for-artist screen.

The visual-regression test companion for `:real`: Compose previews of
`TracksForArtistState` and a parameterized Paparazzi test that snapshots them across
a set of reference devices. As a `:screenshot` module it is Android-only and exists
purely to guard the screen's rendering with golden images.

## Contents

| File | What it is |
| --- | --- |
| `preview/TracksForArtist.kt` | `@DevicePreviews` `TracksForArtist` and `TracksForArtistLoading` composables. Each calls `ListScreenPreview(screenState = ...)`, wrapping a `TracksForArtistState` directly (content uses `FakeModelGenerator` tracks; loading uses `LCE.Loading`) — the state object, not the live VM. |
| `preview/TracksForArtistScreenshots.kt` | `@RunWith(Parameterized::class)` test snapshotting `tracksForArtistScreen` + `tracksForArtistScreenLoading` across `INTERESTING_DEVICES`. |

## Why depend on this module

Nothing depends on `:screenshot`; it is a test-only leaf that depends on `:real`. It
catches unintended layout/styling changes to the screen via committed golden images.
Add or update previews here when the screen's visual output changes.

## Using it

```sh
scripts/dev/paparazzi-diff.sh   # check goldens (preferred entry point)
```

## Module facts

- **Plugin:** `chipbox.plugins.screenshot`
- **Targets:** Android only
- **Source set:** `src/main/java` (previews) + `src/test/java` (Paparazzi test)
- **SAGE/module dependencies:** `:features:tracks-for-artist:real`
