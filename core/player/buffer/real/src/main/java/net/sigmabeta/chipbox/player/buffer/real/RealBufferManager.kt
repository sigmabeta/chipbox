package net.sigmabeta.chipbox.player.buffer.real

import kotlinx.coroutines.channels.Channel
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.buffer.ProducerBufferManager
import net.sigmabeta.chipbox.player.buffer.real.RealBufferManager.Companion.BUFFER_LENGTH_MILLIS
import net.sigmabeta.chipbox.player.buffer.real.RealBufferManager.Companion.BUFFER_SIZE_BYTES_DEFAULT
import net.sigmabeta.chipbox.player.common.bytesToSamples
import net.sigmabeta.chipbox.player.common.clear
import net.sigmabeta.chipbox.player.common.framesToSamples
import net.sigmabeta.chipbox.player.common.millisToFrames
import net.sigmabeta.chipbox.player.common.samplesToBytes

/**
 * Production buffer manager. Backs the queue with two bounded coroutine [Channel]s — one of
 * empty arrays for the producer to fill, one of full [AudioBuffer]s for the consumer to drain.
 *
 * Pool size is chosen so the queue holds [BUFFER_LENGTH_MILLIS] worth of audio at the active
 * sample rate, divided into [BUFFER_SIZE_BYTES_DEFAULT]-byte chunks. That gives the consumer
 * roughly half a second of headroom against producer hiccups.
 *
 * The single object implements both [ProducerBufferManager] and [ConsumerBufferManager]; each
 * side is injected with the narrower interface so neither can call operations meant for the
 * other.
 */
class RealBufferManager: ProducerBufferManager, ConsumerBufferManager {
    private var emptyArrays: Channel<ShortArray>? = null

    private var fullBuffers: Channel<AudioBuffer>? = null

    // TODO Inject a scope and do this setup in init() with a static sample rate,
    //      then make buffers nonnull.
    override suspend fun setSampleRate(sampleRate: Int) {
        val bufferSizeShorts = BUFFER_SIZE_BYTES_DEFAULT.bytesToSamples()
        val bufferCount = BUFFER_LENGTH_MILLIS
                .millisToFrames(sampleRate)
                .framesToSamples()
                .samplesToBytes()
                .div(BUFFER_SIZE_BYTES_DEFAULT)

        val arrays = Channel<ShortArray>(bufferCount)
        val buffers = Channel<AudioBuffer>(bufferCount)

        repeat(bufferCount) {
            arrays.send(ShortArray(bufferSizeShorts))
        }

        emptyArrays = arrays
        fullBuffers = buffers
    }

    override suspend fun sendAudioBuffer(audioBuffer: AudioBuffer) {
        fullBuffers?.send(audioBuffer)
    }

    override fun checkForNextAudioBuffer(): AudioBuffer? {
        return fullBuffers?.tryReceive()?.getOrNull()
    }

    override suspend fun waitForNextAudioBuffer(): AudioBuffer {
        return fullBuffers?.receive() ?: throw  IllegalStateException("Set up buffers first!")
    }

    override suspend fun recycleShortArray(data: ShortArray) {
        data.clear()
        emptyArrays?.send(data)
    }

    override suspend fun getNextEmptyBuffer(): ShortArray {
        return emptyArrays?.receive() ?: throw  IllegalStateException("Set up buffers first!")
    }

    companion object {
        const val BUFFER_SIZE_BYTES_DEFAULT = 8192

        private const val BUFFER_LENGTH_MILLIS = 500.0
    }
}