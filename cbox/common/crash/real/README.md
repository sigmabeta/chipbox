# `:cbox:common:crash:real`

> Production crash reporter/store — synchronous JSON-on-disk writes from a JVM-family handler.

The `:real` module for crash reporting: `RealCrashReporter` installs a
process-wide uncaught-exception handler that synchronously writes a `CrashReport`
JSON file before the process dies, and `RealCrashReportStore` reads those files
back. Both are JVM-family (`java.io.File`) so they live in `jvmSharedMain`
(`src/main/java`), shared by Android and desktop. There is no `:di` module —
the app constructs these directly with a crash directory and `AppInfo` / `Hatchet`.

## Contents

| File | What it is |
| --- | --- |
| `RealCrashReporter.kt` | Implements `CrashReporter`. `install()` (idempotent via `AtomicBoolean`) registers the handler; on crash it writes each report to its own `crash-<ts>.json` via a temp file + atomic move (plain rename fallback), prunes to the most recent 20, then delegates to the previous handler. Writes are fully synchronous because the async `Storage` can't promise bytes hit disk in a dying process. |
| `RealCrashReportStore.kt` | Implements `CrashReportStore` over the same directory. Decodes with `ignoreUnknownKeys` (forward-compatible) and skips/logs corrupt files so one bad write can't hide every other crash. |
| `CrashReportFiles.kt` | `internal` shared naming scheme (`crash-<ts>.json`, list helper) so writer and reader can't drift on what a report file is called. |

Tests (`jvmTest`): `RealCrashReporterTest`, `RealCrashReportStoreTest`.

## Why depend on this module

The app's startup wiring depends on `:cbox:common:crash:real` to construct and
`install()` the reporter and to provide a `CrashReportStore`. UI/consumer code
depends on `:cbox:common:crash:api` for the interfaces and `CrashReport` type, not
on `:real`.

## Using it

```kotlin
val reporter = RealCrashReporter(crashDir, appInfo, hatchet)
reporter.install() // call once on startup

val store = RealCrashReportStore(crashDir, hatchet)
val crashes: List<CrashReport> = store.list()
```

## Module facts

- **Plugin:** `sage.kmp` + `kotlin.serialization` + `chipbox.kmp.test`
- **Targets:** Android + JVM (no JS — the impls are JVM-only)
- **Source set:** `src/main/java` (`jvmSharedMain`); tests in `jvmTest`
- **SAGE/module dependencies:** `:cbox:common:crash:api` (api); `kotlinx-serialization-json`, `sage.common.appinfo`, `sage.common.logging` (impl)
