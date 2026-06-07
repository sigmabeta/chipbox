package net.sigmabeta.chipbox.crash

/**
 * Persists details of a fatal (uncaught) exception so the crash can be inspected after the fact —
 * the process is gone by the next launch, so this is the only record of what killed it.
 *
 * The real implementation installs a process-wide uncaught-exception handler that serializes a
 * [CrashReport] to storage, then delegates to whatever handler was already installed (so the
 * platform's normal "process died" behaviour — Android's crash dialog, the JVM stack-trace dump —
 * still happens).
 */
interface CrashReporter {
    /**
     * Install the uncaught-exception handler. Idempotent: a second call is a no-op, so it's safe to
     * call from each platform's startup path without guarding.
     */
    fun install()
}
