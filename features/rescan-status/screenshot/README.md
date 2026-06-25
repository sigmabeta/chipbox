# `:features:rescan-status:screenshot`

> Paparazzi visual-regression goldens for the rescan-status screen.

The Android-only screenshot companion for the rescan-status feature: device
previews that render `RescanStatusState` through `ListScreenPreview`, plus a
parameterized Paparazzi test that snapshots them across the standard device
matrix. It produces no production code — only golden images checked under
`src/test/snapshots/`.

## Contents

| File | What it is |
| --- | --- |
| `RescanStatus.kt` | Two `@DevicePreviews` composables: `RescanStatus` (a mid-scan `SCANNING` state with added/updated/removed change rows) and `RescanStatusIdle` (the default empty/IDLE state). Both call `ListScreenPreview(screenState = …)`. |
| `RescanStatusScreenshots.kt` | `@RunWith(Parameterized)` Paparazzi test with `rescanStatusScreen` / `rescanStatusScreenIdle`, snapshotting the previews over `INTERESTING_DEVICES`. |

## Why depend on this module

Nothing depends on this module — it's a verification leaf. It depends on
`:real` to render the actual screen state and guards it against unintended
visual change. Per project convention, run goldens via `scripts/paparazzi-diff.sh`
(`verifyPaparazziDebug` *checks*; `testDebugUnitTest` *records*/overwrites the
git-LFS goldens).

## Using it

```kotlin
@DevicePreviews
@Composable
internal fun RescanStatus(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = scanningState(), // SCANNING phase + sample change feed
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}
```

The test drives these previews:

```kotlin
@Test
fun rescanStatusScreen() {
    paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
    paparazzi.snapshot { RescanStatus(syntheticWidthClass = deviceConfig.toWidthClass()) }
}
```

## Module facts

- **Plugin:** `chipbox.screenshot`
- **Targets:** Android only
- **Source set:** `src/main/java` (previews) and `src/test/java` (Paparazzi test); goldens in `src/test/snapshots/`
- **SAGE/module dependencies:** `:features:rescan-status:real`
