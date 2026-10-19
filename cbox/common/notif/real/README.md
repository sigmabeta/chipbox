# `:cbox:common:notif:real`

> Production notification persistence + the scanner-failure producer.

The `:real` implementation of the notification API: `RealNotifManager` stores the active set in SAGE
`Storage` (one JSON value), drops one-time notices on the next launch, and keeps the in-memory
`StateFlow` authoritative so a producer writing during startup can't be overwritten by the older
stored snapshot. `RealScanFailureNotifRecorder` turns a `ScannerState.Failed` into a one-time
`ERROR` notification on Home; `InsecureOriginNotifRecorder` warns when browser audio is unavailable
because the page is not a secure context.

## Contents

| File | What it is |
| --- | --- |
| `RealNotifManager.kt` | `NotifManager` over `Storage` (`notif.state`): JSON persistence, add/remove write-through, one-time cleanup on the first load. |
| `RealScanFailureNotifRecorder.kt` | `ScanFailureNotifRecorder`: collects `scanner.state()`, and on `ScannerState.Failed` adds (or replaces) one `NOTIF_SCAN_FAILED_TITLE` error notice with the failing path. Reuses one id so failures don't stack; `isOneTime = true` so it doesn't nag after a relaunch. |
| `InsecureOriginNotifRecorder.kt` | Posts one `NOTIF_INSECURE_ORIGIN_TITLE` error notice when its injected `isSecureContext` check returns false. The web graph supplies `window.isSecureContext`; other targets leave the default `true`. Reuses one id and is `isOneTime = true`. |

## Why depend on this module

The app graph pulls it in through `:cbox:common:notif:di` (Android/JVM) or the equivalent platform
providers (JS). Tests use `:cbox:common:notif:fake` instead.

## Using it

```kotlin
// Wired by NotifModule; apps call the recorders once at startup:
graph.scanFailureNotifRecorder.observe()
graph.insecureOriginNotifRecorder.observe()   // web only
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `kotlin.serialization` + `chipbox.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (impl) + `commonTest` (manager/recorder tests)
- **SAGE/module dependencies:** `:cbox:common:notif:api` (api), `:cbox:common:scanner:api`, `:cbox:common:strings:api`, `sage.common.storage.common`, `sage.common.logging`, `sage.common.ui.strings`, `kotlinx-serialization-json`
