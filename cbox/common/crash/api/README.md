# `:cbox:common:crash:api`

> Crash-reporting contracts — write reports on fatal exceptions, read them back later.

The `:api` module for crash reporting: it declares the `CrashReporter` (install a
process-wide uncaught-exception handler that persists a report) and
`CrashReportStore` (read/clear persisted reports) interfaces, plus the
`@Serializable` `CrashReport` written to disk as JSON. Depend on this for the
types; the JVM-family implementations live in `:real`.

## Contents

| File | What it is |
| --- | --- |
| `CrashReporter.kt` | Write side: `install()` registers the (idempotent) uncaught-exception handler that serializes a `CrashReport`, then delegates to the previously installed handler so the platform's normal crash behaviour still fires. |
| `CrashReportStore.kt` | Read side: `list()` returns persisted reports newest-first (corrupt files skipped), `clear()` deletes them. Split from the writer so the read path has no business with the exception handler. |
| `CrashReport.kt` | `@Serializable` record: timestamp, thread, exception class/message, full stack trace, app version/branch/debug flag, and `recentErrors` (a serializable mirror of `Hatchet`'s error ring buffer). |

## Why depend on this module

Depend on `:cbox:common:crash:api` from the crash-log UI (to read reports via
`CrashReportStore`) and from any code that needs the `CrashReport` type. The
writer/reader implementations are JVM-only and live in `:real`; this `:api` still
carries the (flag-gated) JS target because it is pulled into the JS graph
transitively (`appui:api` -> `crash-log:real` -> `crash:api`).

## Using it

```kotlin
class CrashLogViewModel(private val store: CrashReportStore) {
    val reports: List<CrashReport> get() = store.list()
    fun onClear() = store.clear()
}
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `kotlin.serialization`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `kotlinx-serialization-core` (api) — leaf otherwise
