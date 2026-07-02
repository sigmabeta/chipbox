# `:features:home:api`

> The Home screen's Voyager route key — the navigable destination for the app's landing tab.

The public surface other features depend on to navigate to Home. As a feature
`:api` module it exports only the `@Serializable` route key (and would hold any
shared `State`/`Action` types, though Home keeps those in `:real`); the actual
screen lives in `:features:home:real`.

## Contents

| File | What it is |
| --- | --- |
| `Home.kt` | `@Serializable data object Home` — the Voyager route key for the Home screen. `ChipboxScreens.screenFor(Home)` maps it to the `Screen` that renders `HomeRoute`. |

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
- **SAGE/module dependencies:** none (leaf) — only `kotlinx.serialization`.
