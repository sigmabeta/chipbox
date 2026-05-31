package net.sigmabeta.chipbox.player.generator

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.concurrent.Volatile
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ProducerBufferManager
import net.sigmabeta.chipbox.player.cache.PcmTrackSource
import net.sigmabeta.chipbox.player.common.SHORTS_PER_FRAME
import net.sigmabeta.chipbox.player.common.STALL_TIMEOUT_MS
import net.sigmabeta.chipbox.player.common.firstAudibleFrame
import net.sigmabeta.chipbox.player.common.framesToMillis
import net.sigmabeta.chipbox.player.common.isBufferSilent
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.utils.ioDispatcher
import net.sigmabeta.sage.logging.Hatchet

/**
 * Production base class for [Generator]. Owns the generation loop, channel, and buffer-manager
 * plumbing; subclasses only have to supply a [PcmTrackSource.Factory] that decides how each
 * track is decoded ([net.sigmabeta.chipbox.player.generator.real.RealGenerator] dispatches
 * across native emulators with a render-ahead cache;
 * [net.sigmabeta.chipbox.player.generator.fake.SynthGenerator] uses an in-process synth).
 *
 * The interface itself is in [Generator] — tests can implement that directly to avoid dragging
 * a [Repository] / [ContentSourceRegistry] / [ProducerBufferManager] into their fixtures.
 *
 * ### Threading
 * The generation loop runs as a single coroutine on [dispatcher] (default [Dispatchers.IO]).
 * [play], [pause], [stop], and [seek] manipulate that job; calls from any thread are safe but
 * non-atomic with respect to each other.
 *
 * ### Track transitions
 * [startTrack] queues the next track id on a 1-slot channel and starts the loop if it isn't
 * running. The loop drains the channel between buffers, so a queued track takes effect at the
 * next buffer boundary rather than mid-buffer. When a track ends naturally, the loop emits
 * [GeneratorEvent.TrackChange] and blocks on the channel until the director sends the next id.
 */
abstract class BaseGenerator(
    private val repository: Repository,
    protected val contentSourceRegistry: ContentSourceRegistry,
    private val bufferManager: ProducerBufferManager,
    protected val hatchet: Hatchet,
    dispatcher: CoroutineDispatcher = ioDispatcher
) : Generator {
    private val generatorScope = CoroutineScope(SupervisorJob() + dispatcher)

    override fun release() {
        generatorScope.cancel()
    }

    // @Volatile: written by transport calls (play/pause/stop, on the director's thread) and read
    // back by a concurrent loop self-teardown, so the null-on-teardown must be visible across
    // threads. Atomicity of the check-then-launch in play() is provided by the director
    // serializing its generator calls plus always stop()-ing before relaunching in recovery.
    @Volatile
    private var ongoingGenerationJob: Job? = null

    private var framesPlayed: Int = 0

    private var nextTrackIdChannel = Channel<Long>(1)

    private var currentTrack: Track? = null

    private var currentSource: PcmTrackSource? = null

    private var sampleRate: Int? = null

    private var lastSilenceState: Boolean? = null

    private var lastSilenceTrackId: Long? = null

    /** False until the current track has produced its first non-silent frame. While false the
     *  leading silence is trimmed; once true the rest of the track passes through untouched. */
    private var audibleStarted: Boolean = false

    /** Frames of leading silence dropped so far for the current track. Drives the
     *  "no audio within the first N seconds" abort. */
    private var silentLeadFrames: Int = 0

    private val eventSink = MutableSharedFlow<GeneratorEvent>(
        replay = 0,
        onBufferOverflow = BufferOverflow.SUSPEND,
        extraBufferCapacity = 10
    )

    /** Subclass-supplied factory that decides which [PcmTrackSource] backs each track. */
    protected abstract val pcmSourceFactory: PcmTrackSource.Factory

    override fun events(): SharedFlow<GeneratorEvent> = eventSink.asSharedFlow()

    private val debugInfoMutable = MutableStateFlow(GeneratorDebugInfo())

    /** Observational diagnostics for the debug PlaybackStatus screen. */
    override fun debugInfo(): StateFlow<GeneratorDebugInfo> = debugInfoMutable.asStateFlow()

    private fun updateDebug(block: (GeneratorDebugInfo) -> GeneratorDebugInfo) {
        debugInfoMutable.value = block(debugInfoMutable.value)
    }

    override suspend fun startTrack(trackId: Long) {
        hatchet.i("startTrack($trackId): queueing on nextTrackIdChannel.")
        nextTrackIdChannel.send(trackId)
        hatchet.d("startTrack($trackId): queued; calling play().")
        play()
    }

    override fun play() {
        if (ongoingGenerationJob == null) {
            updateDebug { it.copy(looping = true) }
            ongoingGenerationJob = generatorScope.launch {
                loop()
            }
        } else {
            hatchet.d("Already looping.")
        }
    }

    override fun pause() {
        ongoingGenerationJob?.cancel()
        ongoingGenerationJob = null
    }

    override suspend fun stop() {
        ongoingGenerationJob?.cancelAndJoin()
        ongoingGenerationJob = null

        // Drop any track id queued but not yet consumed — e.g. a track requested while the loop was
        // stuck rendering the previous one. Otherwise the stale id wedges the 1-slot channel (the
        // next startTrack's send would block behind it) or gets picked up ahead of the real next
        // track when the loop restarts.
        while (nextTrackIdChannel.tryReceive().isSuccess) {
            // draining
        }

        teardownHelper()
    }

    /**
     * Reposition playback within the current track. Effective at the next buffer boundary.
     * The audio currently queued in the buffer manager is not flushed by this call — the
     * caller (typically [net.sigmabeta.chipbox.player.director.Director]) is responsible for
     * draining the queue and flushing the speaker so the seek isn't preceded by stale frames.
     */
    override suspend fun seek(positionMs: Long) {
        val source = currentSource ?: return
        val rate = sampleRate ?: return
        val targetFrame = (positionMs * rate / MILLIS_PER_SECOND).coerceAtLeast(0L)
        source.seek(targetFrame)
        framesPlayed = targetFrame.toInt()
        hatchet.d("Seek to ${positionMs}ms (frame $targetFrame).")
    }

    private suspend fun loop() {
        try {
            var error: String?
            var nextTrackId: Long? = nextTrackIdChannel.receive()

            while (true) {
                // When track is over, block waiting for the next one.
                if (nextTrackId == null && currentSource?.isOver == true) {
                    hatchet.d("Track ${currentTrack?.title} reached natural end.")
                    updateDebug { it.copy(lastEvent = GeneratorEvent.TrackChange) }
                    eventSink.emit(GeneratorEvent.TrackChange)
                    nextTrackId = nextTrackIdChannel.receive()
                } else {
                    // See if we have another one queued up, but don't block.
                    val result = nextTrackIdChannel.tryReceive()
                    if (result.isSuccess) {
                        nextTrackId = result.getOrThrow()
                    }
                }

                error = loadNextTrack(nextTrackId)
                nextTrackId = null

                if (error != null) {
                    break
                }

                val source = currentSource
                if (source == null || currentTrack == null) {
                    error = "No track loaded."
                    break
                }

                val rate = sampleRate
                if (rate == null) {
                    error = "Invalid sample rate."
                    break
                }

                val generatedAudio = bufferManager.getNextEmptyBuffer()
                var framesGenerated = fillBuffer(source, generatedAudio)

                // Trim the run of silence many tracks open with so playback (and the fade
                // timeline) begins at the music. Reuses `generatedAudio` rather than
                // re-borrowing, since the producer side has no way to return a buffer to
                // the pool.
                if (!audibleStarted) {
                    when (
                        val trim = trimLeadingSilence(source, generatedAudio, framesGenerated, rate)
                    ) {
                        is TrimResult.Aborted -> {
                            error = trim.error
                            break
                        }

                        is TrimResult.Audible -> {
                            framesGenerated = trim.frames
                            audibleStarted = true
                            if (trim.trimmedFrames > 0) {
                                hatchet.i(
                                    "Track ${currentTrack?.title}: trimmed " +
                                        "${trim.trimmedFrames} frame(s) of leading silence."
                                )
                            }
                        }

                        is TrimResult.PassThrough -> framesGenerated = trim.frames
                    }
                }

                if (framesGenerated <= 0 && !source.isOver) {
                    error = source.getLastError()
                        ?: "Source returned $framesGenerated frames."
                    break
                }

                val bufferStartFrame = framesPlayed
                framesPlayed += framesGenerated

                source.getDiagnostics()?.let {
                    hatchet.w("Source diagnostics: $it")
                    updateDebug { info -> info.copy(sourceDiagnostics = it) }
                }

                logSilenceTransition(generatedAudio, framesGenerated)

                error = source.getLastError()
                if (error != null) {
                    break
                }

                val track = currentTrack!!

                if (bufferStartFrame == 0) {
                    hatchet.d(
                        "Sending first buffer for track ${track.id} (${track.title}, " +
                            "frames=$framesGenerated, rate=$rate)."
                    )
                }

                bufferManager.sendAudioBuffer(
                    AudioBuffer(
                        trackId = track.id,
                        sampleRate = rate,
                        frameIndex = bufferStartFrame.toLong(),
                        data = generatedAudio,
                        fadeStartMs = track.trackLengthMs,
                        fadeLengthMs = track.fadeLengthMs,
                        // Live, not snapshotted: a render-ahead source's writer races well past
                        // the play head, so the BS.1770 figures are at/near final within the
                        // first 400 ms. The speaker re-derives gain whenever they change.
                        loudnessLufs = source.loudnessLufs,
                        truePeakDbtp = source.truePeakDbtp,
                    )
                )

                if (bufferStartFrame == 0) {
                    hatchet.d("First buffer for track ${track.id} delivered to buffer manager.")
                }

                val emittingEvent = GeneratorEvent.Emitting(
                    producedMs = framesPlayed.framesToMillis(rate).toLong(),
                    trackId = track.id,
                    // Snapshot the source's cache watermark each cycle so the now-playing screen
                    // can render a cached-portion overlay. Live caching sources publish how much
                    // is on disk; cached-file sources return totalFrames (already complete).
                    cachedMs = source.cachedFrames.framesToMillis(rate),
                )
                updateDebug {
                    it.copy(
                        producedMs = emittingEvent.producedMs,
                        framesPlayed = framesPlayed,
                        lastEvent = emittingEvent,
                    )
                }
                eventSink.emit(emittingEvent)

                yield()
            }

            if (error != null) {
                val errorEvent = GeneratorEvent.Error(error)
                updateDebug { it.copy(lastEvent = errorEvent, lastError = error) }
                eventSink.emit(errorEvent)
            }

            teardownHelper()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message ?: "Unknown error"
            val errorEvent = GeneratorEvent.Error(message)
            updateDebug { it.copy(lastEvent = errorEvent, lastError = message) }
            eventSink.emit(errorEvent)
            teardownHelper()
        }
    }

    private suspend fun loadNextTrack(trackId: Long?): String? {
        if (trackId == null) {
            return null
        }

        hatchet.i("loadNextTrack($trackId): emitting Loading.")
        val loadingEvent = GeneratorEvent.Loading(trackId)
        updateDebug { it.copy(lastEvent = loadingEvent, currentTrackId = trackId) }
        eventSink.emit(loadingEvent)

        if (currentSource != null) {
            try {
                currentSource?.close()
            } catch (t: Throwable) {
                hatchet.w("Error closing previous source: ${t.message}")
            }
            currentSource = null
            framesPlayed = 0
        }

        val newTrack = repository.getTrack(trackId) ?: return "Failed to load track."
        val source = contentSourceRegistry.get(newTrack.source)
            ?: return "No content source registered for '${newTrack.source}'."
        val bytes = source.openBytes(newTrack.path)
            ?: return "Failed to read bytes for ${newTrack.title}."

        currentTrack = newTrack
        audibleStarted = false
        silentLeadFrames = 0
        val pcmSource = pcmSourceFactory.open(newTrack, bytes)
        currentSource = pcmSource

        sampleRate = pcmSource.sampleRate
        updateDebug {
            it.copy(
                currentTrackId = newTrack.id,
                currentTrackTitle = newTrack.title,
                sampleRate = pcmSource.sampleRate,
                producedMs = 0L,
                framesPlayed = 0,
            )
        }
        hatchet.d("loadNextTrack($trackId): calling bufferManager.setSampleRate(${pcmSource.sampleRate}).")
        bufferManager.setSampleRate(pcmSource.sampleRate)
        hatchet.d("loadNextTrack($trackId): setSampleRate returned.")

        hatchet.d(
            "Track ${newTrack.title} fade plan: " +
                "trackLengthMs=${newTrack.trackLengthMs}, " +
                "fadeStartMs=${newTrack.trackLengthMs}, " +
                "fadeLengthMs=${newTrack.fadeLengthMs}."
        )

        return pcmSource.getLastError()
    }

    private fun teardownHelper() {
        hatchet.d("Tearing down track ${currentTrack?.title}...")
        val source = currentSource
        currentSource = null
        if (source != null) {
            generatorScope.launch {
                try {
                    source.close()
                } catch (t: Throwable) {
                    hatchet.w("Error closing PCM source: ${t.message}")
                }
            }
        }
        currentTrack = null
        ongoingGenerationJob = null
        framesPlayed = 0
        lastSilenceState = null
        lastSilenceTrackId = null
        audibleStarted = false
        silentLeadFrames = 0
        updateDebug {
            GeneratorDebugInfo(
                looping = false,
                lastEvent = it.lastEvent,
                lastError = it.lastError,
            )
        }
    }

    private fun logSilenceTransition(buffer: ShortArray, framesGenerated: Int) {
        if (framesGenerated <= 0) return
        val track = currentTrack ?: return

        val silent = isBufferSilent(buffer, framesGenerated)
        val previousState = lastSilenceState
        val previousTrackId = lastSilenceTrackId
        if (track.id != previousTrackId) {
            val state = if (silent) "silent" else "audible"
            hatchet.d("Track ${track.title}: first buffer is $state.")
        } else if (silent != previousState) {
            hatchet.w(if (silent) "Audio went silent." else "Audio is audible again.")
        }
        lastSilenceState = silent
        lastSilenceTrackId = track.id
    }

    /**
     * Outcome of [trimLeadingSilence].
     */
    private sealed interface TrimResult {
        /** Audio found. [frames] valid frames sit at the front of the buffer; [trimmedFrames]
         *  leading silent frames were discarded. */
        data class Audible(val frames: Int, val trimmedFrames: Int) : TrimResult

        /** The track stayed silent past the timeout — abort with [error]. */
        data class Aborted(val error: String) : TrimResult

        /** The source ended (or errored) before producing audio. Hand [frames] back so the
         *  caller's normal end/error handling runs. */
        data class PassThrough(val frames: Int) : TrimResult
    }

    /**
     * Drop the leading silence at the very start of a track. Called only until the first
     * audible frame is found ([audibleStarted]). Whole-silent buffers are read over in place —
     * the producer side can't return a buffer to the pool, so re-borrowing would shrink it.
     * Once any audible frame appears, the audible tail is shifted to the front of [buffer] and
     * the freed space refilled so the buffer stays a full, contiguous block (the pipeline
     * always plays the whole array).
     *
     * Aborts via [TrimResult.Aborted] if no audio appears within [STALL_TIMEOUT_MS].
     */
    private suspend fun trimLeadingSilence(
        source: PcmTrackSource,
        buffer: ShortArray,
        initialFrames: Int,
        sampleRate: Int,
    ): TrimResult {
        var frames = initialFrames
        var result: TrimResult? = null
        while (result == null) {
            val firstAudible = if (frames > 0) firstAudibleFrame(buffer, frames) else 0
            when {
                frames <= 0 -> result = TrimResult.PassThrough(frames)

                firstAudible == 0 -> result = TrimResult.Audible(frames, 0)

                firstAudible > 0 -> {
                    val keptShorts = (frames - firstAudible) * SHORTS_PER_FRAME
                    val audibleStart = firstAudible * SHORTS_PER_FRAME
                    buffer.copyInto(buffer, 0, audibleStart, audibleStart + keptShorts)
                    // Refill the freed tail *completely* so we neither drop music nor leave a
                    // gap of pool zeros / stale samples for the consumer to play. fillBuffer
                    // zero-fills its own tail if the source ends mid-refill.
                    val gap = ShortArray(buffer.size - keptShorts)
                    val refilled = fillBuffer(source, gap)
                    gap.copyInto(buffer, keptShorts)
                    result = TrimResult.Audible((frames - firstAudible) + refilled, firstAudible)
                }

                else -> {
                    // Whole buffer silent.
                    silentLeadFrames += frames
                    val silenceTimeoutFrames = sampleRate.toLong() * STALL_TIMEOUT_MS / MILLIS_PER_SECOND
                    when {
                        silentLeadFrames.toLong() >= silenceTimeoutFrames ->
                            result = TrimResult.Aborted(
                                "Track produced no audio within the first " +
                                    "${STALL_TIMEOUT_MS / MILLIS_PER_SECOND} seconds."
                            )

                        source.isOver -> result = TrimResult.PassThrough(frames)

                        else -> {
                            yield()
                            frames = source.readFrames(buffer)
                        }
                    }
                }
            }
        }
        return result
    }

    /**
     * Read at least one frame, transparently waiting out render-ahead starvation. A render-ahead
     * caching source returns 0 with [PcmTrackSource.awaitingRender] true while its writer hasn't
     * yet produced the frame under the cursor (e.g. right after a seek into an un-rendered
     * region). Rather than treat that as an error, emit a [GeneratorEvent.Rendering] heartbeat —
     * so the director can apply its own stall timeout against actual render progress — then poll
     * until frames arrive. Returns 0 only on a genuine end-of-track, a source error, or a
     * non-render 0 (from a source that never lags the cursor), which the caller surfaces as an
     * error. Cancellation while polling unwinds normally via [delay].
     */
    private suspend fun readWaitingForRender(source: PcmTrackSource, buffer: ShortArray): Int {
        while (true) {
            val frames = source.readFrames(buffer).coerceAtLeast(0)
            if (frames > 0) return frames
            if (source.isOver || source.getLastError() != null || !source.awaitingRender) {
                return 0
            }
            eventSink.emit(GeneratorEvent.Rendering(source.cachedFrames.framesToMillis(source.sampleRate)))
            delay(RENDER_POLL_MS)
        }
    }

    /**
     * Fill [buffer] to its full capacity, reading [source] repeatedly until it's full, the
     * track ends, or the source errors. Returns the number of frames actually produced.
     *
     * A single [PcmTrackSource.readFrames] is allowed to return a *short* count: a render-ahead
     * caching source hands back only what its writer has produced so far, which can be a
     * fraction of the buffer. The pipeline plays the whole fixed-size array (no per-buffer
     * frame count travels on [net.sigmabeta.chipbox.player.buffer.AudioBuffer]), so emitting a
     * partially-filled buffer plays its untouched tail — pool zeros or a recycled buffer's
     * stale audio — as a mid-stream gap/glitch. The first read goes through [readWaitingForRender]
     * so render-ahead starvation is waited out; subsequent reads top the buffer up until it's
     * whole, so every emitted buffer is one contiguous block. Cached-file sources already return
     * full reads, so for them the first read fills the buffer and the loop is a no-op.
     *
     * Only a genuine end-of-track (or error) ends the fill early; whatever tail is left unread
     * is zeroed so the final buffer can't replay stale samples.
     */
    private suspend fun fillBuffer(source: PcmTrackSource, buffer: ShortArray): Int {
        val capacityShorts = buffer.size
        var filledShorts = readWaitingForRender(source, buffer) * SHORTS_PER_FRAME
        while (filledShorts in 1 until capacityShorts) {
            val rest = ShortArray(capacityShorts - filledShorts)
            val read = source.readFrames(rest).coerceAtLeast(0)
            if (read == 0) break
            rest.copyInto(buffer, filledShorts, 0, read * SHORTS_PER_FRAME)
            filledShorts += read * SHORTS_PER_FRAME
        }
        if (filledShorts in 1 until capacityShorts) {
            buffer.fill(0.toShort(), filledShorts, capacityShorts)
        }
        return filledShorts / SHORTS_PER_FRAME
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1_000L

        /** How often to re-check a render-ahead source (and emit a [GeneratorEvent.Rendering]
         *  heartbeat) while it's still rendering toward the read cursor. Purely a poll cadence —
         *  the wake latency once frames are ready, well within the buffer manager's headroom — not
         *  a failure deadline; the director decides when a render wait has stalled. */
        private const val RENDER_POLL_MS = 100L
    }
}
