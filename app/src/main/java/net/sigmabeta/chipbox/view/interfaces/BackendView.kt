package net.sigmabeta.chipbox.view.interfaces

import net.sigmabeta.chipbox.model.objects.Track

interface BackendView {
    fun play(track: Track)

    fun pause()

    fun stop()

    fun skipToNext()

    fun skipToPrev()
}
