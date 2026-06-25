# `:cbox:common:player:director:di`

> Metro wiring that binds `RealDirector` as the `AppScope` `Director` singleton.

The `:di` module for the director: a single Metro `@BindingContainer` that
provides the `Director` interface (from `:api`) backed by `RealDirector` (from
`:real`). Include this module in the app graph to make the director injectable;
no other module should depend on `:real` directly.

## Contents

| File | What it is |
| --- | --- |
| `DirectorModule.kt` | `@BindingContainer @ContributesTo(AppScope::class) object` with one `@Provides @SingleIn(AppScope::class)` factory: takes `Generator`, `Speaker`, `Repository`, `PlaylistsRepository`, `FavoritesRepository`, `ChipboxSettingsManager`, and `Hatchet`, returns a `RealDirector` as `Director`. The `@SingleIn(AppScope::class)` makes it a process-wide singleton. |

## Why depend on this module

The app (`apps/android`, `apps/jvm`) depends on `:cbox:common:player:director:di`
so the `Director` is bound into `AppScope` and resolvable wherever it's injected
(ViewModels, the playback service, the session persister). Everyone else depends
on `:api`. The dependencies the module declares (`generator/api`, `speaker/api`,
`playlists/api`, `favorites/api`, `settings/api`) are the collaborators
`RealDirector`'s constructor needs visible at wiring time.

## Using it

`@ContributesTo(AppScope::class)` means inclusion in the graph is automatic — no
manual registration. Consumers just inject the interface:

```kotlin
class SomeViewModel @Inject constructor(
    private val director: Director, // RealDirector, provided here
)
```

## Module facts

- **Plugin:** `sage.jvm` + `sage.di`
- **Targets:** JVM only (Metro/Dagger-shaped DI glue, not multiplatform)
- **Source set:** `src/main/java` (`jvmSharedMain`)
- **SAGE/module dependencies:** `:cbox:common:player:director:real` (`api`); `implementation` of `:cbox:common:player:generator:api`, `:cbox:common:player:speaker:api`, `:cbox:common:playlists:api`, `:cbox:common:favorites:api`, `:cbox:common:settings:api`. DI via `dev.zacsweers.metro`.
