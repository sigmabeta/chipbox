package net.sigmabeta.chipbox.player.speaker

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager

abstract class Speaker(
        private val bufferManager: ConsumerBufferManager,
        dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val speakerScope = CoroutineScope(dispatcher)

    private var ongoingPlaybackJob: Job? = null

    private val eventSink = MutableSharedFlow<SpeakerEvent>(
        replay = 0,
        onBufferOverflow = BufferOverflow.SUSPEND,
        extraBufferCapacity = 10
    )


    fun events() = eventSink.asSharedFlow()

    fun play() {
        startPlayback()
    }

    fun pause() {
        ongoingPlaybackJob?.cancel()
        ongoingPlaybackJob = null
    }

    suspend fun stop() {
        ongoingPlaybackJob?.cancelAndJoin()
        ongoingPlaybackJob = null

        teardown()
    }

    abstract fun onAudioReceived(audio: AudioBuffer)

    abstract fun teardown()

    protected fun emitError(error: String) {
        eventSink.tryEmit(
            SpeakerEvent.Error(error)
        )
    }

    private fun startPlayback() {
        if (ongoingPlaybackJob == null) {
            ongoingPlaybackJob = speakerScope.launch {
                var playingTrackId: Long? = null

                while (true) {
                    yield()
                    var audioBuffer = bufferManager.checkForNextAudioBuffer()

                    if (audioBuffer == null) {
                        eventSink.emit(SpeakerEvent.Buffering)
                        audioBuffer = bufferManager.waitForNextAudioBuffer()
                    }

                    if (audioBuffer.trackId != playingTrackId) {
                        if (playingTrackId != null) {
                            eventSink.emit(
                                SpeakerEvent.TrackChange(audioBuffer.trackId)
                            )
                        }

                        playingTrackId = audioBuffer.trackId
                    }

                    eventSink.emit(SpeakerEvent.Playing)

                    onAudioReceived(audioBuffer)
                    bufferManager.recycleShortArray(audioBuffer.data)
                }
            }
        }
    }
}
