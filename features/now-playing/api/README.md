# `:features:now-playing:api`

> The route key for the full-screen player.

The public surface of the now-playing feature: a single `@Serializable` Voyager
destination that other features navigate to. This is the feature's `:api`
module, so it carries only the route key (no UI, no impl) and is the thing you
depend on to open the player. The app wires `:real` for the actual screen.

## Contents

| File | What it is |
| --- | --- |
| `NowPlaying.kt` | `@Serializable data object NowPlaying` — the route key for the full player screen. No arguments: the screen reads everything from the running playback session. |

## Why depend on this module

Depend on `:features:now-playing:api` whenever a screen needs to navigate to the
full player. You get the `NowPlaying` route key without pulling in the screen's
Compose UI or ViewModel. The app separately includes `:real`, which maps this
key to the actual `NowPlayingRoute` content via
`cbox/common/appui/api/.../ChipboxScreens.kt`.

## Using it

```kotlin
emit(ChipboxEvent.NavigateTo(NowPlaying))
```

## Module facts

- **Plugin:** `chipbox.plugins.feature.api`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** none (leaf)
