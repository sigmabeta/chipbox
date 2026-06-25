# `:cbox:common:debug:api`

> Debug-build toggles — `DebugSettingsManager` and the source-swap enums it exposes.

The `:api` module for Chipbox debug settings: it declares
`DebugSettingsManager` plus the enums that pick which implementation of each
swappable component the app injects (repository, generator, speaker, image
loader, favorites, history, playlists), and the master "show debug UI" toggle.
These drive the in-app debug menu. Depend on this for the types; the app wires
`:real` via `:di`.

## Contents

| File | What it is |
| --- | --- |
| `DebugSettingsManager.kt` | The debug-settings interface: `getShouldShowDebug()`/`set…` plus a `get*Source()`/`set*Source()` pair per swappable component, each as a `Flow`. |
| `RepositorySource.kt` | `REAL` / `MEMORY` / `RANDOM` — which `Repository` serves data (default `REAL`). |
| `GeneratorSource.kt` | `REAL` (real chip emulation) / `FAKE` (procedural synth) `Generator` (default `REAL`). |
| `SpeakerSource.kt` | `REAL` (audio out) / `FILE` (WAV) / `TEXT` (log frames) `Speaker` (default `REAL`). |
| `ImageLoaderSource.kt` | `REAL` (Coil) / `FAKE` (generated gradient) image loader (default `REAL`). |
| `FavoritesSource.kt` | `REAL` (Room) / `FAKE` (in-memory) favorites repo, read at DI-graph build (default `REAL`). |
| `HistorySource.kt` | `REAL` / `FAKE` playback-history repo, read at DI-graph build (default `REAL`). |
| `PlaylistsSource.kt` | `REAL` / `FAKE` playlists repo, read at DI-graph build (default `REAL`). |

Every enum carries a `DEFAULT` and a `fromStorageValue(String?)` that falls back
to it.

## Why depend on this module

Depend on `:cbox:common:debug:api` from the debug-menu UI and from any DI wiring
that needs to read a `*Source` to decide which implementation to bind. It carries
only `kotlinx-coroutines-core`, so it stays multiplatform. The app injects
`RealDebugSettingsManager` (`:real`) via `:di`; tests use
`FakeDebugSettingsManager` (`:fake`).

## Using it

```kotlin
class DebugMenuViewModel(private val debug: DebugSettingsManager) {
    val speaker: Flow<SpeakerSource> = debug.getSpeakerSource()
    fun dumpToWav() = debug.setSpeakerSource(SpeakerSource.FILE)
}
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** none (leaf; `api`s `kotlinx-coroutines-core`)
