package net.sigmabeta.chipbox.player.cache.real

import net.sigmabeta.chipbox.player.cache.PcmCacheFormat
import net.sigmabeta.chipbox.player.cache.PcmCacheKey
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Characterization tests pinning the on-disk `.pcm` cache format and the write/read/complete
 * lifecycle of [PcmCacheFile]. These exist to guarantee the okio rewrite preserves byte-for-byte
 * behavior — the header layout, completion gating, atomic rename, and frame round-trip — since
 * the cache is on the playback hot path and can't be runtime-tested here.
 *
 * Run against the real [FileSystem.SYSTEM] on a temp dir so the okio [okio.FileHandle] paths get
 * exercised exactly as in production.
 */
internal class PcmCacheFileTest {

    private val fileSystem = FileSystem.SYSTEM
    private lateinit var workDir: File
    private lateinit var cacheDir: Path

    private val key = PcmCacheKey(sourceHash = "abc123def4567890", trackNumber = 3, sampleRate = 44_100)

    @BeforeTest
    fun setUp() {
        workDir = Files.createTempDirectory("pcmcache-test-").toFile()
        cacheDir = workDir.absolutePath.toPath()
    }

    @AfterTest
    fun tearDown() {
        workDir.deleteRecursively()
    }

    @Test
    fun `write then read round-trips the frames and header fields`() {
        val frames = shortArrayOf(1, 2, 3, 4, 5, 6, 7, 8) // 8 shorts = 4 stereo frames

        val writer = PcmCacheFile.openForWrite(fileSystem, cacheDir, key, trackId = 42L, trackLengthMs = 1234L)
        writer.appendFrames(frames, framesToWrite = 4)
        writer.complete(trackId = 42L, trackLengthMs = 1234L, integratedLufs = -14.5, truePeakDbtp = -1.25)
        writer.promote()

        val reader = assertNotNull(
            PcmCacheFile.openForRead(fileSystem, cacheDir, key),
            "complete file must be readable",
        )
        try {
            assertEquals(44_100, reader.sampleRate)
            assertEquals(4L, reader.totalFrames)
            assertEquals(42L, reader.header.trackId)
            assertEquals(1234L, reader.header.trackLengthMs)
            assertEquals(3, reader.header.trackNumber)
            assertEquals("abc123def4567890", reader.header.sourceHash)
            assertEquals(-14.5, reader.header.integratedLufs)
            assertEquals(-1.25, reader.header.truePeakDbtp)
            assertEquals(PcmCacheFormat.COMPLETION_COMPLETE, reader.header.completion)

            val out = ShortArray(8)
            val read = reader.readFrames(out, atFramePosition = 0L)
            assertEquals(4, read)
            assertContentEquals(frames, out)
        } finally {
            reader.close()
        }
    }

    @Test
    fun `header uses the documented little-endian layout`() {
        val writer = PcmCacheFile.openForWrite(fileSystem, cacheDir, key, trackId = 7L, trackLengthMs = 999L)
        writer.appendFrames(shortArrayOf(10, 20), framesToWrite = 1)
        writer.complete(trackId = 7L, trackLengthMs = 999L, integratedLufs = -9.0, truePeakDbtp = -2.0)
        writer.promote()

        val bytes = fileSystem.read(cacheDir / key.filename()) { readByteArray() }
        assertTrue(bytes.size >= PcmCacheFormat.HEADER_SIZE_BYTES)

        assertEquals(PcmCacheFormat.MAGIC, bytes.intLe(0))
        assertEquals(PcmCacheFormat.VERSION, bytes.intLe(4))
        assertEquals(44_100, bytes.intLe(8))
        assertEquals(PcmCacheFormat.CHANNELS, bytes.shortLe(12))
        assertEquals(PcmCacheFormat.BITS_PER_SAMPLE, bytes.shortLe(14))
        assertEquals(1L, bytes.longLe(16)) // frameCount
        assertEquals(PcmCacheFormat.COMPLETION_COMPLETE, bytes.intLe(24))
        assertEquals(7L, bytes.longLe(44)) // trackId
        assertEquals(3, bytes.intLe(52)) // trackNumber
        assertEquals(999L, bytes.longLe(56)) // trackLengthMs
        assertEquals(-9.0, Double.fromBits(bytes.longLe(64)))
        assertEquals(-2.0, Double.fromBits(bytes.longLe(72)))

        // source hash is ASCII, exactly fills the field at this length
        val hash = String(bytes, 28, PcmCacheFormat.SOURCE_HASH_FIELD_BYTES, Charsets.US_ASCII).trimEnd(' ')
        assertEquals("abc123def4567890", hash)

        // body begins exactly after the fixed header, one stereo frame = 4 bytes
        assertEquals(PcmCacheFormat.HEADER_SIZE_BYTES + PcmCacheFormat.BYTES_PER_FRAME, bytes.size)
        assertEquals(10.toShort(), bytes.shortLe(PcmCacheFormat.HEADER_SIZE_BYTES))
        assertEquals(20.toShort(), bytes.shortLe(PcmCacheFormat.HEADER_SIZE_BYTES + 2))
    }

    @Test
    fun `an incomplete write is not readable and leaves only a temp file`() {
        val writer = PcmCacheFile.openForWrite(fileSystem, cacheDir, key, trackId = 1L, trackLengthMs = 0L)
        writer.appendFrames(shortArrayOf(1, 2, 3, 4), framesToWrite = 2)
        // no complete()

        assertNull(PcmCacheFile.openForRead(fileSystem, cacheDir, key), "in-progress file must read as a miss")
        assertTrue(fileSystem.exists(cacheDir / key.tempFilename()), "temp file should still exist")
        assertFalse(fileSystem.exists(cacheDir / key.filename()), "final file should not exist yet")
        writer.abort()
    }

    @Test
    fun `complete seals the header and promote renames temp to final`() {
        val writer = PcmCacheFile.openForWrite(fileSystem, cacheDir, key, trackId = 1L, trackLengthMs = 0L)
        assertTrue(fileSystem.exists(cacheDir / key.tempFilename()))
        writer.appendFrames(shortArrayOf(1, 2), framesToWrite = 1)
        writer.complete(
            trackId = 1L,
            trackLengthMs = 0L,
            integratedLufs = Double.NaN,
            truePeakDbtp = Double.NEGATIVE_INFINITY,
        )

        // complete() seals the header but DEFERS the rename (Windows can't rename a file while a
        // reader still holds it open), so the temp file is still the one on disk until promote().
        assertTrue(fileSystem.exists(cacheDir / key.tempFilename()), "temp file should remain until promote")
        assertFalse(fileSystem.exists(cacheDir / key.filename()), "final file should not exist before promote")

        writer.promote()
        assertFalse(fileSystem.exists(cacheDir / key.tempFilename()), "temp file should be gone after promote")
        assertTrue(fileSystem.exists(cacheDir / key.filename()), "final file should exist after promote")
    }

    @Test
    fun `abort deletes the temp file`() {
        val writer = PcmCacheFile.openForWrite(fileSystem, cacheDir, key, trackId = 1L, trackLengthMs = 0L)
        writer.appendFrames(shortArrayOf(1, 2), framesToWrite = 1)
        writer.abort()

        assertFalse(fileSystem.exists(cacheDir / key.tempFilename()))
        assertFalse(fileSystem.exists(cacheDir / key.filename()))
    }

    @Test
    fun `reader rejects a complete file whose key does not match`() {
        val writer = PcmCacheFile.openForWrite(fileSystem, cacheDir, key, trackId = 1L, trackLengthMs = 0L)
        writer.appendFrames(shortArrayOf(1, 2), framesToWrite = 1)
        writer.complete(
            trackId = 1L,
            trackLengthMs = 0L,
            integratedLufs = Double.NaN,
            truePeakDbtp = Double.NEGATIVE_INFINITY,
        )
        writer.promote()

        val mismatchedHash = key.copy(sourceHash = "0000000000000000")
        assertNull(
            PcmCacheFile.openForRead(fileSystem, cacheDir, mismatchedHash),
            "stale source hash must read as a miss",
        )
    }

    @Test
    fun `readFrames honors a non-zero start position`() {
        val frames = ShortArray(20) { (it + 1).toShort() } // 10 stereo frames
        val writer = PcmCacheFile.openForWrite(fileSystem, cacheDir, key, trackId = 1L, trackLengthMs = 0L)
        writer.appendFrames(frames, framesToWrite = 10)
        writer.complete(
            trackId = 1L,
            trackLengthMs = 0L,
            integratedLufs = Double.NaN,
            truePeakDbtp = Double.NEGATIVE_INFINITY,
        )
        writer.promote()

        val reader = assertNotNull(PcmCacheFile.openForRead(fileSystem, cacheDir, key))
        try {
            val out = ShortArray(20)
            val read = reader.readFrames(out, atFramePosition = 7L) // skip 7 frames = 14 shorts
            assertEquals(3, read) // 10 - 7 frames remain
            assertContentEquals(shortArrayOf(15, 16, 17, 18, 19, 20), out.copyOfRange(0, 6))
        } finally {
            reader.close()
        }
    }

    private fun ByteArray.intLe(offset: Int): Int =
        ByteBuffer.wrap(this, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private fun ByteArray.shortLe(offset: Int): Short =
        ByteBuffer.wrap(this, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short

    private fun ByteArray.longLe(offset: Int): Long =
        ByteBuffer.wrap(this, offset, 8).order(ByteOrder.LITTLE_ENDIAN).long
}
