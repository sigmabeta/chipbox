package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * CRI HCA (`.hca`) metadata reader. Fixtures are synthetic bytes laid out exactly as
 * `cbox/native/vgmstream/Core/vgmstream/src/coding/libs/clhca.c` reads them (base header, `fmt`,
 * `comp`/`dec`, optional `loop` and `ciph` chunks, trailing CRC-16) — there is **no HCA file in the
 * local corpus**, so every expectation is a literal derived from the upstream arithmetic.
 */
class HcaReaderTest {
    private val reader = HcaReader(BluntHatchet())

    @Test
    fun `the hca extension resolves to the HCA reader at version 1`() {
        val readers = Readers(BluntHatchet())

        assertSame(readers.hca, readers.forExtension("hca"))
        assertEquals(1, readers.readerVersionFor("hca"))
    }

    @Test
    fun `a non-looping header reports its sample count and stream defaults`() {
        val bytes = hcaFile(blockCount = 44)

        val track = singleTrack(reader.readTracksFromFile(bytes, "music/song.hca"))

        // 44 blocks x 1024 samples = 45_056 samples; 45_056 * 1000 / 44_100 = 1021 ms (truncating).
        assertEquals(1_021L, track.length)
        assertEquals(TAG_UNKNOWN, track.title)
        assertEquals(TAG_UNKNOWN, track.artist)
        assertEquals(TAG_UNKNOWN, track.game)
        assertEquals(0L, track.fadeLengthMs)
        // Streamed readers number subsongs 1-based (see subsongTrackNumber).
        assertEquals(1, track.trackNumber)
        assertEquals("music/song.hca", track.path)
        assertEquals("", track.source)
        assertEquals(Platform.OTHER, track.platform)
    }

    @Test
    fun `the encoder delay and padding are removed from the sample count`() {
        val bytes = hcaFile(blockCount = 44, encoderDelay = 128, encoderPadding = 32)

        val track = singleTrack(reader.readTracksFromFile(bytes, "music/song.hca"))

        // 45_056 - 128 - 32 = 44_896 samples; 44_896 * 1000 / 44_100 = 1018 ms.
        assertEquals(1_018L, track.length)
    }

    @Test
    fun `a looping header plays two loops plus a ten second fade`() {
        val bytes = hcaFile(
            blockCount = 100,
            loop = HcaFixtureLoop(startFrame = 10, endFrame = 99, endPadding = 512),
        )

        val track = singleTrack(reader.readTracksFromFile(bytes, "music/loop.hca"))

        // loop start = 10 * 1024 = 10_240 samples
        // loop end   = 99 * 1024 + (1024 - 512) = 101_888 samples
        // 10_240 + (101_888 - 10_240) * 2 + 10 * 44_100 = 634_536 samples
        // 634_536 * 1000 / 44_100 = 14_388 ms (truncating)
        assertEquals(14_388L, track.length)
    }

    @Test
    fun `an encrypted payload reports the same metadata without any key material`() {
        val loop = HcaFixtureLoop(startFrame = 10, endFrame = 40)
        val clear = hcaFile(blockCount = 44, loop = loop)
        val encrypted = hcaFile(blockCount = 44, loop = loop, cipherType = 56)

        val clearTrack = singleTrack(reader.readTracksFromFile(clear, "music/song.hca"))
        val encryptedTrack = singleTrack(reader.readTracksFromFile(encrypted, "music/song.hca"))

        // `ciph` type 56 encrypts the frame payload only. The reader has no key parameter and never
        // reads one, so an encrypted file must yield byte-identical metadata.
        assertEquals(clearTrack.length, encryptedTrack.length)
        assertEquals(clearTrack.title, encryptedTrack.title)
        assertEquals(clearTrack.trackNumber, encryptedTrack.trackNumber)
        assertTrue(assertNotNull(readHcaHeader(encrypted, 0)).encrypted)
        assertFalse(assertNotNull(readHcaHeader(clear, 0)).encrypted)
    }

    @Test
    fun `the signature check tolerates CRI's high-bit header obfuscation`() {
        // Upstream compares `(u32be & 0x7F7F7F7F) == "HCA\0"`, because an encrypted HCA may have
        // the high bit of each header magic byte set.
        val bytes = hcaFile(blockCount = 44, maskSignature = true)

        val track = singleTrack(reader.readTracksFromFile(bytes, "music/song.hca"))

        assertEquals(1_021L, track.length)
    }

    @Test
    fun `a file without a valid HCA signature yields no tracks`() {
        for (signature in listOf("XCA\u0000", "HCA ", "hca\u0000")) {
            val bytes = hcaFile(blockCount = 4)
            signature.forEachIndexed { index, char -> bytes[index] = char.code.toByte() }

            assertNull(reader.readTracksFromFile(bytes, "song.hca"), "signature '$signature'")
        }
    }

    @Test
    fun `a truncated header yields no tracks instead of throwing`() {
        val bytes = hcaFile(blockCount = 4)

        // Anything shorter than the declared header_size is a miss; the reader never throws. (A
        // truncated *payload* is not a header truncation — metadata is still readable.)
        for (length in listOf(0, 1, 7, 8, 0x20, 40, 41)) {
            assertNull(reader.readTracksFromFile(bytes.copyOf(length), "song.hca"), "truncated to $length")
        }
    }

    @Test
    fun `a header size past the end of the file yields no tracks`() {
        val bytes = hcaFile(blockCount = 4, payload = false)
        writeShortBe(bytes, 0x06, bytes.size + 1)

        assertNull(reader.readTracksFromFile(bytes, "song.hca"))
    }

    @Test
    fun `unsupported versions and implausible fmt fields yield no tracks`() {
        assertNull(reader.readTracksFromFile(hcaFile(version = 0x9999), "song.hca"))
        assertNull(reader.readTracksFromFile(hcaFile(blockCount = 0), "song.hca"))
        assertNull(reader.readTracksFromFile(hcaFile(channels = 0), "song.hca"))
        assertNull(reader.readTracksFromFile(hcaFile(channels = 17), "song.hca"))
        assertNull(reader.readTracksFromFile(hcaFile(sampleRate = 0), "song.hca"))
    }

    @Test
    fun `a header without a fmt chunk yields no tracks`() {
        val bytes = hcaFile(blockCount = 4)
        writeFixedAscii(bytes, 0x08, "bad\u0000", 4)
        resignHca(bytes)

        assertNull(reader.readTracksFromFile(bytes, "song.hca"))
    }

    @Test
    fun `loop points outside the stream yield no tracks`() {
        // Upstream requires loop_end_frame < block_count; a loop ending on the last block is invalid.
        val bytes = hcaFile(blockCount = 44, loop = HcaFixtureLoop(startFrame = 0, endFrame = 44))

        assertNull(reader.readTracksFromFile(bytes, "song.hca"))
    }

    @Test
    fun `an unknown cipher type yields no tracks`() {
        assertNull(reader.readTracksFromFile(hcaFile(blockCount = 4, cipherType = 2), "song.hca"))
    }

    @Test
    fun `the header helper reads an embedded stream at an offset`() {
        val embedded = hcaFile(blockCount = 44, loop = HcaFixtureLoop(startFrame = 10, endFrame = 40))
        val container = cat(ByteArray(0x123) { 0x5A }, embedded, ByteArray(0x10))

        // Not at offset 0: nothing parses.
        assertNull(readHcaHeader(container, 0))

        // The reusable helper (tickets 08/09) parses a cue that starts mid-container.
        val header = assertNotNull(readHcaHeader(container, 0x123))
        assertEquals(2, header.channels)
        assertEquals(44_100, header.sampleRate)
        assertEquals(0x200, header.blockSize)
        assertEquals(44L, header.blockCount)
        assertEquals(10L * HCA_FRAME_SAMPLES, header.loop?.startSample)
        assertEquals(40L * HCA_FRAME_SAMPLES + HCA_FRAME_SAMPLES, header.loop?.endSample)
        // 44 blocks * 1024 samples, no encoder delay/padding.
        assertEquals(45_056L, header.sampleCount)
    }

    @Test
    fun `an out-of-range offset is never a header`() {
        val bytes = hcaFile(blockCount = 4)

        assertNull(readHcaHeader(bytes, -1))
        assertNull(readHcaHeader(bytes, bytes.size))
        assertNull(readHcaHeader(bytes, bytes.size + 0x100))
    }

    @Test
    fun `the header checksum is a CRC-16 over the header`() {
        // Independent check value: MSB-first CRC-16, polynomial 0x8005, init 0, no reflection or
        // final xor (the algorithm behind clhca.c's hcacommon_crc_mask_table).
        assertEquals(0xFEE8, hcaCrc16("123456789".encodeToByteArray(), 0, 9))
    }

    @Test
    fun `a header with a corrupted checksum yields no tracks`() {
        val bytes = hcaFile(blockCount = 4, corruptCrc = true)

        assertNull(reader.readTracksFromFile(bytes, "song.hca"))
    }

    @Test
    fun `a v1-x dec chunk is read like comp`() {
        val bytes = hcaFile(blockCount = 44, decChunk = true)

        val track = singleTrack(reader.readTracksFromFile(bytes, "music/song.hca"))

        assertEquals(1_021L, track.length)
    }

    @Test
    fun `a v3 comp header is accepted with a ranged resolution`() {
        val bytes = hcaFile(version = HCA_VERSION_300, blockCount = 44)

        val track = singleTrack(reader.readTracksFromFile(bytes, "music/song.hca"))

        assertEquals(1_021L, track.length)
    }

    @Test
    fun `an invalid v1-x resolution range yields no tracks`() {
        // v1.x/v2.0 require exactly min=1, max=15; upstream rejects anything else.
        val bytes = hcaFile(blockCount = 4)
        bytes[0x18 + 0x06] = 2
        resignHca(bytes)

        assertNull(reader.readTracksFromFile(bytes, "song.hca"))
    }

    @Test
    fun `a band count above the HCA limit yields no tracks`() {
        val bytes = hcaFile(blockCount = 4)
        bytes[0x18 + 0x0A] = 129.toByte() // total_band_count
        resignHca(bytes)

        assertNull(reader.readTracksFromFile(bytes, "song.hca"))
    }

    @Test
    fun `the encoder delay shifts both model loop points`() {
        val bytes = hcaFile(
            blockCount = 100,
            encoderDelay = 128,
            encoderPadding = 32,
            loop = HcaFixtureLoop(startFrame = 2, endFrame = 99, startDelay = 64, endPadding = 256),
        )

        val track = singleTrack(reader.readTracksFromFile(bytes, "music/loop.hca"))

        // loop start = 2 * 1024 - 128 + 64 = 1_984 samples
        // loop end   = 99 * 1024 - 128 + (1024 - 256) = 102_016 samples
        // 1_984 + (102_016 - 1_984) * 2 + 10 * 44_100 = 643_048 samples
        // 643_048 * 1000 / 44_100 = 14_581 ms (truncating)
        assertEquals(14_581L, track.length)
    }
}

private fun singleTrack(tracks: List<RawTrack>?): RawTrack {
    assertNotNull(tracks)
    assertEquals(1, tracks.size, "expected one track, got ${tracks.size}")
    return tracks.single()
}

// --- Synthetic HCA fixtures (header layout: clhca.c `clHCA_DecodeHeader`) -----------------------

private const val HCA_VERSION_101 = 0x0101
private const val HCA_VERSION_300 = 0x0300
private const val HCA_CRC_SIZE = 2
private const val HCA_FRAME_SAMPLES = 1024
private const val HCA_CHUNK_FMT = 0x10
private const val HCA_CHUNK_COMP = 0x10
private const val HCA_CHUNK_DEC = 0x0C
private const val HCA_CHUNK_LOOP = 0x10
private const val HCA_CHUNK_CIPH = 0x06
private const val HCA_BASE_SIZE = 0x08

/** Loop block fields, in the frame units the header stores. */
private data class HcaFixtureLoop(
    val startFrame: Int,
    val endFrame: Int,
    val startDelay: Int = 0,
    val endPadding: Int = 0,
)

/**
 * Build one synthetic HCA file. Defaults describe a plain v1.1 stereo 44.1 kHz stream; the
 * arithmetic the tests assert is spelled out at each call site.
 *
 * The trailing CRC-16 is written over the assembled header, exactly as a real file stores it, so the
 * production checksum check sees the same bytes upstream's `crc16_checksum` would.
 */
private fun hcaFile(
    version: Int = HCA_VERSION_101,
    channels: Int = 2,
    sampleRate: Int = 44_100,
    blockCount: Int = 44,
    blockSize: Int = 0x200,
    encoderDelay: Int = 0,
    encoderPadding: Int = 0,
    loop: HcaFixtureLoop? = null,
    decChunk: Boolean = false,
    cipherType: Int? = null,
    maskSignature: Boolean = false,
    corruptCrc: Boolean = false,
    payload: Boolean = true,
): ByteArray {
    val base = ByteArray(HCA_BASE_SIZE)
    writeFixedAscii(base, 0x00, "HCA\u0000", 4)
    writeShortBe(base, 0x04, version)

    val fmt = ByteArray(HCA_CHUNK_FMT)
    writeFixedAscii(fmt, 0x00, "fmt\u0000", 4)
    fmt[0x04] = channels.toByte()
    writeU24Be(fmt, 0x05, sampleRate)
    writeIntBe(fmt, 0x08, blockCount)
    writeShortBe(fmt, 0x0C, encoderDelay)
    writeShortBe(fmt, 0x0E, encoderPadding)

    val compression = if (decChunk) decChunk(blockSize) else compChunk(blockSize)
    val chunks = mutableListOf(cat(base, fmt, compression))
    if (loop != null) {
        val loopChunk = ByteArray(HCA_CHUNK_LOOP)
        writeFixedAscii(loopChunk, 0x00, "loop", 4)
        writeIntBe(loopChunk, 0x04, loop.startFrame)
        writeIntBe(loopChunk, 0x08, loop.endFrame)
        writeShortBe(loopChunk, 0x0C, loop.startDelay)
        writeShortBe(loopChunk, 0x0E, loop.endPadding)
        chunks += loopChunk
    }
    if (cipherType != null) {
        val ciph = ByteArray(HCA_CHUNK_CIPH)
        writeFixedAscii(ciph, 0x00, "ciph", 4)
        writeShortBe(ciph, 0x04, cipherType)
        chunks += ciph
    }

    val headerBody = cat(*chunks.toTypedArray())
    // `header_size` covers every chunk plus the trailing CRC.
    writeShortBe(headerBody, 0x06, headerBody.size + HCA_CRC_SIZE)
    if (maskSignature) {
        for (i in 0 until 4) headerBody[i] = (headerBody[i].toInt() or 0x80).toByte()
    }
    val checksum = shortBe(hcaCrc16(headerBody, 0, headerBody.size))
    if (corruptCrc) checksum[0] = (checksum[0].toInt() xor 0xFF).toByte()
    val header = cat(headerBody, checksum)
    val audio = if (payload) ByteArray(blockCount * blockSize) else ByteArray(0)
    return cat(header, audio)
}

/** v2.0+ "comp" chunk: frame size, resolutions, track/channel config, 128-band layout. */
private fun compChunk(blockSize: Int): ByteArray = ByteArray(HCA_CHUNK_COMP).also { comp ->
    writeFixedAscii(comp, 0x00, "comp", 4)
    writeShortBe(comp, 0x04, blockSize)
    comp[0x06] = 1.toByte() // min_resolution
    comp[0x07] = 15.toByte() // max_resolution
    comp[0x08] = 1.toByte() // track_count
    comp[0x09] = 0.toByte() // channel_config
    comp[0x0A] = 128.toByte() // total_band_count
    comp[0x0B] = 128.toByte() // base_band_count
    comp[0x0C] = 0.toByte() // stereo_band_count
    comp[0x0D] = 0.toByte() // bands_per_hfr_group
}

/** v1.x "dec" chunk: band counts are stored one lower and `stereo_type == 0` folds stereo bands. */
private fun decChunk(blockSize: Int): ByteArray = ByteArray(HCA_CHUNK_DEC).also { dec ->
    writeFixedAscii(dec, 0x00, "dec\u0000", 4)
    writeShortBe(dec, 0x04, blockSize)
    dec[0x06] = 1.toByte() // min_resolution
    dec[0x07] = 15.toByte() // max_resolution
    dec[0x08] = 127.toByte() // total_band_count - 1 => 128
    dec[0x09] = 127.toByte() // base_band_count - 1 => 128
    dec[0x0A] = 0x10.toByte() // track_count (1) | channel_config (0) nibbles
    dec[0x0B] = 0.toByte() // stereo_type = 0
}

/** Recompute the trailing CRC-16 after a test mutates header bytes. */
private fun resignHca(bytes: ByteArray) {
    val headerSize = ((bytes[0x06].toInt() and 0xFF) shl 8) or (bytes[0x07].toInt() and 0xFF)
    writeShortBe(bytes, headerSize - HCA_CRC_SIZE, hcaCrc16(bytes, 0, headerSize - HCA_CRC_SIZE))
}

private fun writeU24Be(array: ByteArray, offset: Int, value: Int) {
    array[offset] = ((value shr 16) and 0xFF).toByte()
    array[offset + 1] = ((value shr 8) and 0xFF).toByte()
    array[offset + 2] = (value and 0xFF).toByte()
}
