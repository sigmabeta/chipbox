# `:features:artist-detail:screenshot`

> Paparazzi golden tests for the Artist Detail screen.

The visual-regression companion for `:features:artist-detail:real`: device
previews of the screen plus the Paparazzi test that snapshots them. As the
`:screenshot` module it is Android-only and exists purely to guard the rendered
UI against unintended pixel changes.

## Contents

| File | What it is |
| --- | --- |
| `preview/ArtistDetail.kt` | `@DevicePreviews` composables `ArtistDetail` and `ArtistDetailLoading` rendering `ListScreenPreview` from a built `ArtistDetailState` — content state from `FakeModelGenerator`, loading state from `LCE.Loading`. Wraps the state object, not the VM, so it needs no DI or runtime. |
| `preview/ArtistDetailScreenshots.kt` | `@RunWith(Parameterized::class)` Paparazzi test snapshotting `artistDetailScreen` and `artistDetailScreenLoading` across `INTERESTING_DEVICES`. |

## Why depend on this module

Nothing depends on `:screenshot`; it is a leaf test companion. It depends on
`:real` to render the real `ArtistDetailState`. Touch it only when the
artist-detail UI changes and the goldens need re-recording.

## Using it

```sh
./gradlew verifyPaparazziDebug   # check the goldens (does NOT record)
./gradlew testDebugUnitTest      # re-record/overwrite goldens (git-LFS)
```

## Module facts

- **Plugin:** `chipbox.plugins.screenshot`
- **Targets:** Android only
- **Source set:** `src/main/java` (previews) + `src/test/java` (Paparazzi tests)
- **SAGE/module dependencies:** implementation `:features:artist-detail:real`
