package net.sigmabeta.chipbox.player.speaker

import kotlinx.coroutines.*
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager

abstract class Speaker(
        private val bufferManager: ConsumerBufferManager,
        dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val speakerScope = CoroutineScope(dispatcher)

    private var ongoingPlaybackJob: Job? = null

    fun play() {
        startPlayback()
    }

    abstract fun onAudioReceived(audio: AudioBuffer)

    abstract fun teardown()

    private fun startPlayback() {
        if (ongoingPlaybackJob == null) {
            ongoingPlaybackJob = speakerScope.launch {
                delay(50)

                while (true) {
                    yield()
                    val audioBuffer = bufferManager.getNextAudioBuffer()

                    onAudioReceived(audioBuffer)
                    bufferManager.recycleShortArray(audioBuffer.data)
                }
            }
        }
    }
}
