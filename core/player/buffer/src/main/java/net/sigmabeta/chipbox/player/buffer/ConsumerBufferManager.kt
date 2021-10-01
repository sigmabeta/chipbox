package net.sigmabeta.chipbox.player.buffer

interface ConsumerBufferManager {
    fun checkForNextAudioBuffer(): AudioBuffer?

    suspend fun waitForNextAudioBuffer(): AudioBuffer

    suspend fun recycleShortArray(data: ShortArray)
}