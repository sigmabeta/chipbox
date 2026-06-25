# `:features:error-log:real`

> The debug-only error-log screen: Hatchet's recent-errors buffer as a list.

The `:real` half of the `error-log` feature: the ViewModel, state, Route
composable, and timestamp formatter that implement the screen whose route key
lives in `:features:error-log:api`. `:real` modules hold the screen
implementation (a `ChipboxListViewModel` wired into the Metro `AppScope` graph,
Compose content); the app includes this module. There is no `:screenshot`
companion for this feature.

The screen snapshots `Hatchet.recentErrors` (the logger's last-16 in-memory ring
buffer, reversed so newest is first) once when it opens, and renders each as a
name+caption row. Reached from the Settings debug section (gated behind
`shouldShowDebug`).

## Contents

| File | What it is |
| --- | --- |
| `ErrorLogViewModel.kt` | `@ContributesIntoMap`+`@ViewModelKey`+`@Inject` VM; reads `hatchet.recentErrors.reversed()` into state in `init`. `handleAction` is a no-op (read-only screen). |
| `ErrorLogState.kt` | Maps each `HatchetError` to a `NameCaptionListModel` (message as name; `"12:34:56 · tag · thread"` caption, blank pieces dropped). `dataId` is the list index. Empty list renders an `EmptyStateListModel`. |
| `ErrorLogRoute.kt` | `ErrorLogRoute(onEvent, modifier)` — resolves the VM with `metroViewModel()` and hands it to `ChipboxListEntry`. |
| `ErrorTimestamp.kt` | `expect formatErrorTimestamp(epochMs)` — wall-clock **time-of-day** (`"12:34:56"`). JVM/Android `actual` (`src/main/java`) uses `java.time`; the JS `actual` returns the raw epoch string. |

## Why depend on this module

Only the app's navigation wiring (`ChipboxScreens.kt`) depends on `:real`, to map
the `ErrorLog` route key to a Voyager `Screen`. Everything else depends on `:api`
for the route key. The VM registers itself into the Metro graph via
`@ContributesIntoMap`, so no `:di` module is needed.

## Using it

```kotlin
// In ChipboxScreens.kt:
ErrorLog -> ErrorLogScreen        // ErrorLogRoute(onEvent = LocalChipboxEventSink.current)
```

## Module facts

- **Plugin:** `chipbox.feature.real` + `chipbox.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain` (VM, state, route); `src/main/java` (`jvmSharedMain`) + `jsMain` for the timestamp `actual`; `commonTest` for `ErrorLogViewModelTest`
- **SAGE/module dependencies:** `:features:error-log:api`; `:cbox:common:appcomm:api`, `:cbox:common:ui:list:api`, `:cbox:common:strings:api`; `sage.common.ui.components`
