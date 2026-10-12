package net.sigmabeta.chipbox.player.generator

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.contentsource.ContentSource
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ProducerBufferManager
import net.sigmabeta.chipbox.player.cache.PcmTrackSource
import net.sigmabeta.chipbox.player.common.SHORTS_PER_FRAME
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.fake.FakeRepository
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression guard for the render-ahead "silent gap" discontinuity.
 *
 * A [PcmTrackSource] backed by a render-ahead cache writer is allowed to return a *short* read: it
 * hands back only the frames the writer has committed so far, which can be a fraction of the
 * generator's buffer. [BaseGenerator.fillBuffer] must wait out that mid-buffer starvation (the same
 * way its first read does) and top the buffer up to a full, contiguous block. If the top-up read
 * stops on the first 0, the still-empty tail gets zero-filled and the emitted buffer carries an
 * audible gap — a first (cache-miss) play that a cached replay never has.
 *
 * [RenderAheadRaceSource] scripts exactly that sequence: a partial first chunk, then a 0 read that
 * reports [PcmTrackSource.awaitingRender] (the writer momentarily behind the cursor), then the rest
 * of the buffer. The test asserts the emitted buffer is fully audible — no injected zeros.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BaseGeneratorRenderAheadFillTest {

    @Test
    fun `a mid-buffer render-ahead stall is filled, not zero-padded`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val bufferManager = RecordingBufferManager()
        val generator = TestGenerator(
            repository = FakeRepository(mapOf(TRACK_ID to trackOf(TRACK_ID))),
            contentSourceRegistry = ContentSourceRegistry(setOf(EmptyContentSource())),
            bufferManager = bufferManager,
            pcmSourceFactory = SingleSourceFactory(RenderAheadRaceSource()),
            dispatcher = dispatcher,
        )

        generator.startTrack(TRACK_ID)
        advanceUntilIdle()

        val first = bufferManager.sent.firstOrNull()
        requireNotNull(first) { "the generator should have emitted at least one buffer" }
        assertEquals(
            BUFFER_SHORTS,
            first.size,
            "the emitted buffer is the fixed pool array",
        )
        for (i in first.indices) {
            assertEquals(
                PATTERN,
                first[i],
                "buffer sample $i was zero-filled instead of waited-for render output",
            )
        }
        generator.release()
    }

    private class TestGenerator(
        repository: Repository,
        contentSourceRegistry: ContentSourceRegistry,
        bufferManager: ProducerBufferManager,
        override val pcmSourceFactory: PcmTrackSource.Factory,
        dispatcher: CoroutineDispatcher,
    ) : BaseGenerator(repository, contentSourceRegistry, bufferManager, BluntHatchet(), dispatcher)

    private class SingleSourceFactory(private val source: PcmTrackSource) : PcmTrackSource.Factory {
        override suspend fun open(track: Track, bytes: ByteArray): PcmTrackSource = source
    }

    /**
     * Emits [PARTIAL_FRAMES] frames, then reports a render stall (0 frames with
     * [awaitingRender]), then delivers the remainder. Total audible output is [BUFFER_SHORTS] /
     * [SHORTS_PER_FRAME] frames, so a correct fill produces one all-[PATTERN] buffer.
     */
    private class RenderAheadRaceSource : PcmTrackSource {
        override val sampleRate = SAMPLE_RATE
        override val totalFrames: Long? = null
        override val loudnessLufs = Double.NaN
        override val truePeakDbtp = Double.NEGATIVE_INFINITY

        private var step = 0
        private var endReached = false

        override val isOver: Boolean get() = endReached

        private var awaiting = false
        override val awaitingRender: Boolean get() = awaiting

        override suspend fun readFrames(buffer: ShortArray): Int {
            if (endReached) return 0
            val capacityFrames = buffer.size / SHORTS_PER_FRAME
            return when (step) {
                0 -> {
                    step++
                    val frames = minOf(capacityFrames, PARTIAL_FRAMES)
                    write(buffer, frames)
                    frames
                }

                1 -> {
                    // The reader caught the writer mid-buffer: nothing more committed yet.
                    step++
                    awaiting = true
                    0
                }

                else -> {
                    step++
                    awaiting = false
                    endReached = true
                    val frames = minOf(capacityFrames, FULL_FRAMES - PARTIAL_FRAMES)
                    write(buffer, frames)
                    frames
                }
            }
        }

        override suspend fun seek(framePosition: Long) = Unit

        override fun getLastError(): String? = null

        override suspend fun close() = Unit

        private fun write(buffer: ShortArray, frames: Int) {
            for (i in 0 until frames * SHORTS_PER_FRAME) buffer[i] = PATTERN
        }
    }

    private class RecordingBufferManager : ProducerBufferManager {
        val sent: MutableList<ShortArray> = mutableListOf()

        override suspend fun setSampleRate(sampleRate: Int) = Unit

        override suspend fun getNextEmptyBuffer(): ShortArray = ShortArray(BUFFER_SHORTS)

        override suspend fun sendAudioBuffer(audioBuffer: AudioBuffer) {
            sent += audioBuffer.data.copyOf()
        }

        override suspend fun reset() = Unit
    }

    private class EmptyContentSource : ContentSource {
        override val sourceId = SOURCE_NAME
        override suspend fun openBytes(identifier: String): ByteArray = ByteArray(0)
    }

    private companion object {
        const val TRACK_ID = 1L
        const val SAMPLE_RATE = 1_000
        const val BUFFER_SHORTS = 4_096
        const val FULL_FRAMES = BUFFER_SHORTS / SHORTS_PER_FRAME
        const val PARTIAL_FRAMES = 1_000
        const val SOURCE_NAME = "test"
        const val PATTERN: Short = 1_234

        fun trackOf(id: Long): Track = Track(
            id = id,
            path = "/library/track-$id.psf",
            source = SOURCE_NAME,
            title = "Track $id",
            trackLengthMs = 60_000L,
            trackNumber = 0,
            fadeLengthMs = 0L,
            game = null,
            artists = null,
            platform = Platform.OTHER,
        )
    }
}
