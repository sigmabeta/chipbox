# `:cbox:common:playlists:di`

> Metro wiring for playlists — binds the repository into `AppScope`.

The `:di` module for playlists. Its Metro `@BindingContainer` provides the
`PlaylistsRepository` from the `PlaylistsDatabase` DAOs, or the in-memory fake
when the debug menu selects it. The app graph includes this module; the
`PlaylistsDatabase` itself is provided per-platform.

## Contents

| File | What it is |
| --- | --- |
| `PlaylistsModule.kt` | `@BindingContainer @ContributesTo(AppScope::class)`. Provides `PlaylistsRepository` (`@SingleIn(AppScope::class)`): REAL `RealPlaylistsRepository` vs FAKE `FakePlaylistsRepository`, chosen once at graph build from `DebugSettingsManager.getPlaylistsSource()`. |

## Why depend on this module

The app graph includes `:di` to get playlists wired into `AppScope`. It pulls in
`:cbox:common:playlists:real` and `:cbox:common:playlists:fake`; the debug switch
picks between them at graph build (applied on the next launch).

## Using it

```kotlin
// Included transitively via @ContributesTo(AppScope::class); consumers inject the bound type:
@Inject class PlaylistsViewModel(repository: PlaylistsRepository)
```

## Module facts

- **Plugin:** `sage.jvm` + `sage.di`
- **Targets:** JVM (the DI wiring is plain JVM; the bound impls are KMP)
- **Source set:** `src/main/java`
- **SAGE/module dependencies:** `:cbox:common:playlists:api`, `:cbox:common:playlists:real`, `:cbox:common:playlists:fake`, `:cbox:common:debug:api`, `kotlinx-coroutines-core`, `sage.common.logging`
