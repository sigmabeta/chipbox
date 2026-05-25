package net.sigmabeta.chipbox.player.speaker.file

import kotlinx.coroutines.Dispatchers
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.sage.logging.BluntHatchet
import okio.FileSystem
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
import kotlin.test.assertTrue

/**
 * Drives [FileSpeaker] directly via [FileSpeaker.onAudioReceived] / [FileSpeaker.teardown] rather
 * than through the [Speaker] consume loop — the loop is irrelevant to the WAV-writing contract.
 * The [ConsumerBufferManager] is a no-op stub for the same reason.
 */
internal class FileSpeakerTest {

    private lateinit var workDir: File
    private lateinit var outputFile: File

    private val noopBuffers = object : ConsumerBufferManager {
        override fun checkForNextAudioBuffer(): AudioBuffer? = null
        override suspend fun waitForNextAudioBuffer(): AudioBuffer = error("unused")
        override suspend fun recycleShortArray(data: ShortArray) = Unit
        override suspend fun drain() = Unit
    }

    private lateinit var underTest: FileSpeaker

    @BeforeTest
    fun setUp() {
        workDir = Files.createTempDirectory("filespeaker-test-").toFile()
        outputFile = File(File(workDir, FileSpeaker.FOLDER_NAME), "temp.wav")
        underTest = FileSpeaker(workDir.absolutePath.toPath(), FileSystem.SYSTEM, BluntHatchet(), noopBuffers, Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() {
        underTest.release()
        workDir.deleteRecursively()
    }

    @Test
    fun `teardown without any audio produces no file`() {
        underTest.teardown()

        assertFalse(outputFile.exists(), "no file should be created when no audio was received")
    }

    @Test
    fun `writing one buffer produces a 44-byte canonical PCM WAV header`() {
        val sampleRate = 44_100
        val pcm = shortArrayOf(0x0100, 0x0200, 0x0300, 0x0400) // 4 samples = 2 stereo frames

        underTest.onAudioReceived(audioBuffer(sampleRate, pcm))
        underTest.teardown()

        val header = outputFile.readBytes().copyOfRange(0, 44)

        assertEquals("RIFF", header.asciiAt(0, 4))
        assertEquals(pcm.size * BYTES_PER_SAMPLE + RIFF_OVERHEAD, header.intLeAt(4))
        assertEquals("WAVE", header.asciiAt(8, 4))
        assertEquals("fmt ", header.asciiAt(12, 4))
        assertEquals(FMT_CHUNK_SIZE, header.intLeAt(16))
        assertEquals(PCM_FORMAT, header.shortLeAt(20))
        assertEquals(STEREO, header.shortLeAt(22))
        assertEquals(sampleRate, header.intLeAt(24))
        assertEquals(sampleRate * STEREO * BYTES_PER_SAMPLE, header.intLeAt(28))
        assertEquals((STEREO * BYTES_PER_SAMPLE).toShort(), header.shortLeAt(32))
        assertEquals((BYTES_PER_SAMPLE * BITS_PER_BYTE).toShort(), header.shortLeAt(34))
        assertEquals("data", header.asciiAt(36, 4))
        assertEquals(pcm.size * BYTES_PER_SAMPLE, header.intLeAt(40))
    }

    @Test
    fun `samples are written little-endian and PCM payload is not corrupted`() {
        // 64 samples = 128 bytes of PCM, so PCM extends well past file offset 64 — the previous
        // bug clobbered four PCM bytes at offset 64 by writing the data-size field into the
        // wrong place. This test catches a regression of that.
        val sampleRate = 32_000
        val pcm = ShortArray(64) { (it + 1).toShort() }

        underTest.onAudioReceived(audioBuffer(sampleRate, pcm))
        underTest.teardown()

        val bytes = outputFile.readBytes()
        assertEquals(WAV_HEADER_SIZE + pcm.size * BYTES_PER_SAMPLE, bytes.size)

        val expectedPcm = ByteBuffer.allocate(pcm.size * BYTES_PER_SAMPLE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply { pcm.forEach { putShort(it) } }
            .array()
        assertContentEquals(expectedPcm, bytes.copyOfRange(WAV_HEADER_SIZE, bytes.size))
    }

    @Test
    fun `a second teardown is a safe no-op`() {
        underTest.onAudioReceived(audioBuffer(44_100, shortArrayOf(1, 2, 3, 4)))
        underTest.teardown()
        val firstSize = outputFile.length()

        underTest.teardown()

        assertEquals(firstSize, outputFile.length())
    }

    @Test
    fun `a sample rate change reopens the output stream`() {
        underTest.onAudioReceived(audioBuffer(44_100, shortArrayOf(1, 2)))
        underTest.onAudioReceived(audioBuffer(48_000, shortArrayOf(3, 4, 5, 6)))
        underTest.teardown()

        // The current FileSpeaker reuses temp.wav — the second buffer's open overwrites the first.
        // What we care about here is that the header reflects the *second* (post-change) state:
        // sample rate 48000, data size = 4 samples * 2 bytes = 8.
        val header = outputFile.readBytes().copyOfRange(0, WAV_HEADER_SIZE)
        assertEquals(48_000, header.intLeAt(24))
        assertEquals(8, header.intLeAt(40))
        assertTrue(outputFile.length() == WAV_HEADER_SIZE.toLong() + 8L)
    }

    private fun audioBuffer(sampleRate: Int, pcm: ShortArray) = AudioBuffer(
        trackId = 1L,
        sampleRate = sampleRate,
        frameIndex = 0L,
        data = pcm,
        fadeStartMs = Long.MAX_VALUE,
        fadeLengthMs = 0L,
    )

    private fun ByteArray.asciiAt(offset: Int, length: Int): String =
        String(this, offset, length, Charsets.US_ASCII)

    private fun ByteArray.intLeAt(offset: Int): Int =
        ByteBuffer.wrap(this, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private fun ByteArray.shortLeAt(offset: Int): Short =
        ByteBuffer.wrap(this, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short

    companion object {
        private const val WAV_HEADER_SIZE = 44

        /** Bytes per 16-bit PCM sample. */
        private const val BYTES_PER_SAMPLE = 2
        private const val BITS_PER_BYTE = 8
        private const val STEREO: Short = 2
        private const val PCM_FORMAT: Short = 1
        private const val FMT_CHUNK_SIZE = 16

        /** RIFF chunk size = (total file size) - 8 = header_size - 8 + data_size = 36 + data_size. */
        private const val RIFF_OVERHEAD = WAV_HEADER_SIZE - 8
    }
}
