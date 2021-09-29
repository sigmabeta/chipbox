package net.sigmabeta.chipbox.player.buffer

interface ProducerBufferManager {
    suspend fun setSampleRate(sampleRate: Int)

    suspend fun getNextEmptyBuffer(): ShortArray

    suspend fun sendAudioBuffer(audioBuffer: AudioBuffer)
}