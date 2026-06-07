package net.sigmabeta.chipbox.crash

/**
 * Reads back the crash reports [CrashReporter] persisted in previous (or the current) run. The
 * crash-log screen uses this to show what's killed the app; the writer and the reader are split so
 * the read side has no business with the uncaught-exception handler.
 */
interface CrashReportStore {
    /** Every persisted crash report, newest first. Corrupt or unreadable files are skipped. */
    fun list(): List<CrashReport>

    /** Delete all persisted crash reports. */
    fun clear()
}
