# `:features:error-log:api`

> Route key for the debug-only recent-errors viewer.

The `:api` half of the `error-log` feature: a single Voyager route key. `:api`
modules hold only types — here just the `@Serializable` destination — and stay
KMP so the desktop UI builds the same screen.

## Contents

| File | What it is |
| --- | --- |
| `ErrorLog.kt` | `@Serializable data object ErrorLog` — the route key for the error-log screen. |

## Why depend on this module

Depend on `:features:error-log:api` to navigate to the screen — e.g.
`:features:settings:real` emits `ChipboxEvent.NavigateTo(ErrorLog)` from its debug
section — or to map the route key to a `Screen` in `ChipboxScreens.kt`. The app
wires the implementation via `:features:error-log:real`.

## Using it

```kotlin
// From a debug-section row's action handler:
emit(ChipboxEvent.NavigateTo(ErrorLog))
```

## Module facts

- **Plugin:** `chipbox.feature.api`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** none (leaf — only `kotlinx.serialization`, provided by the plugin)
