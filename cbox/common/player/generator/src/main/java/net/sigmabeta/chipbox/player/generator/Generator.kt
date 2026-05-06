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
import net.sigmabeta.chipbox.player.common.framesToMillis
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.sage.logging.Hatchet

/**
 * Producer side of the playback pipeline. Resolves a track id to bytes via the [Repository] +
 * [ContentSourceRegistry], hands those bytes to a subclass-supplied emulator, and pushes the
 * decoded PCM into the [bufferManager] for a downstream Speaker to consume.
 *
 * Subclasses pick the emulator. [net.sigmabeta.chipbox.player.generator.real.RealGenerator]
 * dispatches based on file extension across the available native emulators (GME, GBA, PSF, etc);
 * [net.sigmabeta.chipbox.player.generator.fake.FakeGenerator] always uses the in-process
 * sine/square synthesizer.
 *
 * ### Threading
 * The generation loop runs as a single coroutine on [dispatcher] (default [Dispatchers.IO]).
 * [play], [pause], and [stop] manipulate that job; calls from any thread are safe but
 * non-atomic with respect to each other. Subclass abstract methods are only ever invoked from
 * inside the loop.
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

    private var framesPlayed = 0

    private var nextTrackIdChannel = Channel<Long>(1)

    private var currentTrack: Track? = null

    private var sampleRate: Int? = null

    private var lastSilenceState: Boolean? = null

    private var lastSilenceTrackId: Long? = null

    private val eventSink = MutableSharedFlow<GeneratorEvent>(
        replay = 0,
        onBufferOverflow = BufferOverflow.SUSPEND,
        extraBufferCapacity = 10
    )

    /** Hand the loaded track + its raw file bytes to the underlying emulator so subsequent
     *  [generateAudio] calls produce its samples. Called once per track from the loop. */
    protected abstract suspend fun loadTrack(loadedTrack: Track, bytes: ByteArray)

    /** Fill [buffer] with up to its capacity worth of stereo 16-bit PCM samples. Returns the
     *  number of frames actually generated; 0 is treated as fatal by the loop. */
    protected abstract fun generateAudio(buffer: ShortArray): Int

    /** Release any per-track emulator resources. May be called multiple times. */
    protected abstract fun teardown()

    /** True once the emulator has reached its configured track length. */
    protected abstract fun isTrackOver(): Boolean

    /** The emulator's native output sample rate for the loaded track, in Hz. Used to size the
     *  buffer pool and drive the fade processor. */
    protected abstract fun getEmulatorSampleRate(): Int

    /** Most-recent error from the emulator, or null. Polled after every buffer; non-null
     *  terminates the loop. */
    abstract fun getLastError(): String?

    /** Non-fatal diagnostics from the emulator (IOP HLE warnings, etc.) accumulated during
     *  the most recent [generateAudio] call. Default null; subclasses opt in. Polled after
     *  every buffer for logging only — does not terminate the loop. */
    open fun getDiagnostics(): String? = null

    fun events() = eventSink.asSharedFlow()

    suspend fun startTrack(
            trackId: Long,
    ) {
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

    private suspend fun loop() {
        try {
            var error: String?
            var nextTrackId: Long? = nextTrackIdChannel.receive()

            while (true) {
                // When track is over, block waiting for the next one.
                if (nextTrackId == null && isTrackOver()) {
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

                if (currentTrack == null) {
                    error = "No track loaded."
                    break
                }

                if (sampleRate == null) {
                    error = "Invalid sample rate."
                    break
                }

                val bufferStartFrame = framesPlayed

                // Generate the next buffer of audio..
                val generatedAudio = bufferManager.getNextEmptyBuffer()
                val framesGenerated = generateAudio(generatedAudio)

                if (framesGenerated <= 0 && !isTrackOver()) {
                    error = "Emulator returned $framesGenerated frames."
                    break
                }

                framesPlayed += framesGenerated

                getDiagnostics()?.let { hatchet.w("Emulator diagnostics: $it") }

                logSilenceTransition(generatedAudio, framesGenerated)

                error = getLastError()

                if (error != null) {
                    break
                }

                FadeProcessor.fadeIfNecessary(
                    generatedAudio,
                    sampleRate!!,
                    bufferStartFrame.framesToMillis(sampleRate!!),
                    currentTrack!!.trackLengthMs - LENGTH_FADE_MILLIS,
                    LENGTH_FADE_MILLIS
                )

                bufferManager.sendAudioBuffer(
                    AudioBuffer(
                        currentTrack!!.id,
                        sampleRate!!,
                        generatedAudio
                    )
                )

                // Emit this buffer.
                eventSink.emit(GeneratorEvent.Emitting)

                // Check if this coroutine has been cancelled.
                yield()
            }

            // Report error, if it happened.
            if (error != null) {
                eventSink.emit(
                    GeneratorEvent.Error(error)
                )
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

        if (currentTrack != null) {
            teardown()
            framesPlayed = 0
        }

        val newTrack = repository.getTrack(trackId) ?: return "Failed to load track."
        val source = contentSourceRegistry.get(newTrack.source)
            ?: return "No content source registered for '${newTrack.source}'."
        val bytes = source.openBytes(newTrack.path)
            ?: return "Failed to read bytes for ${newTrack.title}."

        currentTrack = newTrack
        loadTrack(newTrack, bytes)

        sampleRate = getEmulatorSampleRate()
        bufferManager.setSampleRate(sampleRate!!)

        return getLastError()
    }

    private fun teardownHelper() {
        hatchet.d("Tearing down track ${currentTrack?.title}...")
        teardown()
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

    private fun ShortArray.clear() {
        forEachIndexed { index, _ -> set(index, 0) }
    }

    companion object {
        private const val LENGTH_FADE_MILLIS = 6_000.0
    }
}