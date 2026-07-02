# `:features:games-for-platform:api`

> The Voyager route key for the "games for a platform" screen.

The public surface of the games-for-platform feature: the navigation route key
and its argument types. As an `:api` module it carries only the types that other
features need to navigate here — the screen implementation lives in `:real`.

## Contents

| File | What it is |
| --- | --- |
| `GamesForPlatform.kt` | `@Serializable data class GamesForPlatform(val platform: Platform)` — the Voyager route key. The argument is a `Platform` enum value, not a database id. |

## Why depend on this module

Depend on `:api` when you need to navigate to this screen without pulling in its
implementation. For example, browse-by-platform depends on `:api` so it can do
`emit(NavigateTo(GamesForPlatform(platform)))`. The app wires the actual screen
by including `:real`; `:api` stays small and multiplatform so any feature can
reference the route key cheaply.

## Using it

```kotlin
// From another feature's ViewModel, navigate here for a chosen platform:
emit(NavigateTo(GamesForPlatform(Platform.SNES)))
```

## Module facts

- **Plugin:** `chipbox.plugins.feature.api`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `:cbox:common:models:api` (for `Platform`)
