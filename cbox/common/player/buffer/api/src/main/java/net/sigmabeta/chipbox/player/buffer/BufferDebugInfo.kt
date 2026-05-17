package net.sigmabeta.chipbox.player.buffer

/**
 * Diagnostic snapshot of buffer pool health, surfaced for the debug PlaybackStatus
 * screen via [BufferDebugSource.debugInfo]. Purely observational.
 */
data class BufferDebugInfo(
    val sampleRate: Int? = null,
    /** Pool depth — how many buffers exist for the current sample rate. */
    val capacity: Int = 0,
    /** Filled buffers waiting for the consumer (producer is ahead by this many). */
    val fullBuffersQueued: Int = 0,
    /** Empty arrays the producer can borrow. */
    val emptyArraysAvailable: Int = 0,
    /** Number of completed drain() passes (seeks / flushes). */
    val drainCount: Int = 0,
)
