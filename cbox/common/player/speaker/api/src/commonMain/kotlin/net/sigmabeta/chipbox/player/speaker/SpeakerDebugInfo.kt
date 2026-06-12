package net.sigmabeta.chipbox.player.speaker

import net.sigmabeta.chipbox.player.common.VolumeDebugInfo
import net.sigmabeta.chipbox.player.resampler.ResamplerDebugInfo

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
    /** Gain state of the speaker's [net.sigmabeta.chipbox.player.common.VolumeProcessor],
     *  sampled after the most recent buffer was processed. */
    val volume: VolumeDebugInfo? = null,
    /** In-app resampler stage feeding the sink, or null for sinks that don't resample (test/file
     *  sinks, and the JVM `SourceDataLine` sink that leaves rate conversion to the OS mixer). */
    val resampler: ResamplerDebugInfo? = null,
)
