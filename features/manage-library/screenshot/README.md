# `:features:manage-library:screenshot`

> Paparazzi visual-regression goldens for the manage-library screen.

The `:screenshot` companion for `:features:manage-library:real`. It's an
Android-only Paparazzi module that renders `ManageLibraryState` through the
shared `ListScreenPreview` harness across a matrix of devices and themes, so
visual changes to the screen are caught by golden comparison. It contributes no
production code.

## Contents

| File | What it is |
| --- | --- |
| `preview/ManageLibrary.kt` | `@DevicePreviews` composables `ManageLibrary` (a populated `ManageLibraryState` with three folders) and `ManageLibraryEmpty` (a default empty `ManageLibraryState`), each wrapped in `ListScreenPreview`. |
| `preview/ManageLibraryScreenshots.kt` | Parameterized Paparazzi test (`manageLibraryScreen`, `manageLibraryScreenEmpty`) that snapshots those previews over `INTERESTING_DEVICES`. |

## Why depend on this module

Nothing depends on this module — it's a test-only leaf that depends on `:real`.
It exists so CI's `verifyPaparazziDebug` can diff the manage-library screen
against committed goldens.

## Using it

```sh
# Check the goldens (does NOT record):
./gradlew :features:manage-library:screenshot:verifyPaparazziDebug

# Or use the repo helper, which reports diffs:
scripts/paparazzi-diff.sh
```

Recording (overwriting) goldens is `testDebugUnitTest`; prefer verify unless you
intend to re-record.

## Module facts

- **Plugin:** `chipbox.plugins.screenshot`
- **Targets:** Android only (Paparazzi runs on the JVM unit-test classpath)
- **Source set:** `src/main/java` (preview composables) + `src/test/java`
  (Paparazzi tests); goldens under `src/test/snapshots/images` (git-LFS)
- **SAGE/module dependencies:** `:features:manage-library:real`
