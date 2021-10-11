package net.sigmabeta.chipbox.player.generator

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ProducerBufferManager
import net.sigmabeta.chipbox.player.common.framesToMillis
import net.sigmabeta.chipbox.repository.Repository

abstract class Generator(
        private val repository: Repository,
        private val bufferManager: ProducerBufferManager,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val generatorScope = CoroutineScope(dispatcher)

    private var ongoingGenerationJob: Job? = null

    private var framesPlayed = 0

    private var nextTrackIdChannel = Channel<Long>(1)

    private var currentTrack: Track? = null

    private var sampleRate: Int? = null

    private val eventSink = MutableSharedFlow<GeneratorEvent>(
        replay = 0,
        onBufferOverflow = BufferOverflow.SUSPEND,
        extraBufferCapacity = 10
    )

    protected abstract fun loadTrack(loadedTrack: Track)

    protected abstract fun generateAudio(buffer: ShortArray): Int

    protected abstract fun teardown()

    protected abstract fun isTrackOver(): Boolean

    protected abstract fun getEmulatorSampleRate(): Int

    abstract fun getLastError(): String?

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
            println("Already looping.")
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

            if (framesGenerated == 0) {
                error = "Failed to generate any audio."
                break
            }

            framesPlayed += framesGenerated

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
        currentTrack = newTrack

        println("Loading track ${newTrack.title} into emulator.")
        loadTrack(newTrack)

        sampleRate = getEmulatorSampleRate()
        bufferManager.setSampleRate(sampleRate!!)

        return getLastError()
    }

    private fun teardownHelper() {
        println("Tearing down track ${currentTrack?.title}...")
        teardown()
        currentTrack = null
        ongoingGenerationJob = null
        framesPlayed = 0
    }

    private fun ShortArray.clear() {
        forEachIndexed { index, _ -> set(index, 0) }
    }

    companion object {
        private const val LENGTH_FADE_MILLIS = 6_000.0
    }
}