package net.sigmabeta.chipbox.player.buffer.real

import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.BufferDebugInfo
import net.sigmabeta.chipbox.player.buffer.BufferDebugSource
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
@OptIn(ExperimentalAtomicApi::class)
class RealBufferManager(
    private val hatchet: Hatchet,
) : ProducerBufferManager,
    ConsumerBufferManager,
    BufferDebugSource {
    /** The empty-array pool and the full-buffer queue, held together so they're swapped as one
     *  unit on a sample-rate change. A reader can therefore never observe a half-swapped
     *  (old-full, new-empty) pair — the race that let [drain] pour old-rate buffers into a fresh
     *  full pool and leak live-pool arrays to GC during a seek that coincided with a rate change.
     *
     *  @Volatile so a consumer that wakes from a ClosedReceiveChannelException after the swap sees
     *  the post-swap pool when it retries. */
    @Volatile
    private var pool: Pool? = null

    private class Pool(
        val empty: Channel<ShortArray>,
        val full: Channel<AudioBuffer>,
    )

    private var currentSampleRate: Int? = null

    private val fullCount = AtomicInt(0)

    private val emptyCount = AtomicInt(0)

    private val capacity = AtomicInt(0)

    private val drainCount = AtomicInt(0)

    private val debugInfoMutable = MutableStateFlow(BufferDebugInfo())

    override fun debugInfo(): StateFlow<BufferDebugInfo> = debugInfoMutable.asStateFlow()

    private fun publishDebug() {
        debugInfoMutable.value = BufferDebugInfo(
            sampleRate = currentSampleRate,
            capacity = capacity.load(),
            fullBuffersQueued = fullCount.load(),
            emptyArraysAvailable = emptyCount.load(),
            drainCount = drainCount.load(),
        )
    }

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

        val old = pool

        // Publish both channels in one write so no reader can catch a mismatched pair.
        pool = Pool(empty = arrays, full = buffers)

        // Close the old channels AFTER publishing the new ones — closing wakes any
        // consumer suspended in receive() with ClosedReceiveChannelException, and the
        // retry loops below re-read `pool`, which by then points at the new channels.
        // Otherwise the consumer stays parked on the orphaned old channel forever.
        old?.empty?.close()
        old?.full?.close()

        capacity.store(bufferCount)
        emptyCount.store(bufferCount)
        fullCount.store(0)
        publishDebug()

        hatchet.i(
            "setSampleRate: $previousRate Hz -> $sampleRate Hz " +
                "(bufferCount=$bufferCount, oldChannelsClosed=${old != null})."
        )
    }

    override suspend fun reset() {
        // Drop the pool and forget the rate so the next setSampleRate rebuilds a full empty pool
        // unconditionally. Closing the old channels wakes any consumer parked in receive() (none at
        // the cold-start call site) with ClosedReceiveChannelException; with pool now null there's
        // nothing for it to half-read. See the doc on ProducerBufferManager.reset for why a
        // surviving-process pool goes stale.
        val old = pool
        pool = null
        old?.empty?.close()
        old?.full?.close()
        currentSampleRate = null
        capacity.store(0)
        emptyCount.store(0)
        fullCount.store(0)
        publishDebug()
        hatchet.d("reset(): pool discarded; next setSampleRate rebuilds.")
    }

    override suspend fun sendAudioBuffer(audioBuffer: AudioBuffer) {
        pool?.full?.send(audioBuffer)
        fullCount.fetchAndAdd(1)
        publishDebug()
    }

    override fun checkForNextAudioBuffer(): AudioBuffer? {
        val buffer = pool?.full?.tryReceive()?.getOrNull() ?: return null
        fullCount.fetchAndAdd(-1)
        publishDebug()
        return buffer
    }

    override suspend fun waitForNextAudioBuffer(): AudioBuffer {
        while (true) {
            val channel = pool?.full ?: throw IllegalStateException("Set up buffers first!")
            try {
                val buffer = channel.receive()
                fullCount.fetchAndAdd(-1)
                publishDebug()
                return buffer
            } catch (_: ClosedReceiveChannelException) {
                // Channels were swapped for a sample rate change; loop to pick up the new one.
                hatchet.w("waitForNextAudioBuffer: old fullBuffers closed; retrying on new channel.")
            }
        }
    }

    override suspend fun recycleShortArray(data: ShortArray) {
        data.clear()
        try {
            pool?.empty?.send(data)
            emptyCount.fetchAndAdd(1)
            publishDebug()
        } catch (_: ClosedSendChannelException) {
            // Channels were swapped for a sample rate change; the new pool has its own
            // pre-allocated arrays, so let this orphaned array fall to GC.
        }
    }

    override suspend fun drain() {
        // drain() exists only to discard queued audio so a seek/skip starts clean; it must
        // never suspend. Snapshot the pool once at entry — setSampleRate runs concurrently
        // (the generator's loadNextTrack races the director's drain), and the single read
        // guarantees `full` and `empty` belong to the SAME generation rather than a
        // half-swapped (old-full, new-empty) pair. Recycling old-pool arrays is a pure
        // optimization, so use the non-blocking trySend: if the empty channel is at capacity
        // (nobody receives from it once setSampleRate swaps in a new pool) or already closed,
        // let the array fall to GC. A suspending send here would park forever and deadlock
        // speaker.seek so it never reaches flushSink/startPlayback and the consume loop
        // never restarts.
        val snapshot = pool ?: return
        val full = snapshot.full
        val empty = snapshot.empty
        hatchet.d("drain: entering.")
        var drained = 0
        while (true) {
            val result = full.tryReceive()
            val buffer = result.getOrNull() ?: break
            drained++
            fullCount.fetchAndAdd(-1)
            buffer.data.clear()
            val sendResult = empty.trySend(buffer.data)
            when {
                sendResult.isSuccess -> emptyCount.fetchAndAdd(1)

                // Closed == the expected case: setSampleRate swapped in a fresh pool and
                // closed this (snapshotted) old channel, so the orphan rightfully falls to GC.
                sendResult.isClosed -> Unit

                // Failure on a still-open channel should now be impossible: `full` and `empty`
                // are snapshotted from the same pool generation, so their array count is
                // conserved and the empty channel can't be over capacity. If this ever fires,
                // an unforeseen path is permanently shrinking the live buffer pool.
                else -> hatchet.w(
                    "drain: trySend failed on an open empty channel; " +
                        "live-pool array dropped to GC ($sendResult)."
                )
            }
            if (drained % DRAIN_LOG_INTERVAL == 0) {
                hatchet.d("drain: $drained buffer(s) so far.")
            }
        }
        drainCount.fetchAndAdd(1)
        publishDebug()
        hatchet.d("drain: returned $drained buffer(s) to the old empty pool.")
    }

    override suspend fun getNextEmptyBuffer(): ShortArray {
        val array = pool?.empty?.receive() ?: throw IllegalStateException("Set up buffers first!")
        emptyCount.fetchAndAdd(-1)
        publishDebug()
        return array
    }

    companion object {
        const val BUFFER_SIZE_BYTES_DEFAULT = 8192

        private const val BUFFER_LENGTH_MILLIS = 500.0

        private const val DRAIN_LOG_INTERVAL = 4
    }
}
