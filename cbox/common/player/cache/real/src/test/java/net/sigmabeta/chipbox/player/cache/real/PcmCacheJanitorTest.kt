package net.sigmabeta.chipbox.player.cache.real

import net.sigmabeta.chipbox.player.cache.PcmCacheKey
import net.sigmabeta.sage.logging.BluntHatchet
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Characterization tests for [PcmCacheJanitor]: startup cleanup of partial/corrupt files and
 * the mtime-ordered LRU cap. The mtime eviction is the behavior most at risk in the okio
 * rewrite (okio has no portable set-mtime), so these pin the eviction order and in-use
 * protection explicitly.
 */
internal class PcmCacheJanitorTest {

    private lateinit var cacheDir: File

    @BeforeTest
    fun setUp() {
        cacheDir = Files.createTempDirectory("janitor-test-").toFile()
    }

    @AfterTest
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun `startup cleanup removes temp and corrupt files but keeps complete ones`() {
        val complete = writeCompleteFile(keyFor("aaaaaaaaaaaaaaaa"), frames = 1, mtime = 1_000L)
        // a .pcm.tmp left by an interrupted write
        PcmCacheFile.openForWrite(cacheDir, keyFor("bbbbbbbbbbbbbbbb"), trackId = 1L, trackLengthMs = 0L)
        val tmp = File(cacheDir, keyFor("bbbbbbbbbbbbbbbb").tempFilename())
        // a malformed .pcm (header can't be parsed)
        val corrupt = File(cacheDir, "garbage.pcm").apply { writeBytes(ByteArray(10)) }

        assertTrue(tmp.exists() && corrupt.exists())

        janitor().runStartupCleanup()

        assertTrue(complete.exists(), "complete .pcm must survive")
        assertFalse(tmp.exists(), ".pcm.tmp must be swept")
        assertFalse(corrupt.exists(), "unparseable .pcm must be swept")
    }

    @Test
    fun `startup cleanup only does work once`() {
        val janitor = janitor()
        janitor.runStartupCleanup()

        // A temp file created after the first run should NOT be swept by a second call.
        PcmCacheFile.openForWrite(cacheDir, keyFor("cccccccccccccccc"), trackId = 1L, trackLengthMs = 0L)
        val tmp = File(cacheDir, keyFor("cccccccccccccccc").tempFilename())

        janitor.runStartupCleanup()

        assertTrue(tmp.exists(), "second runStartupCleanup() should be a no-op")
    }

    @Test
    fun `enforceCap evicts oldest-by-mtime first until under the cap`() {
        val oldest = writeCompleteFile(keyFor("1111111111111111"), frames = 1, mtime = 1_000L)
        val middle = writeCompleteFile(keyFor("2222222222222222"), frames = 1, mtime = 2_000L)
        val newest = writeCompleteFile(keyFor("3333333333333333"), frames = 1, mtime = 3_000L)

        // Each file is 132 bytes (128 header + 4 body). Total 396; cap 300 forces one eviction.
        janitor(capBytes = 300L).enforceCap()

        assertFalse(oldest.exists(), "oldest file should be evicted")
        assertTrue(middle.exists(), "second-oldest should remain (under cap after one eviction)")
        assertTrue(newest.exists(), "newest should remain")
    }

    @Test
    fun `enforceCap never evicts an in-use file even if it is oldest`() {
        val inUseOldest = writeCompleteFile(keyFor("4444444444444444"), frames = 1, mtime = 1_000L)
        val b = writeCompleteFile(keyFor("5555555555555555"), frames = 1, mtime = 2_000L)
        val c = writeCompleteFile(keyFor("6666666666666666"), frames = 1, mtime = 3_000L)
        val d = writeCompleteFile(keyFor("7777777777777777"), frames = 1, mtime = 4_000L)

        val janitor = janitor(capBytes = 300L)
        janitor.markInUse(inUseOldest)
        // Non-protected candidates b,c,d = 396 bytes; evicting the oldest non-protected (b) drops to 264.
        janitor.enforceCap()

        assertTrue(inUseOldest.exists(), "in-use file must survive even though it is the oldest")
        assertFalse(b.exists(), "oldest non-protected file should be evicted")
        assertTrue(c.exists())
        assertTrue(d.exists())
    }

    private fun janitor(capBytes: Long = PcmCacheJanitor.DEFAULT_CAP_BYTES) =
        PcmCacheJanitor(cacheDir, capBytes, BluntHatchet())

    private fun keyFor(hash: String) = PcmCacheKey(sourceHash = hash, trackNumber = 0, sampleRate = 44_100)

    private fun writeCompleteFile(key: PcmCacheKey, frames: Int, mtime: Long): File {
        val writer = PcmCacheFile.openForWrite(cacheDir, key, trackId = 1L, trackLengthMs = 0L)
        writer.appendFrames(ShortArray(frames * 2), frames)
        writer.complete(trackId = 1L, trackLengthMs = 0L, integratedLufs = Double.NaN, truePeakDbtp = Double.NEGATIVE_INFINITY)
        return File(cacheDir, key.filename()).apply { setLastModified(mtime) }
    }
}
