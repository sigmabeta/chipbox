package net.sigmabeta.chipbox.perf

// No Perfetto / systrace on the desktop JVM target — every section is a no-op. Kept as a thin
// actual (rather than dropping the calls at the call site) so shared code can trace
// unconditionally without per-platform guards.
actual fun traceBeginSection(label: String) = Unit

actual fun traceEndSection() = Unit

actual fun traceBeginAsyncSection(label: String, cookie: Int) = Unit

actual fun traceEndAsyncSection(label: String, cookie: Int) = Unit
