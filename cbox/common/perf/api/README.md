# `:cbox:common:perf:api`

> Multiplatform Perfetto/systrace shim — `trace { }` from shared code, real on Android, no-op elsewhere.

A tiny tracing facade: it declares `expect` trace-section functions in
`commonMain` with per-platform `actual`s, so shared code (including
`jvmSharedMain`, which has `java.*` but not `android.*`) can open Perfetto /
systrace sections without touching `android.os.Trace` directly. The Android actual
delegates to `androidx.tracing.Trace` (lands in Perfetto / macrobenchmark traces);
JVM and JS actuals are no-ops. There is no `:real`/`:di`/`:fake` — the platform
`actual` is the implementation.

## Contents

| File | What it is |
| --- | --- |
| `Trace.kt` (commonMain) | `expect` functions `traceBeginSection`/`traceEndSection` (synchronous, same-thread) and `traceBeginAsyncSection`/`traceEndAsyncSection` (overlapping, cross-thread, keyed by `label` + `cookie`), plus inline `trace { }` / `traceAsync { }` wrappers that close the section even on throw. |
| `Trace.android.kt` | `actual`s delegating to `androidx.tracing.Trace`; labels truncated to 127 chars (matching what `android.os.Trace` accepts). |
| `Trace.jvm.kt` | No-op `actual`s — no Perfetto on desktop JVM. |
| `Trace.js.kt` | No-op `actual`s — JS target is enforcement-only. |

## Why depend on this module

Depend on `:cbox:common:perf:api` from any module that wants to mark spans for
Perfetto / macrobenchmark profiling without per-platform guards — the no-op
actuals make the calls free off Android. The `androidx.tracing` dependency is
Android-only, so it doesn't leak into the JVM/JS builds.

## Using it

```kotlin
fun renderFrame() = trace("renderFrame") {
    // ...synchronous work, traced on Android, no-op on JVM/JS
}

// cross-thread / suspending work needs a unique cookie:
traceAsync("decodeTrack", cookie = trackId) { decode() }
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (expect) + `androidMain` / `jvmMain` / `jsMain` (actuals)
- **SAGE/module dependencies:** `androidx.tracing.ktx` (Android-only impl) — leaf otherwise
