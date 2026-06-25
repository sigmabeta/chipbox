# `:features:browse-by-artist:screenshot`

> Paparazzi visual-regression companion for the Browse-by-Artist grid.

The `:screenshot` module for the feature: an Android-only Paparazzi harness that
renders `:real`'s state object across a set of device configs and diffs the
result against committed golden images. It exists purely to catch unintended
visual changes to the artist grid — it ships no production code.

## Contents

| File | What it is |
| --- | --- |
| `preview/BrowseByArtist.kt` | `@DevicePreviews` composables `BrowseByArtist` and `BrowseByArtistLoading`, each calling `ListScreenPreview(screenState = …)`. Content comes from `FakeModelGenerator` artists; the loading variant uses `LCE.Loading`. These wrap the `BrowseByArtistState` object directly, not the VM. |
| `preview/BrowseByArtistScreenshots.kt` | `@RunWith(Parameterized::class)` test snapshotting `browseByArtistScreen` and `browseByArtistScreenLoading` across `INTERESTING_DEVICES`. |

## Why depend on this module

Nothing depends on `:screenshot` — it's a leaf test companion for `:real`. It
exists so CI (and developers) can verify the artist grid renders unchanged.
Because it wraps the `State` object rather than the live `ViewModel`, the
snapshots are deterministic and need no repository or DI wiring.

## Using it

```sh
./gradlew verifyPaparazziDebug    # check against committed goldens (does NOT record)
./gradlew testDebugUnitTest       # re-record/overwrite goldens (git-LFS)
```

Use `verifyPaparazziDebug` unless you intentionally mean to re-record. Prefer the
`scripts/paparazzi-diff.sh` helper over invoking the Gradle tasks directly.

## Module facts

- **Plugin:** `chipbox.plugins.screenshot`
- **Targets:** Android only
- **Source set:** `src/main/java` (previews) + `src/test/java` (Paparazzi tests)
- **SAGE/module dependencies:** `implementation` → `:features:browse-by-artist:real`
