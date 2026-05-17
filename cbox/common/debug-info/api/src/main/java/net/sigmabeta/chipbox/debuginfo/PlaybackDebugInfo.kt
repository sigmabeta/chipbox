package net.sigmabeta.chipbox.debuginfo

import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.buffer.BufferDebugInfo
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.director.ChipboxPlaybackState
import net.sigmabeta.chipbox.player.generator.GeneratorDebugInfo
import net.sigmabeta.chipbox.player.speaker.SpeakerDebugInfo

/**
 * Aggregate diagnostic snapshot for the debug PlaybackStatus screen. Combines the
 * Director's reduced view (track / playback / session) with per-component diagnostics
 * collected by [DebugInfoManager].
 */
data class PlaybackDebugInfo(
    val track: Track? = null,
    val playback: ChipboxPlaybackState? = null,
    val session: Session? = null,
    val generator: GeneratorDebugInfo? = null,
    val speaker: SpeakerDebugInfo? = null,
    val buffer: BufferDebugInfo? = null,
)
