package net.sigmabeta.chipbox.player.speaker

import java.util.Collections
import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the speaker's position-read confinement: [BaseSpeaker.currentPositionMs] (which the
 * director calls from its own coroutine) must return a snapshot published by the consume loop and
 * must never reach into the sink off-thread. The live sink read ([BaseSpeaker.readSinkPositionMs])
 * runs only on the consume coroutine, which also owns sink open/teardown — so a director-thread
 * position read can't race a teardown.
 */
internal class BaseSpeakerPositionTest {

    @Test
    fun `currentPositionMs returns the snapshot the consume loop published, read only on its thread`() =
        runBlocking {
            val speakerThreadName = "test-speaker-thread"
            val executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, speakerThreadName)
            }
            val dispatcher = executor.asCoroutineDispatcher()
            try {
                val reachedSecondPull = CompletableDeferred<Unit>()
                val buffers = OneBufferThenAwait(reachedSecondPull)
                val speaker = PositionRecordingSpeaker(buffers, dispatcher)

                speaker.play()
                // The loop consumed the one buffer (refreshing position) and is now parked awaiting
                // the next — so the snapshot is published and stable.
                reachedSecondPull.await()

                assertEquals(
                    SINK_POSITION_MS,
                    speaker.currentPositionMs(),
                    "currentPositionMs must return the position the consume loop sampled",
                )
                // readSinkPositionMs must have run, and only on the speaker thread.
                assertTrue(speaker.readThreads.isNotEmpty(), "the loop should have read the sink position")
                assertEquals(
                    setOf(speakerThreadName),
                    speaker.readThreads.map { it.substringBefore(" @") }.toSet(),
                    "the live sink read must stay on the consume thread; saw ${speaker.readThreads}",
                )
                speaker.stop()
            } finally {
                executor.shutdown()
            }
        }

    /** Hands back exactly one buffer, then signals and blocks on the next pull. */
    private class OneBufferThenAwait(
        private val reachedSecondPull: CompletableDeferred<Unit>,
    ) : ConsumerBufferManager {
        private var handedOut = false

        override fun checkForNextAudioBuffer(): AudioBuffer? =
            if (handedOut) {
                null
            } else {
                handedOut = true
                AudioBuffer(
                    trackId = 1L,
                    sampleRate = SAMPLE_RATE,
                    frameIndex = 0L,
                    data = ShortArray(4),
                    fadeStartMs = Long.MAX_VALUE,
                    fadeLengthMs = 0L,
                )
            }

        override suspend fun waitForNextAudioBuffer(): AudioBuffer {
            reachedSecondPull.complete(Unit)
            awaitCancellation()
        }

        override suspend fun recycleShortArray(data: ShortArray) = Unit
        override suspend fun drain() = Unit
    }

    /** [BaseSpeaker] with a fixed fake sink position that records which thread reads it. */
    private class PositionRecordingSpeaker(
        bufferManager: ConsumerBufferManager,
        dispatcher: CoroutineDispatcher,
    ) : BaseSpeaker(bufferManager, BluntHatchet(), dispatcher) {
        val readThreads: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override fun onAudioReceived(audio: AudioBuffer) = Unit
        override fun teardown() = Unit

        override fun readSinkPositionMs(): Long {
            readThreads.add(Thread.currentThread().name)
            return SINK_POSITION_MS
        }
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val SINK_POSITION_MS = 1_234L
    }
}
