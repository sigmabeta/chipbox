package net.sigmabeta.chipbox.player.speaker

/**
 * Diagnostic snapshot of the [Speaker]'s consume loop, surfaced for the debug
 * PlaybackStatus screen via [Speaker.debugInfo]. Purely observational.
 */
data class SpeakerDebugInfo(
    val playingTrackId: Long? = null,
    /** Sink play head in ms, sampled on the most recent buffer write. */
    val positionMs: Long = 0L,
    /** True while the consume loop coroutine is active. */
    val consumeLoopRunning: Boolean = false,
    val lastEvent: SpeakerEvent? = null,
    /** Count of [SpeakerEvent.Buffering] events (queue-empty underruns) this run. */
    val underrunCount: Int = 0,
    val lastError: String? = null,
)
