# `:features:crash-log:real`

> The debug-only crash-log screen: persisted crash reports as expandable rows.

> The screen's `State`/`Action` value types live in `:features:crash-log:api`; this module holds the ViewModel, Route and runtime helpers.

The `:real` half of the `crash-log` feature: the ViewModel, Route composable, and timestamp formatter that implement the screen whose route key
lives in `:features:crash-log:api`. `:real` modules hold the screen
implementation (a `ChipboxListViewModel` wired into the Metro `AppScope` graph,
Compose content); the app includes this module. There is no `:screenshot`
companion for this feature.

The screen reads the persisted reports from `CrashReportStore` (a fixed snapshot
of at most 20 reports, newest first) once when it opens, and renders each as a
collapsible row. Reached from the Settings debug section (gated behind
`shouldShowDebug`).

## Contents

| File | What it is |
| --- | --- |
| `CrashLogViewModel.kt` | `@ContributesIntoMap`+`@ViewModelKey`+`@Inject` VM; reads `crashReportStore.list()` into state in `init`. `handleAction` is a no-op (read-only screen). |
| `CrashLogRoute.kt` | `CrashLogRoute(onEvent, modifier)` — resolves the VM with `metroViewModel()` and hands it to `ChipboxListEntry`. |

The `CrashTimestamp` formatter lives with `CrashLogState` in `:features:crash-log:api`.

## Why depend on this module

Only the app's navigation wiring (`ChipboxScreens.kt`) depends on `:real`, to map
the `CrashLog` route key to a Voyager `Screen`. Everything else depends on `:api`
for the route key. The VM registers itself into the Metro graph via
`@ContributesIntoMap`, so no `:di` module is needed.

## Using it

```kotlin
// In ChipboxScreens.kt:
CrashLog -> CrashLogScreen        // CrashLogRoute(onEvent = LocalChipboxEventSink.current)
```

## Module facts

- **Plugin:** `chipbox.feature.real` + `chipbox.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (VM, route); `commonTest` for `CrashLogViewModelTest`. The state and timestamp formatter live in `:api`.
- **SAGE/module dependencies:** `:features:crash-log:api`; `:cbox:common:appcomm:api`, `:cbox:common:ui:list:api`, `:cbox:common:strings:api`, `:cbox:common:crash:api`; `sage.common.ui.components`, `kotlinx.collections.immutable`
