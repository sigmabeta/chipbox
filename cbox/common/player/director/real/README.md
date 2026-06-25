# `:cbox:common:player:director:real`

> `RealDirector` — the production playback orchestrator and its state machine.

The production implementation of `Director` (from
`:cbox:common:player:director:api`). This is the `:real` module: the app never
references it directly — it depends on `:cbox:common:player:director:di`, which
binds `RealDirector` into `AppScope`. Tests use `:cbox:common:player:director:fake`
instead.

## Contents

| File | What it is |
| --- | --- |
| `RealDirector.kt` | The orchestrator. Holds all playback state in one immutable `Model`, merges `Generator.events()` + `Speaker.events()` into a single stream, and runs pure `reduce(model, event) → (nextModel, effects)` reducers whose `Effect`s (start/stop track, switch speaker, arm watchdog, emit metadata, publish error…) are then applied. Owns track sequencing/shuffle, the `IDLE→BUFFERING→PLAYING→ENDING→STOPPED/ERROR` transitions, repeat/shuffle modes, setlist resolution per `Session.type`, a ~5 s stall watchdog, and a consecutive-failure cap. Transport methods are imperative entry points; `request()` dispatches each `SessionRequest` onto the director scope. |

Tests (`commonTest`): `RealDirectorReducerTest` exercises the pure reducers by
asserting returned effect lists; `RealDirectorTest` drives the whole pipeline over
the generator/speaker/repository/playlists/favorites/settings fakes.

## Why depend on this module

You almost never should directly — depend on `:api` for the types and let `:di`
provide the instance. Only `:cbox:common:player:director:di` depends on `:real`
(to construct it). The `commonTest` deps show the seams it needs at runtime:
a `Generator`, `Speaker`, `Repository`, `PlaylistsRepository`,
`FavoritesRepository`, `ChipboxSettingsManager`, and a `Hatchet` logger.

## Using it

`RealDirector` runs on a single-threaded dispatcher
(`Dispatchers.Default.limitedParallelism(1)`) so its `Model` mutates without locks.
Construction is normally Metro's job; the shape is:

```kotlin
val director: Director = RealDirector(
    generator, speaker, repository,
    playlistsRepository, favoritesRepository, settingsManager, hatchet,
)
director.request(SessionRequest.Start(session))
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `chipbox.plugins.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain` (impl) / `commonTest` (tests)
- **SAGE/module dependencies:** `:cbox:common:player:director:api` (`api`); `implementation` of `:cbox:common:player:common:api`, `:cbox:common:player:generator:api`, `:cbox:common:player:speaker:api`, `:cbox:common:repository:api`, `:cbox:common:playlists:api`, `:cbox:common:favorites:api`, `:cbox:common:settings:api`, `kotlinx.coroutines.core`, `sage.common.logging`. Test deps add the matching `:fake` modules.
