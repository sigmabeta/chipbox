package net.sigmabeta.chipbox.crash

import kotlinx.serialization.Serializable

/**
 * A serialized fatal-exception record, written to disk as JSON when an uncaught exception kills the
 * app. Carries the full stack trace plus the surrounding context that makes a crash diagnosable
 * without a debugger: which thread died, the build it died on, and the tail of the error log
 * leading up to it ([recentErrors], from `Hatchet.recentErrors`).
 */
@Serializable
data class CrashReport(
    val timestampMs: Long,
    val threadName: String,
    val exceptionClass: String,
    val message: String?,
    val stackTrace: String,
    val appVersionName: String,
    val appVersionCode: Int,
    val appBuildBranch: String,
    val isDebugBuild: Boolean,
    /** The error-log ring buffer at crash time, oldest first — context preceding the crash. */
    val recentErrors: List<RecentError>,
) {
    /** A serializable mirror of `net.sigmabeta.sage.logging.HatchetError` (which isn't @Serializable). */
    @Serializable
    data class RecentError(
        val timestampMs: Long,
        val tag: String,
        val thread: String,
        val message: String,
    )
}
