package net.sigmabeta.chipbox.player.generator

/**
 * Diagnostic snapshot of the [Generator]'s producer loop, surfaced for the debug
 * PlaybackStatus screen via [Generator.debugInfo]. Purely observational — nothing here
 * feeds back into playback.
 */
data class GeneratorDebugInfo(
    val currentTrackId: Long? = null,
    val currentTrackTitle: String? = null,
    val sampleRate: Int? = null,
    /** Generator high-water mark within the current track, in ms (mirrors [GeneratorEvent.Emitting]). */
    val producedMs: Long = 0L,
    val framesPlayed: Int = 0,
    /** True while the generation loop coroutine is active. */
    val looping: Boolean = false,
    val lastEvent: GeneratorEvent? = null,
    val lastError: String? = null,
    /** Last non-null [net.sigmabeta.chipbox.player.cache.PcmTrackSource.getDiagnostics] string. */
    val sourceDiagnostics: String? = null,
)
