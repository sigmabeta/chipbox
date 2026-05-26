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
     * Set by [switchTo] for the duration of a forced track change (skip / error-skip / a
     * resumed session jumping tracks). While non-null the consume loop discards — and recycles —
     * every dequeued buffer whose `trackId` doesn't match, until the target track's first buffer
     * arrives. That's what makes a skip robust against a stale buffer from the *outgoing* track
     * leaking past the director's drain: the speaker never writes an old-track buffer to a sink
     * that's mid-rate-swap, which is what used to wedge `SourceDataLine.write()` and strand the
     * director waiting for audio that never came. Cleared the instant the target is reached. Only touched on the
     * consume coroutine and in [switchTo] (which has cancel-joined that coroutine first), so a
     * plain var is safe.
     */
    private var pendingTargetTrackId: Long? = null

    /**
     * (LUFS, dBTP) pair the [volumeProcessor]'s normalization is currently configured for. The
     * generator stamps live BS.1770 figures on every buffer — for a render-ahead source they
     * climb over the first 400 ms then settle — so the gain is re-derived whenever either
     * changes. `NaN` LUFS is the "nothing applied yet" sentinel (a real LUFS is always finite
     * and `<= 0`); reset on full teardown.
     */
    private var appliedNormalizationLufs: Double = Double.NaN
    private var appliedNormalizationTruePeakDbtp: Double = Double.NaN

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
        pendingTargetTrackId = null
        appliedNormalizationLufs = Double.NaN
        appliedNormalizationTruePeakDbtp = Double.NaN

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
     * Used by the director during an in-track seek so the next buffer the consumer sees is from
     * the post-seek position. Pre-seek audio that was already in flight is discarded. The track
     * isn't changing, so no buffer is filtered out.
     */
    suspend fun seek() = drainAndRestart(targetTrackId = null, label = "seek")

    /**
     * Like [seek], but for a forced *track* change (skip, error-skip, resumed session jumping
     * tracks): the consume loop discards every buffer that isn't [trackId] until that track's
     * first buffer arrives, then announces the change. Lets the director cut over immediately
     * without racing the generator's track load / sample-rate swap — a straggler buffer from the
     * outgoing track is dropped rather than written to a sink that's about to change rate.
     */
    suspend fun switchTo(trackId: Long) =
        drainAndRestart(targetTrackId = trackId, label = "switchTo($trackId)")

    private suspend fun drainAndRestart(targetTrackId: Long?, label: String) {
        hatchet.i("$label: cancelling consume loop.")
        ongoingPlaybackJob?.cancelAndJoin()
        ongoingPlaybackJob = null
        hatchet.i("$label: consume loop cancelled, draining buffers.")
        bufferManager.drain()
        hatchet.i("$label: drained, flushing sink.")
        flushSink()
        pendingTargetTrackId = targetTrackId
        hatchet.i("$label: sink flushed, restarting consume loop (playingTrackId=$playingTrackId).")
        startPlayback()
        hatchet.i("$label: startPlayback returned.")
    }

    protected fun emitError(error: String) {
        val errorEvent = SpeakerEvent.Error(error)
        updateDebug { it.copy(lastEvent = errorEvent, lastError = error) }
        eventSink.tryEmit(errorEvent)
    }

    /** Double equality that treats NaN-as-NaN as the same value (so an unmeasured buffer
     *  doesn't bounce the gain between calls), matching the contract of [Double.compareTo]. */
    private fun sameDouble(a: Double, b: Double): Boolean = a.compareTo(b) == 0

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

                val target = pendingTargetTrackId
                if (target != null) {
                    if (audioBuffer.trackId != target) {
                        hatchet.d(
                            "Discarding pre-switch buffer (track=${audioBuffer.trackId}, " +
                                "awaiting target $target)."
                        )
                        bufferManager.recycleShortArray(audioBuffer.data)
                        continue
                    }
                    // Reached the track we were told to switch to. Stop filtering and announce
                    // the change unconditionally so the now-playing metadata refreshes even if the
                    // id coincides with the outgoing one (e.g. the same track twice in a setlist).
                    pendingTargetTrackId = null
                    announceTrackChange(audioBuffer.trackId)
                } else {
                    emitTrackChangeIfNeeded(audioBuffer)
                }

                val playingEvent = SpeakerEvent.Playing(currentPositionMs())
                updateDebug {
                    it.copy(lastEvent = playingEvent, positionMs = playingEvent.positionMs)
                }
                eventSink.emit(playingEvent)

                if (!sameDouble(audioBuffer.loudnessLufs, appliedNormalizationLufs) ||
                    !sameDouble(audioBuffer.truePeakDbtp, appliedNormalizationTruePeakDbtp)
                ) {
                    volumeProcessor.setNormalization(
                        audioBuffer.loudnessLufs,
                        audioBuffer.truePeakDbtp,
                    )
                    appliedNormalizationLufs = audioBuffer.loudnessLufs
                    appliedNormalizationTruePeakDbtp = audioBuffer.truePeakDbtp
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

    /**
     * Unconditionally emit [SpeakerEvent.TrackChange] for [trackId] at the end of a [switchTo].
     * Unlike [emitTrackChangeIfNeeded] this never suppresses the emit — the director uses it to
     * refresh the now-playing metadata, so a coincidental match with the previous [playingTrackId]
     * must still be announced. Resets the gain ramp so the new track fades in from unity.
     */
    private suspend fun announceTrackChange(trackId: Long) {
        volumeProcessor.resetGain()
        hatchet.i("Announcing switch TrackChange: $playingTrackId -> $trackId.")
        val trackChangeEvent = SpeakerEvent.TrackChange(trackId)
        updateDebug { it.copy(lastEvent = trackChangeEvent, playingTrackId = trackId) }
        eventSink.emit(trackChangeEvent)
        playingTrackId = trackId
    }
}
