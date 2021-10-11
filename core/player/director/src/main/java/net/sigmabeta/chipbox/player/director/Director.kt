package net.sigmabeta.chipbox.player.director

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.models.Track

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

    fun duck() // Quack

    fun resumeFocus()
}