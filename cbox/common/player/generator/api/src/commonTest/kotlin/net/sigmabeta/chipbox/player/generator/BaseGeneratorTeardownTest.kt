package net.sigmabeta.chipbox.player.generator

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import net.sigmabeta.chipbox.contentsource.ContentSource
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ProducerBufferManager
import net.sigmabeta.chipbox.player.cache.PcmTrackSource
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.fake.FakeRepository
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression guard for the native-emulator teardown race that crashed slopsf (SIGSEGV in
 * `r3000_setreg`) on a track skip.
 *
 * Native emulators are process-wide singletons over global C state, so the outgoing track's
 * teardown (which frees that state) must complete before the next track loads it. The director
 * sequences a skip as `generator.stop()` then `generator.startTrack(next)`, so [BaseGenerator.stop]
 * must not return until the current source — and therefore the emulator — is fully torn down. The
 * regression made teardown fire-and-forget (`generatorScope.launch { source.close() }`), letting
 * `stop()` return before the close ran so the next load raced the free.
 *
 * [RecordingSource.close] suspends once (via [yield]) before completing. Under a
 * [StandardTestDispatcher] a fire-and-forget close stays queued — so right after `stop()` returns,
 * [RecordingSource.closeCompleted] would still be false. Awaiting the close keeps it inline, so it
 * completes before `stop()` returns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BaseGeneratorTeardownTest {

    @Test
    fun `stop awaits the source close before returning`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val source = RecordingSource()
        val factory = RecordingFactory(source)
        val contentSource = EmptyContentSource()
        val generator = TestGenerator(
            repository = FakeRepository(mapOf(TRACK_ID to trackOf(TRACK_ID))),
            contentSourceRegistry = ContentSourceRegistry(setOf(contentSource)),
            bufferManager = NoopBufferManager(),
            pcmSourceFactory = factory,
            dispatcher = dispatcher,
        )

        generator.startTrack(TRACK_ID)
        advanceUntilIdle() // let the loop load the source and park awaiting the next id
        assertEquals(1, factory.openCount, "the track's source should have been opened")

        generator.stop()

        assertTrue(source.closeStarted, "stop() must close the current source")
        assertTrue(
            source.closeCompleted,
            "stop() must await close() (and thus the singleton emulator's teardown) before " +
                "returning; a fire-and-forget close lets the next track's load race the teardown",
        )
        generator.release()
    }

    @Test
    fun `a cold start resets the buffer pool before producing`() = runTest {
        // The buffer manager is a process singleton; a relaunch that finds the process still alive
        // can inherit a drained pool at the same rate, which setSampleRate would no-op on. A fresh
        // produce loop (no source loaded) must reset it so the pool is rebuilt full.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val bufferManager = RecordingBufferManager()
        val generator = TestGenerator(
            repository = FakeRepository(mapOf(TRACK_ID to trackOf(TRACK_ID))),
            contentSourceRegistry = ContentSourceRegistry(setOf(EmptyContentSource())),
            bufferManager = bufferManager,
            pcmSourceFactory = RecordingFactory(RecordingSource()),
            dispatcher = dispatcher,
        )

        generator.startTrack(TRACK_ID)
        advanceUntilIdle()

        assertEquals(1, bufferManager.resetCount, "a fresh produce loop must rebuild the (possibly stale) pool")
        generator.release()
    }

    /** Minimal concrete [BaseGenerator] that returns a caller-supplied [PcmTrackSource.Factory]. */
    private class TestGenerator(
        repository: Repository,
        contentSourceRegistry: ContentSourceRegistry,
        bufferManager: ProducerBufferManager,
        override val pcmSourceFactory: PcmTrackSource.Factory,
        dispatcher: CoroutineDispatcher,
    ) : BaseGenerator(repository, contentSourceRegistry, bufferManager, BluntHatchet(), dispatcher)

    /** Reports end-of-track immediately (so the loop parks after one cycle) and records whether its
     *  suspending [close] ran to completion. */
    private class RecordingSource : PcmTrackSource {
        override val sampleRate = SAMPLE_RATE
        override val totalFrames: Long? = null
        override val isOver = true

        var closeStarted = false
        var closeCompleted = false

        override suspend fun readFrames(buffer: ShortArray): Int = 0

        override suspend fun seek(framePosition: Long) = Unit

        override fun getLastError(): String? = null

        override suspend fun close() {
            closeStarted = true
            yield() // a fire-and-forget teardown would let stop() return across this suspension
            closeCompleted = true
        }
    }

    private class RecordingFactory(private val source: PcmTrackSource) : PcmTrackSource.Factory {
        var openCount = 0
        override suspend fun open(track: Track, bytes: ByteArray): PcmTrackSource {
            openCount++
            return source
        }
    }

    private class EmptyContentSource : ContentSource {
        override val sourceId = SOURCE_NAME
        override suspend fun openBytes(identifier: String): ByteArray = ByteArray(0)
    }

    private class NoopBufferManager : ProducerBufferManager {
        override suspend fun setSampleRate(sampleRate: Int) = Unit
        override suspend fun getNextEmptyBuffer(): ShortArray = ShortArray(BUFFER_SHORTS)
        override suspend fun sendAudioBuffer(audioBuffer: AudioBuffer) = Unit
        override suspend fun reset() = Unit
    }

    /** Counts [reset] calls so the cold-start test can assert the pool was rebuilt. */
    private class RecordingBufferManager : ProducerBufferManager {
        var resetCount = 0
            private set

        override suspend fun setSampleRate(sampleRate: Int) = Unit
        override suspend fun getNextEmptyBuffer(): ShortArray = ShortArray(BUFFER_SHORTS)
        override suspend fun sendAudioBuffer(audioBuffer: AudioBuffer) = Unit
        override suspend fun reset() {
            resetCount++
        }
    }

    private companion object {
        const val TRACK_ID = 1L
        const val SAMPLE_RATE = 44_100
        const val BUFFER_SHORTS = 8
        const val SOURCE_NAME = "test"

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
