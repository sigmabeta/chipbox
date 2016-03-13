package net.sigmabeta.chipbox.backend

interface BackendView {
    fun play()

    fun pause()

    fun stop()

    fun skipToNext()

    fun skipToPrev()
}
