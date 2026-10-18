package net.sigmabeta.chipbox.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `/api/health` wire contract, shared by the server (producer) and the browser (consumer).
 *
 * The server owns the library and scans it in the background on startup. The browser polls this
 * endpoint while its first library read is still filling so a client opened during the initial
 * scan paints the partial library immediately and re-reads once the scan finishes instead of
 * staying empty forever.
 *
 * No filesystem path from the host is ever included: [ScanState.failedFile] is a basename, so the
 * unauthenticated endpoint can't leak the library layout.
 */
@Serializable
data class HealthStatus(
    /** False only when the scan ended in a fatal failure; a running scan is still a healthy server. */
    val ok: Boolean,
    val state: ScanState,
    val games: Int = 0,
    val tracks: Int = 0,
    val tracksFailed: Int = 0,
) {
    val scanning: Boolean get() = state.scanning

    /**
     * The server may still fill the library: a scan is running, or it has not reported a state yet
     * (`unknown` at startup). Used by the browser's first-read retry so an early `unknown` does not
     * stop the wait before the scan even starts.
     */
    val scanInProgress: Boolean
        get() = state.status == ScanStatus.SCANNING || state.status == ScanStatus.UNKNOWN
}

@Serializable
data class ScanState(
    val status: ScanStatus,
    val timeInSeconds: Int = 0,
    val failedFile: String? = null,
) {
    val scanning: Boolean get() = status == ScanStatus.SCANNING
}

@Serializable
enum class ScanStatus {
    @SerialName("unknown")
    UNKNOWN,

    @SerialName("idle")
    IDLE,

    @SerialName("scanning")
    SCANNING,

    @SerialName("complete")
    COMPLETE,

    @SerialName("failed")
    FAILED,
}

/**
 * Bounded polling policy for the browser's initial library read.
 *
 * Retry while the server may still fill the library (a scan is running, or the state is still
 * `unknown` at startup) **and** the read is not yet final. [maxAttempts] at [delayMillis] apart
 * bounds the wait, so a slow or wedged scan ends with the last read instead of a screen that spins
 * forever. A finished/failed scan or an unreachable health endpoint stops immediately.
 */
data class ScanRetryPolicy(
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    val delayMillis: Long = DEFAULT_DELAY_MILLIS,
) {
    init {
        require(maxAttempts >= 0) { "maxAttempts must be >= 0, was $maxAttempts" }
        require(delayMillis >= 0) { "delayMillis must be >= 0, was $delayMillis" }
    }

    fun shouldRetry(health: HealthStatus?, attempt: Int): Boolean =
        health != null && health.scanInProgress && attempt < maxAttempts

    companion object {
        /** ~60s of polling at the default delay — enough for a modest library's first scan. */
        const val DEFAULT_MAX_ATTEMPTS = 60
        const val DEFAULT_DELAY_MILLIS = 1_000L
    }
}
