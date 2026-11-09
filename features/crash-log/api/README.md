# `:features:crash-log:api`

> Route key for the debug-only crash-report viewer.

The `:api` half of the `crash-log` feature: a single Voyager route key. `:api`
modules hold types — here the `@Serializable` destination plus the screen's State/Action types — and stay
KMP so the desktop UI builds the same screen.

## Contents

| File | What it is |
| --- | --- |
| `CrashLog.kt` | `@Serializable data object CrashLog` — the route key for the crash-log screen. |
| `CrashLogState.kt` | Maps each `CrashReport` to a `CollapsibleDetailsListModel` — a one-line summary title (`"IllegalStateException · 2026-06-07 14:23:01"`) plus expandable detail items (message, build metadata, stack trace, recent-log tail). Empty list renders an `EmptyStateListModel`. |
| `CrashTimestamp.kt` | `expect fun formatCrashTimestamp(epochMs)` — wall-clock date + time; JVM/Android actuals use `java.time`, JS returns the raw epoch. |

## Why depend on this module

Depend on `:features:crash-log:api` to navigate to the screen — e.g.
`:features:settings:real` emits `ChipboxEvent.NavigateTo(CrashLog)` from its
debug section — or to map the route key to a `Screen` in `ChipboxScreens.kt`. The
app wires the implementation via `:features:crash-log:real`.

## Using it

```kotlin
// From a debug-section row's action handler:
emit(ChipboxEvent.NavigateTo(CrashLog))
```

## Module facts

- **Plugin:** `chipbox.feature.api`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`, with `src/main/java` (`jvmSharedMain`) + `jsMain` actuals for the formatter/decode helper
- **SAGE/module dependencies:** the public dependencies of the State/Action types, declared in `build.gradle.kts`; `kotlinx.serialization` comes from the convention plugin
