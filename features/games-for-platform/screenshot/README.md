# `:features:games-for-platform:screenshot`

> Paparazzi golden-image companion for the games-for-platform screen.

The visual-regression test companion for `:real`: Compose previews of
`GamesForPlatformState` and a parameterized Paparazzi test that snapshots them
across a set of reference devices. As a `:screenshot` module it is Android-only
and exists purely to guard the screen's rendering with golden images.

## Contents

| File | What it is |
| --- | --- |
| `preview/GamesForPlatform.kt` | `@DevicePreviews` `GamesForPlatform` and `GamesForPlatformLoading` composables. Each calls `ListScreenPreview(screenState = ...)`, wrapping a `GamesForPlatformState` directly (content uses `FakeModelGenerator` games on `Platform.SNES`; loading uses `LCE.Loading`) — the state object, not the live VM. |
| `preview/GamesForPlatformScreenshots.kt` | `@RunWith(Parameterized::class)` test snapshotting `gamesForPlatformScreen` + `gamesForPlatformScreenLoading` across `INTERESTING_DEVICES`. |

## Why depend on this module

Nothing depends on `:screenshot`; it is a test-only leaf that depends on `:real`.
It catches unintended layout/styling changes to the screen via committed golden
images. Add or update previews here when the screen's visual output changes.

## Using it

```sh
./gradlew verifyPaparazziDebug   # check goldens against current rendering
./gradlew testDebugUnitTest      # re-record goldens (overwrites, git-LFS)
```

## Module facts

- **Plugin:** `chipbox.plugins.screenshot`
- **Targets:** Android only
- **Source set:** `src/main/java` (previews) + `src/test/java` (Paparazzi test)
- **SAGE/module dependencies:** `:features:games-for-platform:real`
