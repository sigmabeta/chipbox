# `:features:folder-picker:screenshot`

> Paparazzi visual-regression companion for the folder-picker screen.

The `:screenshot` module for the folder-picker feature: an Android-only Paparazzi
test harness that snapshots the picker's UI across device configs. Following the
feature-screen convention, it renders the `FolderPickerState` value object (not the
ViewModel) through `ListScreenPreview`, so the goldens have no runtime or DI
dependencies — they exercise the pure `State` → list rendering only.

## Contents

| File | What it is |
| --- | --- |
| `preview/FolderPicker.kt` | Two `@DevicePreviews @Composable`s — `FolderPicker` (a populated `FolderPickerState` with subfolder rows, an aggregate files row, and a non-null parent so the "Go up a folder" CTA renders) and `FolderPickerEmpty` (a bare `FolderPickerState(currentPath = …)`), each wrapped in `ListScreenPreview`. |
| `preview/FolderPickerScreenshots.kt` | `@RunWith(Parameterized::class)` Paparazzi test snapshotting both previews across every `INTERESTING_DEVICES` config. |

## Why depend on this module

Nothing depends on `:screenshot`; it's a test-only leaf that depends on
`:features:folder-picker:real` to reach the `FolderPickerState`/`FolderPickerEntry`
types it builds previews from. It exists purely to catch unintended visual changes
to the picker screen.

## Using it

Check the goldens (does NOT record):

```sh
./gradlew verifyPaparazziDebug
```

Re-record (overwrites the LFS goldens) only when the change is intentional:

```sh
./gradlew :features:folder-picker:screenshot:testDebugUnitTest
```

Prefer `scripts/paparazzi-diff.sh` over invoking the Gradle tasks directly.

## Module facts

- **Plugin:** `chipbox.screenshot`
- **Targets:** Android only
- **Source set:** `src/main/java` (previews) + `src/test/java` (Paparazzi tests)
- **SAGE/module dependencies:** `:features:folder-picker:real`
