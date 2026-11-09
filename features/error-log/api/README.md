# `:features:error-log:api`

> Route key for the debug-only recent-errors viewer.

The `:api` half of the `error-log` feature: a single Voyager route key. `:api`
modules hold types — here the `@Serializable` destination plus the screen's State/Action types — and stay
KMP so the desktop UI builds the same screen.

## Contents

| File | What it is |
| --- | --- |
| `ErrorLog.kt` | `@Serializable data object ErrorLog` — the route key for the error-log screen. |
| `ErrorLogState.kt` | Maps each `HatchetError` to a `NameCaptionListModel` (message as name; `"12:34:56 · tag · thread"` caption, blank pieces dropped). `dataId` is the list index. Empty list renders an `EmptyStateListModel`. |
| `ErrorTimestamp.kt` | `expect fun formatErrorTimestamp(epochMs)` — wall-clock time-of-day; JVM/Android actuals use `java.time`, JS returns the raw epoch. |

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
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`, with `src/main/java` (`jvmSharedMain`) + `jsMain` actuals for the formatter/decode helper
- **SAGE/module dependencies:** the public dependencies of the State/Action types, declared in `build.gradle.kts`; `kotlinx.serialization` comes from the convention plugin
