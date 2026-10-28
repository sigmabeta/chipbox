package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class VagReaderTest {
    private val reader = VagReader(BluntHatchet())

    @Test
    fun `a standard vagp stream reports its sample count`() {
        val bytes = vagFile(dataFrames = 100, name = "TEST")

        val track = singleTrack(reader.readTracksFromFile(bytes, "voice.vag"))

        // 100 PS-ADPCM frames * 28 samples = 2_800 samples; 2_800 / 44_100 s -> 63 ms (truncated).
        assertEquals(63L, track.length)
        assertEquals("TEST", track.title)
        assertEquals(Platform.PSX, track.platform)
        assertEquals(0L, track.fadeLengthMs)
    }

    @Test
    fun `a looping vagp stream plays two loops plus the fade`() {
        val flags = ByteArray(100)
        flags[10] = 0x06 // loop start before this frame
        flags[90] = 0x03 // loop end after this frame
        flags[99] = 0x01 // end marker
        val bytes = vagFile(dataFrames = 100, flags = flags)

        val track = singleTrack(reader.readTracksFromFile(bytes, "loop.vag"))

        // loop_start = 10 * 28 = 280; loop_end = 90 * 28 + 28 = 2_548 samples.
        // 280 + (2_548 - 280) * 2 + 10 * 44_100 = 445_816 samples -> 445_816 * 1000 / 44_100 = 10_109 ms.
        assertEquals(10_109L, track.length)
        // No name in this header, so the scanner's filename fallback supplies the title.
        assertEquals(TAG_UNKNOWN, track.title)
    }

    @Test
    fun `the sample rate scales the reported length`() {
        val bytes = vagFile(dataFrames = 100, sampleRate = 22_050)

        val track = singleTrack(reader.readTracksFromFile(bytes, "half-rate.vag"))

        // 2_800 samples / 22_050 Hz -> 126 ms (truncated).
        assertEquals(126L, track.length)
    }

    @Test
    fun `the vag extension resolves to the VAG reader at version 1`() {
        val readers = Readers(BluntHatchet())

        assertSame(readers.vag, readers.forExtension("vag"))
        assertEquals(1, readers.readerVersionFor("vag"))
    }

    @Test
    fun `a file with the wrong magic is a parse miss`() {
        val bytes = vagFile(dataFrames = 2, magic = "XXXX")

        assertNull(reader.readTracksFromFile(bytes, "broken.vag"))
    }

    @Test
    fun `a truncated header is a parse miss without throwing`() {
        val bytes = vagFile(dataFrames = 2).copyOf(0x20)

        assertNull(reader.readTracksFromFile(bytes, "short.vag"))
    }

    @Test
    fun `a header claiming more data than the file holds is a parse miss`() {
        val bytes = vagFile(dataFrames = 2)
        writeIntBe(bytes, 0x0C, 0x1000)

        assertNull(reader.readTracksFromFile(bytes, "overrun.vag"))
    }

    @Test
    fun `a vagi stream takes its data start and interleave from the 0x800 header`() {
        val track = singleTrack(reader.readTracksFromFile(vagiFile(), "interleaved.vag"))

        // 4 frames * 28 = 112 samples -> 112 * 1000 / 44_100 = 2 ms.
        assertEquals(2L, track.length)
        assertEquals("TEST", track.title)
    }

    @Test
    fun `a second vagp header marks a stereo stream and scans both channels`() {
        val track = singleTrack(reader.readTracksFromFile(interleavedVagpFile(), "stereo.vag"))

        // Loop start at frame 0, loop end at frame 4: 140 samples -> (140 * 2 + 441_000) -> 10_006 ms.
        // A mono fallback would only see the first 0x40 bytes and report the unlooped 2 ms.
        assertEquals(10_006L, track.length)
    }

    @Test
    fun `a data-first vag with a trailing vagp footer parses the footer header`() {
        val track = singleTrack(reader.readTracksFromFile(footerVagFile(), "footer.vag"))

        // 4 frames * 28 = 112 samples -> 2 ms.
        assertEquals(2L, track.length)
        assertEquals("SIM", track.title)
    }

    @Test
    fun `an aaap channel bundle parses its first vagp header`() {
        val track = singleTrack(reader.readTracksFromFile(aaapFile(), "bundle.vag"))

        // 2 frames * 28 = 56 samples -> 1 ms; AAAp exposes no stream name.
        assertEquals(1L, track.length)
        assertEquals(TAG_UNKNOWN, track.title)
    }

    @Test
    fun `an evolution games vag with its magic erased parses as a header`() {
        val track = singleTrack(reader.readTracksFromFile(evolutionVagFile(), "evolution.vag"))

        // Declared 0x40-byte stream minus vgmstream's 0x20-byte tail hack = 0x20 bytes = 56 samples.
        assertEquals(1L, track.length)
        assertEquals("EVO", track.title)
    }

    @Test
    fun `a vag1 header reads its optional channel count`() {
        val track = singleTrack(reader.readTracksFromFile(vag1File(), "mg3.vag"))

        // Channel size 0x20 is per channel: 56 samples -> 1 ms.
        assertEquals(1L, track.length)
        assertEquals("TEST", track.title)
    }

    @Test
    fun `a pgav header reads little-endian channel size and rate`() {
        val track = singleTrack(reader.readTracksFromFile(pgavFile(), "jak.vag"))

        // 0x20 bytes of LE-header data = 56 samples -> 1 ms.
        assertEquals(1L, track.length)
        assertEquals("TEST", track.title)
    }

    @Test
    fun `a vag2 header is stereo with an 0x800 interleave`() {
        val track = singleTrack(reader.readTracksFromFile(vag2File(), "mg3-stereo.vag"))

        // Channel size 0x20 is per channel: 56 samples -> 1 ms.
        assertEquals(1L, track.length)
        assertEquals("TEST", track.title)
    }
}

/** `VAGi`: 0x800-byte header, stereo, little-endian interleave at 0x08. */
private fun vagiFile(frames: Int = 4, interleave: Int = 0x40): ByteArray {
    val channelSize = frames * PSX_FRAME_SIZE
    val bytes = ByteArray(VAGI_HEADER + interleave * 2)
    writeFixedAscii(bytes, 0x00, "VAGi", 4)
    writeIntLe(bytes, 0x08, interleave)
    writeIntBe(bytes, 0x0C, channelSize)
    writeIntBe(bytes, 0x10, 44_100)
    writeFixedAscii(bytes, 0x20, "TEST", 0x10)
    writeFrames(bytes, VAGI_HEADER, frames)
    return bytes
}

/** `VAGp` with a second header at 0x1000, so vgmstream treats it as stereo interleaved. */
private fun interleavedVagpFile(): ByteArray {
    val bytes = ByteArray(SECOND_HEADER_1000 + 4)
    writeFixedAscii(bytes, 0x00, "VAGp", 4)
    writeIntBe(bytes, 0x04, 0x20)
    writeIntBe(bytes, 0x0C, 0x40)
    writeIntBe(bytes, 0x10, 44_100)
    writeFixedAscii(bytes, SECOND_HEADER_1000, "VAGp", 4)
    writeFrames(bytes, VAG_HEADER_SIZE, frames = 8)
    // Loop start at frame 0; loop end at frame 4, which a mono 0x40-byte window never reaches.
    bytes[VAG_HEADER_SIZE + 1] = 0x06
    bytes[VAG_HEADER_SIZE + 4 * PSX_FRAME_SIZE + 1] = 0x03
    return bytes
}

/** Data first, a little-endian `VAGp` header in the last 0x40 bytes. */
private fun footerVagFile(frames: Int = 4): ByteArray {
    val streamSize = frames * PSX_FRAME_SIZE
    val bytes = ByteArray(streamSize + FOOTER_HEADER_SIZE)
    writeFrames(bytes, 0, frames)
    writeFixedAscii(bytes, streamSize, "VAGp", 4)
    writeIntLe(bytes, streamSize + 0x04, 2) // footer version
    writeIntLe(bytes, streamSize + 0x0C, streamSize)
    writeIntLe(bytes, streamSize + 0x10, 44_100)
    writeFixedAscii(bytes, streamSize + 0x20, "SIM", 0x10)
    return bytes
}

/** `AAAp`: one 0x30-byte `VAGp` header per channel, then the data. */
private fun aaapFile(channels: Int = 1, frames: Int = 2): ByteArray {
    val channelSize = frames * PSX_FRAME_SIZE
    val dataOffset = 0x08 + channels * VAG_HEADER_SIZE
    val bytes = ByteArray(dataOffset + channelSize * channels)
    writeFixedAscii(bytes, 0x00, "AAAp", 4)
    writeShortLe(bytes, 0x04, 0x10) // interleave (playback only)
    writeShortLe(bytes, 0x06, channels)
    for (channel in 0 until channels) {
        val header = 0x08 + channel * VAG_HEADER_SIZE
        writeFixedAscii(bytes, header, "VAGp", 4)
        writeIntBe(bytes, header + 0x04, 0x20)
        writeIntBe(bytes, header + 0x0C, channelSize)
        writeIntBe(bytes, header + 0x10, 44_100)
    }
    writeFrames(bytes, dataOffset, frames * channels)
    return bytes
}

/** Evolution Games: `VAGp` erased to three spaces + NUL, all header fields little endian. */
private fun evolutionVagFile(declaredSize: Int = 0x40): ByteArray {
    val bytes = ByteArray(declaredSize + VAG_HEADER_SIZE)
    writeFixedAscii(bytes, 0x00, "   \u0000", 4)
    writeIntLe(bytes, 0x04, 0)
    writeFixedAscii(bytes, 0x08, "   \u0000", 4)
    writeIntLe(bytes, 0x0C, declaredSize)
    writeIntLe(bytes, 0x10, 44_100)
    writeFixedAscii(bytes, 0x20, "EVO", 0x10)
    writeFrames(bytes, VAG_HEADER_SIZE, frames = declaredSize / PSX_FRAME_SIZE)
    return bytes
}

/** `VAG1`: 0x40-byte header with the channel count at 0x1E. */
private fun vag1File(channels: Int = 2, frames: Int = 2): ByteArray {
    val channelSize = frames * PSX_FRAME_SIZE
    val bytes = ByteArray(0x40 + channelSize * channels)
    writeFixedAscii(bytes, 0x00, "VAG1", 4)
    writeIntBe(bytes, 0x04, 0)
    writeIntBe(bytes, 0x0C, channelSize)
    writeIntBe(bytes, 0x10, 44_100)
    bytes[0x1E] = channels.toByte()
    writeFixedAscii(bytes, 0x20, "TEST", 0x10)
    writeFrames(bytes, 0x40, frames * channels)
    return bytes
}

/** `VAG2`: 0x40-byte header, stereo at 0x800 interleave. */
private fun vag2File(frames: Int = 2): ByteArray {
    val channelSize = frames * PSX_FRAME_SIZE
    val bytes = ByteArray(0x40 + channelSize)
    writeFixedAscii(bytes, 0x00, "VAG2", 4)
    writeIntBe(bytes, 0x0C, channelSize)
    writeIntBe(bytes, 0x10, 44_100)
    writeFixedAscii(bytes, 0x20, "TEST", 0x10)
    writeFrames(bytes, 0x40, frames)
    return bytes
}

/** `pGAV`: little-endian channel size at 0x0C and rate at 0x10. */
private fun pgavFile(frames: Int = 2): ByteArray {
    val channelSize = frames * PSX_FRAME_SIZE
    val bytes = ByteArray(VAG_HEADER_SIZE + channelSize)
    writeFixedAscii(bytes, 0x00, "pGAV", 4)
    writeIntLe(bytes, 0x0C, channelSize)
    writeIntLe(bytes, 0x10, 44_100)
    writeFixedAscii(bytes, 0x20, "TEST", 0x10)
    writeFrames(bytes, VAG_HEADER_SIZE, frames)
    return bytes
}

/** Writes [frames] silent PS-ADPCM frames at [offset], ending with a 0x01 end flag. */
private fun writeFrames(bytes: ByteArray, offset: Int, frames: Int) {
    for (frame in 0 until frames) {
        bytes[offset + frame * PSX_FRAME_SIZE] = 0x0C
        bytes[offset + frame * PSX_FRAME_SIZE + 1] = if (frame == frames - 1) 0x01 else 0x00
    }
}

private const val VAGI_HEADER = 0x800
private const val SECOND_HEADER_1000 = 0x1000
private const val FOOTER_HEADER_SIZE = 0x40

/** A standard `.vag`-reachable `VAGp` header followed by [dataFrames] 16-byte PS-ADPCM frames. */
internal fun vagFile(
    dataFrames: Int,
    magic: String = "VAGp",
    version: Int = 0x00000020,
    sampleRate: Int = 44_100,
    name: String = "",
    flags: ByteArray = ByteArray(dataFrames).also { it[dataFrames - 1] = 0x01 },
): ByteArray {
    require(flags.size == dataFrames) { "one flag byte per frame" }
    val dataSize = dataFrames * PSX_FRAME_SIZE
    val bytes = ByteArray(VAG_HEADER_SIZE + dataSize)
    writeFixedAscii(bytes, 0x00, magic, 4)
    writeIntBe(bytes, 0x04, version)
    writeIntBe(bytes, 0x0C, dataSize)
    writeIntBe(bytes, 0x10, sampleRate)
    writeFixedAscii(bytes, 0x20, name, 0x10)
    for (frame in 0 until dataFrames) {
        // Predictor 0 / shift 12 is the usual silent-ish first frame byte; only byte 1 is a flag.
        bytes[VAG_HEADER_SIZE + frame * PSX_FRAME_SIZE] = 0x0C
        bytes[VAG_HEADER_SIZE + frame * PSX_FRAME_SIZE + 1] = flags[frame]
    }
    return bytes
}

private fun singleTrack(tracks: List<RawTrack>?): RawTrack {
    val list = assertNotNull(tracks)
    assertEquals(1, list.size)
    return list.single()
}

internal const val PSX_FRAME_SIZE = 0x10
internal const val VAG_HEADER_SIZE = 0x30
