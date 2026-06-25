# `:features:home:screenshot`

> Paparazzi visual-regression companion for the Home screen.

The `:screenshot` module for the Home feature: Android-only Paparazzi tests that
snapshot `:features:home:real`'s `HomeState` across a matrix of devices and
themes. It renders the **state object** directly through `ListScreenPreview`
(never the ViewModel), so the goldens stay free of DI, repositories, and the
scanner — every section is a hand-built `HomeState` fixture.

## Contents

| File | What it is |
| --- | --- |
| `src/main/java/.../preview/Home.kt` | `@DevicePreviews` composables wrapping `HomeState` fixtures in `ListScreenPreview`: a fully-populated home (`Home`), `HomeLoading`, the two empty states (`HomeEmptyNoFolders` / `HomeEmptyFoldersPresent`), and the three scan-status cards (`HomeScanStatusScanning` / `Complete` / `Failed`). Builds the section `ListModel`s inline (game/song/artist tiles, now-playing card, scan-status card) from `FakeModelGenerator` data. |
| `src/test/java/.../preview/HomeScreenshots.kt` | `@RunWith(Parameterized::class)` Paparazzi test; one `@Test` per preview, snapshotting each `INTERESTING_DEVICES` config (light/dark × phone/tablet sizes). |

## Why depend on this module

Nothing depends on `:features:home:screenshot` — it's a test-only leaf. It exists
so changes to the Home UI (or the SAGE list components it renders) are caught as
pixel diffs. It needs `:features:home:real` for `HomeState`/`HomeAction`/
`HomeSectionState`, plus `sage.common.ui.components`, `cbox.common.appcomm.api`,
and `cbox.common.ui.components.api` because the previews construct sage and
chipbox `ListModel`s (e.g. `GridImageListModel`, `NowPlayingHomeCardListModel`,
`ScanStatusCardListModel`) inline rather than going through a module's
item-building.

## Using it

```sh
./gradlew verifyPaparazziDebug   # check Home goldens (does NOT record)
./gradlew testDebugUnitTest      # re-record / overwrite the LFS goldens
```

Prefer `scripts/paparazzi-diff.sh` over invoking the tasks directly. To add a
state, add a `@DevicePreviews` composable in `Home.kt` (wrapping a new `HomeState`
fixture in `ListScreenPreview`) and a matching `@Test` in `HomeScreenshots.kt`.

## Module facts

- **Plugin:** `chipbox.screenshot` (Paparazzi)
- **Targets:** Android only.
- **Source set:** `src/main/java` (previews) + `src/test/java` (Paparazzi tests).
- **SAGE/module dependencies:** `:features:home:real`,
  `sage.common.ui.components`, `cbox/common/appcomm/api`,
  `cbox/common/ui/components/api`.
