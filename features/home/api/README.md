# `:features:home:api`

> The Home screen's Voyager route key — the navigable destination for the app's landing tab.

The public surface other features depend on to navigate to Home. As a feature
`:api` module it exports the `@Serializable` route key, the screen's
`State`/`Action` types, and the `HomeModule` extension point; the actual screen
lives in `:features:home:real`.

## Contents

| File | What it is |
| --- | --- |
| `Home.kt` | `@Serializable data object Home` — the Voyager route key for the Home screen. `ChipboxScreens.screenFor(Home)` maps it to the `Screen` that renders `HomeRoute`. |
| `HomeState.kt` | `HomeState : ListState` plus `HomeSectionState` — assembles the section rows and first-run empty state. |
| `HomeAction.kt` | `HomeAction : ChipboxAction` — game/artist/song clicks, RNG actions, folder/rescan CTAs, and now-playing card events. |
| `module/HomeModule.kt` | The `HomeModule` extension point and its `HomeModuleSection` result type; concrete modules implement this from `:real`. |

## Why depend on this module

Depend on `:features:home:api` whenever you need to *navigate to* Home without
pulling in its implementation — e.g. emitting `ChipboxEvent.NavigateTo(Home)`, or
registering the route in `ChipboxScreens`. It carries no UI, ViewModel, or DI, so
it stays a cheap, leaf-ish dependency. The app wires the screen itself via
`:features:home:real`.

## Using it

```kotlin
// From any ViewModel that wants to land the user on Home:
emit(ChipboxEvent.NavigateTo(Home))
```

## Module facts

- **Plugin:** `chipbox.feature.api`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js` (the
  `feature.api` plugin transitively applies `sage.kmp.js`).
- **Source set:** `commonMain`
- **SAGE/module dependencies:** the public dependencies of the State/Action types, declared in `build.gradle.kts`; `kotlinx.serialization` comes from the convention plugin
