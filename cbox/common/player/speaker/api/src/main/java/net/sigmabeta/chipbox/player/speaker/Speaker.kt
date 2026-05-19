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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.common.VolumeProcessor
import net.sigmabeta.chipbox.player.common.framesToMillis
import net.sigmabeta.sage.logging.Hatchet

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
        protected val hatchet: Hatchet,
        dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val speakerScope = CoroutineScope(SupervisorJob() + dispatcher)

    fun release() {
        speakerScope.cancel()
    }

    private var ongoingPlaybackJob: Job? = null

    /**
     * Volume adjustments applied to every consumed buffer: the end-of-track fade-out plus any
     * persistent modifications (OS ducking, master volume, …). Shared for this speaker's
     * lifetime; mutated from the director/audio-focus side via the pass-through methods below.
     */
    private val volumeProcessor = VolumeProcessor(hatchet)

    /** Duck output to 50% while [ducked] (transient OS audio-focus loss), restoring it after. */
    fun setDucked(ducked: Boolean) = volumeProcessor.setDucked(ducked)

    /** Set an arbitrary master output volume ([scale] = 1.0 unchanged, 1.5 = +50%, 0.0 silent).
     *  Independent of the fade-out and of ducking. */
    fun setVolume(scale: Double) = volumeProcessor.setMasterVolume(scale)

    /** Register an arbitrary, independently-keyed volume modification. No UI yet — API only. */
    fun setVolumeModification(key: String, scale: Double) =
        volumeProcessor.setModification(key, scale)

    /** Remove a previously registered [setVolumeModification]. */
    fun clearVolumeModification(key: String) = volumeProcessor.clearModification(key)

    /**
     * Track id of the most recently consumed [AudioBuffer]. Hoisted out of the playback loop so
     * it survives [seek]'s cancel-and-restart cycle — without this the post-seek loop would
     * treat the first buffer as an initial track and suppress its [SpeakerEvent.TrackChange],
     * causing the now-playing UI to miss skip-forward/back updates. Reset only on full teardown.
     */
    private var playingTrackId: Long? = null

    /**
     * Peak amplitude the [volumeProcessor]'s normalization is currently configured for. The
     * generator stamps a *live* peak on every buffer — for a render-ahead source it climbs
     * over the first buffers then settles — so the gain is re-derived whenever this value
     * changes (it only ever rises, so the gain only steps down, never pumps). `-1` is a
     * "nothing applied yet" sentinel (a real peak is always `>= 0`); reset on full teardown.
     */
    private var appliedNormalizationPeak: Int = -1

    private val eventSink = MutableSharedFlow<SpeakerEvent>(
        replay = 0,
        onBufferOverflow = BufferOverflow.SUSPEND,
        extraBufferCapacity = 10
    )

    fun events() = eventSink.asSharedFlow()

    private val debugInfoMutable = MutableStateFlow(SpeakerDebugInfo())

    /** Observational diagnostics for the debug PlaybackStatus screen. */
    fun debugInfo(): StateFlow<SpeakerDebugInfo> = debugInfoMutable.asStateFlow()

    private fun updateDebug(block: (SpeakerDebugInfo) -> SpeakerDebugInfo) {
        debugInfoMutable.value = block(debugInfoMutable.value)
    }

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
        playingTrackId = null
        appliedNormalizationPeak = -1

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
        hatchet.i("seek(): cancelling consume loop.")
        ongoingPlaybackJob?.cancelAndJoin()
        ongoingPlaybackJob = null
        hatchet.i("seek(): consume loop cancelled, draining buffers.")
        bufferManager.drain()
        hatchet.i("seek(): drained, flushing sink.")
        flushSink()
        hatchet.i("seek(): sink flushed, restarting consume loop (playingTrackId=$playingTrackId).")
        startPlayback()
        hatchet.i("seek(): startPlayback returned.")
    }

    protected fun emitError(error: String) {
        val errorEvent = SpeakerEvent.Error(error)
        updateDebug { it.copy(lastEvent = errorEvent, lastError = error) }
        eventSink.tryEmit(errorEvent)
    }

    private fun startPlayback() {
        if (ongoingPlaybackJob != null) {
            hatchet.w("startPlayback called but consume loop already running.")
            return
        }
        ongoingPlaybackJob = speakerScope.launch { runConsumeLoop() }
    }

    private suspend fun runConsumeLoop() {
        hatchet.i("Consume loop entering (playingTrackId=$playingTrackId).")
        updateDebug { it.copy(consumeLoopRunning = true) }
        try {
            onResumed()

            while (true) {
                yield()
                val audioBuffer = nextBufferOrAwait()
                emitTrackChangeIfNeeded(audioBuffer)

                val playingEvent = SpeakerEvent.Playing(currentPositionMs())
                updateDebug {
                    it.copy(lastEvent = playingEvent, positionMs = playingEvent.positionMs)
                }
                eventSink.emit(playingEvent)

                if (audioBuffer.peakAmplitude != appliedNormalizationPeak) {
                    volumeProcessor.setNormalization(audioBuffer.peakAmplitude)
                    appliedNormalizationPeak = audioBuffer.peakAmplitude
                }

                volumeProcessor.process(
                    audioBuffer.data,
                    audioBuffer.sampleRate,
                    audioBuffer.frameIndex.toInt().framesToMillis(audioBuffer.sampleRate),
                    audioBuffer.fadeStartMs.toDouble(),
                    audioBuffer.fadeLengthMs.toDouble(),
                )
                updateDebug { it.copy(volume = volumeProcessor.debugSnapshot()) }

                onAudioReceived(audioBuffer)
                bufferManager.recycleShortArray(audioBuffer.data)
            }
        } finally {
            hatchet.i("Consume loop exiting (playingTrackId=$playingTrackId).")
            updateDebug { it.copy(consumeLoopRunning = false) }
        }
    }

    /**
     * Return an immediately-available buffer, or emit [SpeakerEvent.Buffering] (an underrun)
     * and suspend until the next one arrives.
     */
    private suspend fun nextBufferOrAwait(): AudioBuffer {
        bufferManager.checkForNextAudioBuffer()?.let { return it }

        hatchet.d("Consume: no buffer ready, emitting Buffering and awaiting.")
        val bufferingEvent = SpeakerEvent.Buffering(currentPositionMs())
        updateDebug {
            it.copy(
                lastEvent = bufferingEvent,
                positionMs = bufferingEvent.positionMs,
                underrunCount = it.underrunCount + 1,
            )
        }
        eventSink.emit(bufferingEvent)
        val awaited = bufferManager.waitForNextAudioBuffer()
        hatchet.d("Consume: awaited buffer arrived (trackId=${awaited.trackId}).")
        return awaited
    }

    /**
     * Emit [SpeakerEvent.TrackChange] when [audioBuffer] starts a different track than the one
     * being played — except for the first buffer of a run, which only seeds [playingTrackId].
     */
    private suspend fun emitTrackChangeIfNeeded(audioBuffer: AudioBuffer) {
        if (audioBuffer.trackId == playingTrackId) return

        // New track starting: drop any in-progress gain ramp so this track's gain fades in
        // from unity rather than from the previous track's faded/ducked state.
        volumeProcessor.resetGain()

        if (playingTrackId != null) {
            hatchet.i("Emitting TrackChange: $playingTrackId -> ${audioBuffer.trackId}.")
            val trackChangeEvent = SpeakerEvent.TrackChange(audioBuffer.trackId)
            updateDebug {
                it.copy(
                    lastEvent = trackChangeEvent,
                    playingTrackId = audioBuffer.trackId,
                )
            }
            eventSink.emit(trackChangeEvent)
        } else {
            hatchet.d(
                "First buffer this run; setting playingTrackId=${audioBuffer.trackId} without emit."
            )
        }

        playingTrackId = audioBuffer.trackId
        updateDebug { it.copy(playingTrackId = audioBuffer.trackId) }
    }
}
