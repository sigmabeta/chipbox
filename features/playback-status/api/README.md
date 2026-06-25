# `:features:playback-status:api`

> The route key for the playback-diagnostics screen.

The public, KMP-safe surface of the `playback-status` feature: a single
`@Serializable` Voyager destination, `PlaybackStatus`. As a feature `:api`
module its only job is to let other modules navigate to this screen without
depending on its implementation — the `:real` impl and the app's
`ChipboxScreens` route map both reference this type.

Despite the "status" name this is the **debug-only playback diagnostics
screen** (sound-pipeline state dump), reached from the gated Settings debug
section — not a mini-player bar.

## Contents

| File | What it is |
| --- | --- |
| `PlaybackStatus.kt` | `@Serializable data object PlaybackStatus` — the Voyager route key for the playback diagnostics screen. |

## Why depend on this module

Depend on `:features:playback-status:api` when you need to *navigate* to the
playback diagnostics screen (e.g. emitting `NavigateTo(PlaybackStatus)` from a
Settings row). It carries no implementation, so it stays a tiny leaf and keeps
callers off the `:real` module. The `:real` module exposes this destination's
behavior; `cbox/common/appui/api`'s `ChipboxScreens` maps the key to a Voyager
`Screen`.

## Using it

```kotlin
// From a screen that wants to open the diagnostics screen:
onEvent(ChipboxEvent.NavigateTo(PlaybackStatus))
```

## Module facts

- **Plugin:** `chipbox.feature.api`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** none (leaf)
