# `:features:playlists:screenshot`

> Paparazzi visual-regression goldens for the playlists list screen.

The `:screenshot` companion for the `playlists` feature (Android-only). It renders
`PlaylistsState` through the shared `ListScreenPreview` harness across a device matrix
and pins the result as Paparazzi snapshots, so unintended UI changes fail CI.

## Contents

| File | What it is |
| --- | --- |
| `preview/Playlists.kt` | Three `@DevicePreviews` composables — `Playlists` (populated list), `PlaylistsEmpty` (empty `PlaylistsState`), `PlaylistsPicker` (`isPicker = true`) — each calling `ListScreenPreview(screenState = …)` with a fixed sample of three `Playlist`s. |
| `preview/PlaylistsScreenshots.kt` | `@RunWith(Parameterized)` Paparazzi test: `playlistsScreen`, `playlistsScreenEmpty`, and `playlistsScreenPicker` snapshot each preview across `INTERESTING_DEVICES` (light/dark × phone/tablet form factors). |
| `src/test/snapshots/images/` | Recorded golden PNGs (git-LFS), one per test × device × theme. |

## Why depend on this module

Nothing depends on `:features:playlists:screenshot`; it is a leaf test companion. It
depends on `:features:playlists:real` purely to render the real `PlaylistsState` /
`ListScreenPreview` output. It exists to keep the screen's appearance under
regression control, separate from the `:api` route key and `:real` implementation.

## Using it

Verify the goldens (does not record):

```sh
./gradlew :features:playlists:screenshot:verifyPaparazziDebug
```

Use `scripts/paparazzi-diff.sh` rather than invoking the Gradle task directly. Record
(overwrite) goldens only intentionally via `testDebugUnitTest`.

## Module facts

- **Plugin:** `chipbox.plugins.screenshot`
- **Targets:** Android only
- **Source set:** `src/main/java` (previews), `src/test/java` (Paparazzi test)
- **SAGE/module dependencies:** `:features:playlists:real`
