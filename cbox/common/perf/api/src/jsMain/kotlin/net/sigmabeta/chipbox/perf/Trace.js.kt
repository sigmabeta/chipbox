package net.sigmabeta.chipbox.perf

// No Perfetto / systrace on the JS target (enforcement-only); every section is a no-op, same as
// the desktop JVM actual.
actual fun traceBeginSection(label: String) = Unit

actual fun traceEndSection() = Unit

actual fun traceBeginAsyncSection(label: String, cookie: Int) = Unit

actual fun traceEndAsyncSection(label: String, cookie: Int) = Unit
