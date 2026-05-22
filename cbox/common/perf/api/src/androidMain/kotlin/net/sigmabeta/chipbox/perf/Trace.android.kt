package net.sigmabeta.chipbox.perf

import androidx.tracing.Trace

// android.os.Trace silently truncates labels longer than this; cap ourselves so the Perfetto
// section name matches what we pass.
private const val MAX_LABEL_LENGTH = 127

actual fun traceBeginSection(label: String) {
    Trace.beginSection(label.take(MAX_LABEL_LENGTH))
}

actual fun traceEndSection() {
    Trace.endSection()
}

actual fun traceBeginAsyncSection(label: String, cookie: Int) {
    Trace.beginAsyncSection(label.take(MAX_LABEL_LENGTH), cookie)
}

actual fun traceEndAsyncSection(label: String, cookie: Int) {
    Trace.endAsyncSection(label.take(MAX_LABEL_LENGTH), cookie)
}
