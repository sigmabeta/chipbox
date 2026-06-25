# `:cbox:common:player:persistence:di`

> Metro wiring for the session store + persister singletons in `AppScope`.

The `:di` module for playback-session persistence: one Metro `@BindingContainer`
that provides the `PlaybackSessionStore` and `PlaybackSessionPersister` interfaces
(from `:api`) backed by their `:real` implementations. Include it in the app graph;
nothing else should depend on `:real`.

## Contents

| File | What it is |
| --- | --- |
| `PlaybackSessionModule.kt` | `@BindingContainer @ContributesTo(AppScope::class) object` with two `@Provides @SingleIn(AppScope::class)` factories: `RealPlaybackSessionStore(storage)` as `PlaybackSessionStore`, and `RealPlaybackSessionPersister(director, store, hatchet)` as `PlaybackSessionPersister`. |

## Why depend on this module

The app depends on `:cbox:common:player:persistence:di` so the store and persister
are bound into `AppScope` and injectable into the playback service. Everyone else
depends on `:api`. The declared deps (`director/api`, SAGE `storage.common`,
`logging`) are the collaborators the two factories need at wiring time.

## Using it

`@ContributesTo(AppScope::class)` auto-includes the container. Consumers inject the
interfaces:

```kotlin
class PlaybackService @Inject constructor(
    private val persister: PlaybackSessionPersister,
)
```

## Module facts

- **Plugin:** `sage.jvm` + `sage.di`
- **Targets:** JVM only (Metro/Dagger-shaped DI glue, not multiplatform)
- **Source set:** `src/main/java` (`jvmSharedMain`)
- **SAGE/module dependencies:** `:cbox:common:player:persistence:api`, `:cbox:common:player:persistence:real` (both `api`); `implementation` of `:cbox:common:player:director:api`, `sage.common.storage.common`, `sage.common.logging`. DI via `dev.zacsweers.metro`.
