package net.sigmabeta.chipbox.player.buffer

interface ConsumerBufferManager {
    suspend fun getNextAudioBuffer(): AudioBuffer

    suspend fun recycleShortArray(data: ShortArray)
}