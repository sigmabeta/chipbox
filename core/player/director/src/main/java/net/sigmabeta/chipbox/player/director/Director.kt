package net.sigmabeta.chipbox.player.director

import kotlinx.coroutines.flow.SharedFlow
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.Session

interface Director {
    // Controls

    fun start(session: Session)

    fun play()

    fun pause()

    fun stop()

    // State Updates

    fun metadataState(): SharedFlow<Track>

    fun playbackState(): SharedFlow<ChipboxPlaybackState>

    // Audio Focus

    fun pauseTemporarily()

    fun duck() // 🦆

    fun resumeFocus()
}