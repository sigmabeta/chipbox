package net.sigmabeta.chipbox.player.buffer.real

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
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
import net.sigmabeta.sage.logging.Hatchet

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
class RealBufferManager(
    private val hatchet: Hatchet,
) : ProducerBufferManager,
    ConsumerBufferManager {
    // @Volatile so a consumer that wakes from a ClosedReceiveChannelException after
    // setSampleRate swaps channels sees the post-swap value when it retries.
    @Volatile
    private var emptyArrays: Channel<ShortArray>? = null

    @Volatile
    private var fullBuffers: Channel<AudioBuffer>? = null

    private var currentSampleRate: Int? = null

    // TODO Inject a scope and do this setup in init() with a static sample rate,
    //      then make buffers nonnull.
    override suspend fun setSampleRate(sampleRate: Int) {
        val previousRate = currentSampleRate
        if (sampleRate == previousRate) {
            hatchet.d("setSampleRate($sampleRate Hz) — unchanged, no swap.")
            return
        }
        currentSampleRate = sampleRate

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

        val oldArrays = emptyArrays
        val oldBuffers = fullBuffers

        emptyArrays = arrays
        fullBuffers = buffers

        // Close the old channels AFTER publishing the new ones — closing wakes any
        // consumer suspended in receive() with ClosedReceiveChannelException, and the
        // retry loops below re-read the field, which by then points at the new channel.
        // Otherwise the consumer stays parked on the orphaned old channel forever.
        oldArrays?.close()
        oldBuffers?.close()

        hatchet.i(
            "setSampleRate: $previousRate Hz -> $sampleRate Hz " +
                "(bufferCount=$bufferCount, oldChannelsClosed=${oldArrays != null})."
        )
    }

    override suspend fun sendAudioBuffer(audioBuffer: AudioBuffer) {
        fullBuffers?.send(audioBuffer)
    }

    override fun checkForNextAudioBuffer(): AudioBuffer? = fullBuffers?.tryReceive()?.getOrNull()

    override suspend fun waitForNextAudioBuffer(): AudioBuffer {
        while (true) {
            val channel = fullBuffers ?: throw IllegalStateException("Set up buffers first!")
            try {
                return channel.receive()
            } catch (_: ClosedReceiveChannelException) {
                // Channels were swapped for a sample rate change; loop to pick up the new one.
                hatchet.w("waitForNextAudioBuffer: old fullBuffers closed; retrying on new channel.")
            }
        }
    }

    override suspend fun recycleShortArray(data: ShortArray) {
        data.clear()
        try {
            emptyArrays?.send(data)
        } catch (_: ClosedSendChannelException) {
            // Channels were swapped for a sample rate change; the new pool has its own
            // pre-allocated arrays, so let this orphaned array fall to GC.
        }
    }

    override suspend fun drain() {
        // Capture both channel references at entry. If setSampleRate runs concurrently
        // (it does — the generator's loadNextTrack races with the director's drain), we
        // must keep recycling these old-pool buffers back into the OLD emptyArrays. If
        // we re-read the field on every send we'd race into the freshly-allocated NEW
        // emptyArrays, which is initialized full-to-capacity, and our send would suspend
        // forever — the suspension actually deadlocks speaker.seek so it never reaches
        // flushSink/startPlayback and the consume loop never restarts.
        val full = fullBuffers ?: return
        val empty = emptyArrays
        hatchet.d("drain: entering.")
        var drained = 0
        while (true) {
            val result = full.tryReceive()
            val buffer = result.getOrNull() ?: break
            drained++
            buffer.data.clear()
            try {
                empty?.send(buffer.data)
            } catch (_: ClosedSendChannelException) {
                // OLD emptyArrays was closed by a concurrent setSampleRate before we
                // could deposit this buffer. The new pool has its own allocations, so
                // the orphan can just fall to GC.
                hatchet.w("drain: emptyArrays send hit ClosedSendChannelException.")
            }
            if (drained % DRAIN_LOG_INTERVAL == 0) {
                hatchet.d("drain: $drained buffer(s) so far.")
            }
        }
        hatchet.d("drain: returned $drained buffer(s) to the old empty pool.")
    }

    override suspend fun getNextEmptyBuffer(): ShortArray = emptyArrays?.receive() ?: throw IllegalStateException("Set up buffers first!")

    companion object {
        const val BUFFER_SIZE_BYTES_DEFAULT = 8192

        private const val BUFFER_LENGTH_MILLIS = 500.0

        private const val DRAIN_LOG_INTERVAL = 4
    }
}
