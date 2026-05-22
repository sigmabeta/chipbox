package net.sigmabeta.chipbox.perf

/*
 * Multiplatform Perfetto / systrace shim.
 *
 * android.os.Trace is unreachable from most chipbox code: the sage.kmp plugin maps the
 * legacy src/main/java of each module onto the jvmSharedMain source set, which is compiled
 * for both the Android app and the desktop JVM target. That set has java.* but not android.*,
 * so it can't call the platform trace API directly. This module declares the API in commonMain
 * and provides per-platform actuals: Android delegates to androidx.tracing.Trace (which lands
 * in Perfetto / macrobenchmark traces); the JVM/desktop actual is a no-op since there's no
 * Perfetto there.
 *
 * Wrap synchronous, single-threaded work with [trace]. For overlapping or suspending work — where
 * begin and end happen on different threads or interleave — use [traceAsync] (or the raw
 * [traceBeginAsyncSection] / [traceEndAsyncSection]) with a unique cookie per concurrent span.
 */

/** Open a synchronous trace section. Must be balanced by [traceEndSection] on the same thread. */
expect fun traceBeginSection(label: String)

/** Close the most recently opened synchronous trace section on this thread. */
expect fun traceEndSection()

/**
 * Open an asynchronous trace section, identified by [label] + [cookie]. Unlike [traceBeginSection]
 * these may overlap and need not begin/end on the same thread; pair with [traceEndAsyncSection]
 * using the same [label] and [cookie].
 */
expect fun traceBeginAsyncSection(label: String, cookie: Int)

/** Close the asynchronous trace section identified by [label] + [cookie]. */
expect fun traceEndAsyncSection(label: String, cookie: Int)

/**
 * Run [block] inside a synchronous trace section named [label]. Inlined so it adds no call
 * overhead and the section is closed even if [block] throws. Defined here (not `expect`) so it
 * resolves to the right platform `actual`s for callers in `jvmSharedMain`.
 */
inline fun <T> trace(label: String, block: () -> T): T {
    traceBeginSection(label)
    try {
        return block()
    } finally {
        traceEndSection()
    }
}

/**
 * Run [block] inside an asynchronous trace section named [label]. Use for suspending or
 * cross-thread work; [cookie] must be unique across concurrently-open sections sharing [label].
 */
inline fun <T> traceAsync(label: String, cookie: Int, block: () -> T): T {
    traceBeginAsyncSection(label, cookie)
    try {
        return block()
    } finally {
        traceEndAsyncSection(label, cookie)
    }
}
