package net.sigmabeta.chipbox.player.buffer.real

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals

class RealBufferManagerTest {
    private fun manager() = RealBufferManager(BluntHatchet())

    private fun audioBuffer(trackId: Long, sampleRate: Int) = AudioBuffer(
        trackId = trackId,
        sampleRate = sampleRate,
        frameIndex = 0L,
        data = ShortArray(RealBufferManager.BUFFER_SIZE_BYTES_DEFAULT / 2),
        fadeStartMs = 0L,
        fadeLengthMs = 0L,
    )

    /**
     * Regression for the "Set up buffers first!" crash: a consumer parked in
     * [RealBufferManager.waitForNextAudioBuffer] when [RealBufferManager.reset] nulls the pool
     * (a cold start in a surviving process) must ride out the transiently-null pool and resume
     * once the generator's [RealBufferManager.setSampleRate] rebuilds it — not throw.
     */
    @Test
    fun waitForNextAudioBuffer_awaitsRebuiltPool_afterResetWhileParked() = runTest {
        val manager = manager()
        manager.setSampleRate(SAMPLE_RATE)

        // Park a consumer with no buffer queued: it falls through to waitForNextAudioBuffer.
        val received = async { manager.waitForNextAudioBuffer() }
        yield()

        // Cold-start reset closes the channel the consumer is parked on and nulls the pool.
        manager.reset()
        yield()

        // The generator rebuilds the pool and emits; the parked consumer must pick this up.
        manager.setSampleRate(SAMPLE_RATE)
        manager.sendAudioBuffer(audioBuffer(trackId = 7L, sampleRate = SAMPLE_RATE))

        assertEquals(7L, received.await().trackId)
    }

    /**
     * A consumer that awaits before any pool exists at all must suspend, then deliver the first
     * buffer once [RealBufferManager.setSampleRate] sets the pool up.
     */
    @Test
    fun waitForNextAudioBuffer_awaitsInitialPoolSetup() = runTest {
        val manager = manager()

        val received = async { manager.waitForNextAudioBuffer() }
        yield()

        manager.setSampleRate(SAMPLE_RATE)
        manager.sendAudioBuffer(audioBuffer(trackId = 3L, sampleRate = SAMPLE_RATE))

        assertEquals(3L, received.await().trackId)
    }

    /** A plain rate swap still wakes a parked consumer onto the new channel. */
    @Test
    fun waitForNextAudioBuffer_followsRateSwap() = runTest {
        val manager = manager()
        manager.setSampleRate(SAMPLE_RATE)

        val received = async { manager.waitForNextAudioBuffer() }
        yield()

        manager.setSampleRate(OTHER_SAMPLE_RATE)
        manager.sendAudioBuffer(audioBuffer(trackId = 11L, sampleRate = OTHER_SAMPLE_RATE))

        assertEquals(11L, received.await().trackId)
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val OTHER_SAMPLE_RATE = 32_000
    }
}
