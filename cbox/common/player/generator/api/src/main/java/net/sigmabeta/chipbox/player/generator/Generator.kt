package net.sigmabeta.chipbox.player.generator

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ProducerBufferManager
import net.sigmabeta.chipbox.player.cache.PcmTrackSource
import net.sigmabeta.chipbox.player.common.framesToMillis
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.sage.logging.Hatchet

/**
 * Producer side of the playback pipeline. Resolves a track id to bytes via the [Repository] +
 * [ContentSourceRegistry], hands those bytes to a subclass-supplied [PcmTrackSource.Factory],
 * and pushes the decoded PCM into the [bufferManager] for a downstream Speaker to consume.
 *
 * Subclasses pick the [PcmTrackSource.Factory].
 * [net.sigmabeta.chipbox.player.generator.real.RealGenerator] dispatches across the available
 * native emulators with a render-ahead cache;
 * [net.sigmabeta.chipbox.player.generator.fake.FakeGenerator] uses an in-process synth.
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
abstract class Generator(
    private val repository: Repository,
    protected val contentSourceRegistry: ContentSourceRegistry,
    private val bufferManager: ProducerBufferManager,
    protected val hatchet: Hatchet,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val generatorScope = CoroutineScope(dispatcher)

    private var ongoingGenerationJob: Job? = null

    private var framesPlayed: Int = 0

    private var nextTrackIdChannel = Channel<Long>(1)

    private var currentTrack: Track? = null

    private var currentSource: PcmTrackSource? = null

    private var sampleRate: Int? = null

    private var lastSilenceState: Boolean? = null

    private var lastSilenceTrackId: Long? = null

    private val eventSink = MutableSharedFlow<GeneratorEvent>(
        replay = 0,
        onBufferOverflow = BufferOverflow.SUSPEND,
        extraBufferCapacity = 10
    )

    /** Subclass-supplied factory that decides which [PcmTrackSource] backs each track. */
    protected abstract val pcmSourceFactory: PcmTrackSource.Factory

    fun events() = eventSink.asSharedFlow()

    suspend fun startTrack(trackId: Long) {
        nextTrackIdChannel.send(trackId)
        play()
    }

    fun play() {
        if (ongoingGenerationJob == null) {
            ongoingGenerationJob = generatorScope.launch {
                loop()
            }
        } else {
            hatchet.d("Already looping.")
        }
    }

    fun pause() {
        ongoingGenerationJob?.cancel()
        ongoingGenerationJob = null
    }

    suspend fun stop() {
        ongoingGenerationJob?.cancelAndJoin()
        ongoingGenerationJob = null

        teardownHelper()
    }

    /**
     * Reposition playback within the current track. Effective at the next buffer boundary.
     * The audio currently queued in the buffer manager is not flushed by this call — the
     * caller (typically [net.sigmabeta.chipbox.player.director.Director]) is responsible for
     * draining the queue and flushing the speaker so the seek isn't preceded by stale frames.
     */
    suspend fun seek(positionMs: Long) {
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

                val bufferStartFrame = framesPlayed

                val generatedAudio = bufferManager.getNextEmptyBuffer()
                val framesGenerated = source.readFrames(generatedAudio)

                if (framesGenerated <= 0 && !source.isOver) {
                    error = source.getLastError()
                        ?: "Source returned $framesGenerated frames."
                    break
                }

                framesPlayed += framesGenerated

                source.getDiagnostics()?.let { hatchet.w("Source diagnostics: $it") }

                logSilenceTransition(generatedAudio, framesGenerated)

                error = source.getLastError()
                if (error != null) {
                    break
                }

                FadeProcessor.fadeIfNecessary(
                    generatedAudio,
                    rate,
                    bufferStartFrame.framesToMillis(rate),
                    currentTrack!!.trackLengthMs - LENGTH_FADE_MILLIS,
                    LENGTH_FADE_MILLIS
                )

                bufferManager.sendAudioBuffer(
                    AudioBuffer(
                        currentTrack!!.id,
                        rate,
                        generatedAudio
                    )
                )

                eventSink.emit(GeneratorEvent.Emitting)

                yield()
            }

            if (error != null) {
                eventSink.emit(GeneratorEvent.Error(error))
            }

            teardownHelper()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            eventSink.emit(GeneratorEvent.Error(e.message ?: "Unknown error"))
            teardownHelper()
        }
    }

    private suspend fun loadNextTrack(trackId: Long?): String? {
        if (trackId == null) {
            return null
        }

        eventSink.emit(GeneratorEvent.Loading(trackId))

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
        val pcmSource = pcmSourceFactory.open(newTrack, bytes)
        currentSource = pcmSource

        sampleRate = pcmSource.sampleRate
        bufferManager.setSampleRate(pcmSource.sampleRate)

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
    }

    private fun logSilenceTransition(buffer: ShortArray, framesGenerated: Int) {
        if (framesGenerated <= 0) return
        val track = currentTrack ?: return

        val silent = buffer.all { it == 0.toShort() }
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

    companion object {
        private const val LENGTH_FADE_MILLIS = 6_000.0
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
