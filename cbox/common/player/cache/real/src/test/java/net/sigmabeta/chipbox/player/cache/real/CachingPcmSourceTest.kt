package net.sigmabeta.chipbox.player.cache.real

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.cache.PcmCacheKey
import net.sigmabeta.chipbox.player.cache.PcmTrackSource
import net.sigmabeta.chipbox.player.cache.fake.FakePcmTrackSource
import net.sigmabeta.sage.logging.BluntHatchet
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the render-ahead [CachingPcmSource]. Uses a scriptable [FakePcmTrackSource] in place
 * of the live [EmulatorPcmSource] and a real [PcmCacheFile.Writer] backed by [FileSystem.SYSTEM]
 * — the same pattern [PcmCacheFileTest] and [PcmCacheJanitorTest] use — because okio's
 * `FakeFileSystem` refuses to open a read handle on a file currently held for writing, and the
 * cache deliberately holds both a writer handle and a reader handle on the same `.pcm.tmp` at
 * once.
 *
 * `UnconfinedTestDispatcher` makes the writer's `init { launch { ... } }` body run synchronously
 * on the test thread, so for a fake source that hands back a finite chunk sequence the writer is
 * already complete by the time `CachingPcmSource(...)` returns. The producer / consumer race the
 * production code coordinates collapses to a deterministic in-test sequence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class CachingPcmSourceTest {

    private val fileSystem = FileSystem.SYSTEM
    private lateinit var workDir: File
    private lateinit var cacheDir: Path

    @BeforeTest
    fun setUp() {
        workDir = Files.createTempDirectory("caching-pcm-source-").toFile()
        cacheDir = workDir.absolutePath.toPath()
    }

    @AfterTest
    fun tearDown() {
        workDir.deleteRecursively()
    }

    // ---- happy path ----

    @Test
    fun `writer renders a short audible track to completion and promotes the cache file on close`() = runTest {
        val fake = FakePcmTrackSource(sampleRate = 1000).apply { enqueueAudible(2000) }
        var completeCalls = 0

        val source = newCachingSource(fake, onComplete = { completeCalls++ })

        assertEquals(1, completeCalls, "onWriteComplete must fire exactly once on success")
        // The header is sealed on completion, but the rename is DEFERRED to close(): Windows can't
        // rename the temp file while the render-ahead read handle is open. So until close, the sealed
        // .pcm.tmp is still the file on disk and the final .pcm does not exist yet.
        assertTrue(fileSystem.exists(tempPath()), ".pcm.tmp should remain (sealed) until close")
        assertFalse(fileSystem.exists(finalPath()), ".pcm should not exist until close promotes it")

        source.close()
        assertTrue(fileSystem.exists(finalPath()), ".pcm should exist after close promotes the temp file")
        assertFalse(fileSystem.exists(tempPath()), ".pcm.tmp should be gone after promotion")
    }

    @Test
    fun `readFrames hands back every cached frame in order`() = runTest {
        // Writer runs to completion first (Unconfined); the reader then drains the cache file
        // via random-access reads. A single read of buffer.size/2 frames covers all 2000 here.
        val fake = FakePcmTrackSource(sampleRate = 1000).apply {
            enqueueAudible(2000, pattern = 500)
        }
        val source = newCachingSource(fake)

        val buffer = ShortArray(8_192) // capacity = 4096 frames
        val frames = source.readFrames(buffer)
        assertEquals(2000, frames, "should return every written frame in a single read")
        // Pattern check on a sample — both channels should carry the encoded value.
        assertEquals(500.toShort(), buffer[0])
        assertEquals(500.toShort(), buffer[1])
        assertEquals(500.toShort(), buffer[2 * 1999])
        assertTrue(source.isOver, "after cursor catches the watermark on a complete write, isOver flips")
        source.close()
    }

    // ---- silence handling ----

    @Test
    fun `trailing silence over the threshold is trimmed from the cache`() = runTest {
        // At sampleRate=1000, silenceTrimFrames = 5 * 1000 = 5000. 6000 trailing silent frames
        // exceed that, so the writer drops them and seals where the audible content ended.
        val fake = FakePcmTrackSource(sampleRate = 1000).apply {
            enqueueAudible(1000)
            enqueueSilent(6000)
        }
        val source = newCachingSource(fake)

        val buffer = ShortArray(16_384)
        val frames = source.readFrames(buffer)
        assertEquals(1000, frames, "trailing silence should not land in the cache")
        source.close()
    }

    @Test
    fun `mid-track silent gap shorter than the threshold persists as zero frames`() = runTest {
        // pendingSilentFrames < silenceTrimFrames AND followed by audible → the silent gap is
        // committed as zeros so playback stays in time. 200 silent frames < 5000 threshold.
        val fake = FakePcmTrackSource(sampleRate = 1000).apply {
            enqueueAudible(1000, pattern = 500)
            enqueueSilent(200)
            enqueueAudible(1000, pattern = 800)
        }
        val source = newCachingSource(fake)

        val buffer = ShortArray(16_384)
        val frames = source.readFrames(buffer)
        assertEquals(2200, frames, "audio + gap + audio should round-trip whole")
        // Spot-check that the gap landed as zeros at the right offset.
        val gapStart = 1000 * 2 // frames * 2 channels
        assertEquals(0.toShort(), buffer[gapStart])
        assertEquals(0.toShort(), buffer[gapStart + 1])
        // And that the post-gap audible chunk uses the second pattern.
        val postGapStart = (1000 + 200) * 2
        assertEquals(800.toShort(), buffer[postGapStart])
        source.close()
    }

    @Test
    fun `an entirely silent track surfaces as a writer error and aborts the cache`() = runTest {
        // pendingSilentFrames hits the threshold while framesWritten == 0 → not "trailing
        // silence", it's a track that produced nothing at all. Sealing it as an empty,
        // "complete" cache entry would make every future play of this track return silence
        // instantly — surface as an error instead.
        val fake = FakePcmTrackSource(sampleRate = 1000).apply { enqueueSilent(6000) }
        var abortCalls = 0
        var completeCalls = 0

        val source = newCachingSource(fake, onComplete = { completeCalls++ }, onAbort = { abortCalls++ })

        assertEquals(0, completeCalls, "complete must not fire when the writer errors out")
        assertEquals(1, abortCalls, "onWriteAbort must fire on an empty-track error")
        assertFalse(fileSystem.exists(finalPath()), "no .pcm should be sealed for an empty track")
        val message = source.getLastError()
        assertNotNull(message, "writer error must surface via getLastError")
        assertTrue(message.contains("no audio"), "expected the no-audio diagnostic; got '$message'")
        source.close()
    }

    // ---- cancellation / teardown ----

    @Test
    fun `close on an in-progress writer aborts the cache and fires onWriteAbort`() = runTest {
        // No chunks enqueued, parkOnEmpty set → the writer's first readFrames suspends
        // cancellably. close() cancel-joins the writer job, which hits the catch
        // (CancellationException) branch in CachingPcmSource — writer.abort + onWriteAbort.
        //
        // Implementation quirk locked in here: onWriteAbort actually fires *twice* on the cancel
        // path — once from the writer body's CancellationException catch, and a second time from
        // close()'s `if (!writerComplete)` safety net (intended for the case where the writer
        // coroutine never started, but it also runs on the normal cancel path). Production is
        // fine with this — `janitor.markIdle` is idempotent — but a future code change that
        // de-duplicates would visibly flip this expectation.
        val fake = FakePcmTrackSource(sampleRate = 1000).apply { parkOnEmpty() }
        var abortCalls = 0
        var completeCalls = 0

        val source = newCachingSource(fake, onComplete = { completeCalls++ }, onAbort = { abortCalls++ })

        source.close()

        assertEquals(0, completeCalls, "complete must not fire when the writer was cancelled")
        assertEquals(2, abortCalls, "documented double-fire of onWriteAbort on cancel; see comment")
        assertFalse(fileSystem.exists(finalPath()), "no .pcm should be sealed when the writer was cancelled")
        assertTrue(fake.closed, "the wrapped emulator source must be closed too")
    }

    @Test
    fun `close on a completed writer still closes the wrapped emulator source`() = runTest {
        val fake = FakePcmTrackSource(sampleRate = 1000).apply { enqueueAudible(500) }

        val source = newCachingSource(fake)
        source.close()

        assertTrue(fake.closed)
    }

    @Test
    fun `getDiagnostics passes through to the emulator source`() = runTest {
        // Diagnostics are observational only — verify the delegation so the debug PlaybackStatus
        // screen continues to see what the underlying source reports.
        val fake = object : PcmTrackSource by FakePcmTrackSource(sampleRate = 1000) {
            override fun getDiagnostics(): String? = "diag-payload"
        }
        val source = newCachingSource(fake)

        assertEquals("diag-payload", source.getDiagnostics())
        source.close()
    }

    @Test
    fun `seek moves the read cursor`() = runTest {
        // Seek inside the cached range is a pure cursor move — verify the next read picks up at
        // the requested frame rather than the start.
        val fake = FakePcmTrackSource(sampleRate = 1000).apply {
            // Two clearly different patterns so we can spot which range the post-seek read served.
            enqueueAudible(500, pattern = 100)
            enqueueAudible(500, pattern = 700)
        }
        val source = newCachingSource(fake)

        source.seek(framePosition = 500)
        val buffer = ShortArray(8_192)
        val frames = source.readFrames(buffer)
        assertEquals(500, frames, "should read the second 500 frames only")
        assertEquals(700.toShort(), buffer[0], "post-seek read should serve the second chunk's pattern")
        source.close()
    }

    // ---- helpers ----

    private val key = PcmCacheKey(sourceHash = "test", trackNumber = 0, sampleRate = 1000)

    private fun finalPath(): Path = cacheDir / key.filename()
    private fun tempPath(): Path = cacheDir / key.tempFilename()

    private fun TestScope.newCachingSource(
        emulator: PcmTrackSource,
        onComplete: () -> Unit = {},
        onAbort: () -> Unit = {},
    ): CachingPcmSource {
        val writer = PcmCacheFile.openForWrite(
            fileSystem = fileSystem,
            cacheDir = cacheDir,
            key = key,
            trackId = 1L,
            trackLengthMs = 0L,
        )
        return CachingPcmSource(
            emulatorSource = emulator,
            writer = writer,
            fileSystem = fileSystem,
            track = Track(
                id = 1L,
                path = "/library/test.psf",
                source = "test",
                title = "Test Track",
                trackLengthMs = 0L,
                trackNumber = 0,
                fadeLengthMs = 0L,
                game = null,
                artists = null,
                platform = Platform.OTHER,
            ),
            key = key,
            hatchet = BluntHatchet(),
            onWriteComplete = onComplete,
            onWriteAbort = onAbort,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )
    }
}
