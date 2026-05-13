package net.sigmabeta.chipbox.player.speaker

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager

/**
 * Consumer side of the playback pipeline. Pulls [AudioBuffer]s off the [bufferManager] and
 * hands them to a subclass-supplied sink — speakers ([net.sigmabeta.chipbox.player.speaker.real.RealSpeaker]
 * via Android `AudioTrack`), a WAV file ([net.sigmabeta.chipbox.player.speaker.file.FileSpeaker]),
 * or stdout ([net.sigmabeta.chipbox.player.speaker.text.TextSpeaker]).
 *
 * ### Threading
 * The consume loop runs as a single coroutine on [dispatcher]. The loop never terminates on its
 * own; it's stopped via [stop], which cancels the coroutine and tears down the sink.
 *
 * ### Buffer recycling
 * Each consumed [AudioBuffer]'s `data` array is returned to the buffer manager via
 * [ConsumerBufferManager.recycleShortArray] so the producer can reuse it. Subclasses must
 * finish reading from `audio.data` before [onAudioReceived] returns — the array becomes
 * available to the producer immediately afterward.
 */
abstract class Speaker(
        private val bufferManager: ConsumerBufferManager,
        dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val speakerScope = CoroutineScope(SupervisorJob() + dispatcher)

    fun release() {
        speakerScope.cancel()
    }

    private var ongoingPlaybackJob: Job? = null

    private val eventSink = MutableSharedFlow<SpeakerEvent>(
        replay = 0,
        onBufferOverflow = BufferOverflow.SUSPEND,
        extraBufferCapacity = 10
    )

    fun events() = eventSink.asSharedFlow()

    /**
     * Milliseconds played within the currently-loaded track, derived from each [AudioBuffer]'s
     * [net.sigmabeta.chipbox.player.buffer.AudioBuffer.frameIndex] and the sink's own play head.
     * Returns 0 for sinks that don't track a play head (e.g. test/file sinks).
     */
    open fun currentPositionMs(): Long = 0L

    fun play() {
        startPlayback()
    }

    suspend fun pause() {
        ongoingPlaybackJob?.cancelAndJoin()
        ongoingPlaybackJob = null

        onPaused()
    }

    suspend fun stop() {
        ongoingPlaybackJob?.cancelAndJoin()
        ongoingPlaybackJob = null

        teardown()
    }

    protected open fun onPaused() = Unit

    protected open fun onResumed() = Unit

    /** Called on the speaker coroutine for each buffer pulled from the queue. Must complete
     *  synchronously — `audio.data` is recycled as soon as this returns. */
    abstract fun onAudioReceived(audio: AudioBuffer)

    /** Release any sink-specific resources (audio track, file handle, etc). Called from
     *  [stop] after the consume loop is cancelled. */
    abstract fun teardown()

    /** Discard any audio buffered inside the sink itself (e.g. AudioTrack's hardware buffer)
     *  so a seek isn't preceded by stale frames already on the way to the speaker. Subclasses
     *  that don't buffer downstream audio (test/debug sinks) should leave this as a no-op. */
    open fun flushSink() = Unit

    /**
     * Cancel the consume loop, drain queued buffers, flush the sink, and restart consumption.
     * Used by the director during seek so the next buffer the consumer sees is from the
     * post-seek position. Pre-seek audio that was already in flight is discarded.
     */
    suspend fun seek() {
        ongoingPlaybackJob?.cancelAndJoin()
        ongoingPlaybackJob = null
        bufferManager.drain()
        flushSink()
        startPlayback()
    }

    protected fun emitError(error: String) {
        eventSink.tryEmit(
            SpeakerEvent.Error(error)
        )
    }

    private fun startPlayback() {
        if (ongoingPlaybackJob == null) {
            ongoingPlaybackJob = speakerScope.launch {
                onResumed()
                var playingTrackId: Long? = null

                while (true) {
                    yield()
                    var audioBuffer = bufferManager.checkForNextAudioBuffer()

                    if (audioBuffer == null) {
                        eventSink.emit(SpeakerEvent.Buffering(currentPositionMs()))
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

                    eventSink.emit(SpeakerEvent.Playing(currentPositionMs()))

                    onAudioReceived(audioBuffer)
                    bufferManager.recycleShortArray(audioBuffer.data)
                }
            }
        }
    }
}
