# `:cbox:common:notif:di`

> Metro wiring for notifications — manager + scanner-failure producer into `AppScope`.

The `:di` module for notifications. Its `@BindingContainer` provides the `NotifManager`
(`RealNotifManager`) and the `ScanFailureNotifRecorder` (`RealScanFailureNotifRecorder`) as app-scope
singletons. Android and JVM include it directly; the browser wires the same implementations in
`WebModules`.

## Contents

| File | What it is |
| --- | --- |
| `NotifModule.kt` | `@BindingContainer @ContributesTo(AppScope::class)` providing `NotifManager` (storage + app scope + logger) and `ScanFailureNotifRecorder` (scanner + manager + strings + logger). |

## Why depend on this module

The app graphs include `:di` so Home's `NotifHomeModule` can resolve `NotifManager` and the app can
call `scanFailureNotifRecorder.observe()` at startup.

## Using it

```kotlin
// In ChipboxApplication.onCreate / Jvm Main / JsMain:
graph.scanFailureNotifRecorder.observe()
```

## Module facts

- **Plugin:** `sage.jvm` + `sage.di`
- **Targets:** JVM (plain binding container; bound impls are KMP)
- **Source set:** `src/main/java`
- **SAGE/module dependencies:** `:cbox:common:notif:api`, `:cbox:common:notif:real`, `:cbox:common:scanner:api`, `sage.common.storage.common`, `sage.common.logging`, `sage.common.ui.strings`
