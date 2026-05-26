package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.FADE_LENGTH_MS
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * commonTest counterpart to the JVM-side `VgmReaderTest`. Covers the same VGM header parsing the
 * JVM file does, minus the gzip (.vgz) round-trip — that one needs `java.util.zip.GZIPOutputStream`
 * to *build* the fixture, which doesn't exist in commonMain. The decompression path itself stays
 * exercised by the JVM test; this file adds JS-side coverage of the pure header math and the GD3
 * "system" → Platform mapping that the JVM test doesn't.
 */
class VgmReaderCommonTest {

    private val reader = VgmReader(BluntHatchet())

    @Test
    fun `parses a plain VGM header and computes length from total samples`() {
        val tracks = reader.readTracksFromFile(minimalVgm(totalSamples = 44_100), "test.vgm")
        assertNotNull(tracks)
        assertEquals(1, tracks.size)
        // 44_100 samples / 44_100 Hz * 1000 = 1000 ms.
        assertEquals(1000L, tracks[0].length)
        assertEquals("Unknown", tracks[0].title)
        assertEquals(0L, tracks[0].fadeLengthMs)
    }

    @Test
    fun `a loop adds two extra loop iterations to the length and enables fade`() {
        // VGM "endless" tracks loop forever; the reader extends total by 2x loop so playback gets
        // 3 iterations + fade. Pins the documented math down so a future rework doesn't change it
        // by accident.
        val tracks = reader.readTracksFromFile(
            minimalVgm(totalSamples = 44_100, loopSamples = 22_050),
            "loop.vgm",
        )
        assertNotNull(tracks)
        assertEquals(2000L, tracks[0].length)
        assertEquals(FADE_LENGTH_MS, tracks[0].fadeLengthMs)
    }

    @Test
    fun `zero total samples reports LENGTH_UNKNOWN_MS rather than zero`() {
        val tracks = reader.readTracksFromFile(minimalVgm(totalSamples = 0), "empty.vgm")
        assertNotNull(tracks)
        assertEquals(LENGTH_UNKNOWN_MS, tracks[0].length)
    }

    @Test
    fun `rejects a file with bad magic`() {
        val notVgm = ByteArray(0x40) // all zeros — magic mismatch
        assertNull(reader.readTracksFromFile(notVgm, "bogus.vgm"))
    }

    @Test
    fun `rejects a file too small to hold the 0x40-byte header`() {
        val truncated = ByteArray(0x10)
        "Vgm ".encodeToByteArray().copyInto(truncated, 0)
        assertNull(reader.readTracksFromFile(truncated, "tiny.vgm"))
    }

    @Test
    fun `Genesis variants in the GD3 system field all map to GENESIS`() {
        // The mapping table folds Sega CD, 32X, and Mega Drive aliases onto a single platform —
        // they're all addons or regional renames of the same hardware.
        listOf("Sega Mega Drive", "Sega Genesis", "Sega 32X", "Sega CD").forEach { systemName ->
            val tracks = reader.readTracksFromFile(vgmWithSystem(systemName), "x.vgm")
            assertEquals(Platform.GENESIS, tracks?.firstOrNull()?.platform, "system='$systemName'")
        }
    }

    @Test
    fun `arcade boards take priority over the vendor's home console`() {
        // "Sega System 32" must read as ARCADE, not Genesis. Order-sensitive mapping; pin it.
        val tracks = reader.readTracksFromFile(vgmWithSystem("Sega System 32"), "arcade.vgm")
        assertEquals(Platform.ARCADE, tracks?.firstOrNull()?.platform)
    }

    @Test
    fun `unrecognised system name falls back to OTHER`() {
        val tracks = reader.readTracksFromFile(vgmWithSystem("Brand New Console XL"), "unknown.vgm")
        assertEquals(Platform.OTHER, tracks?.firstOrNull()?.platform)
    }

    @Test
    fun `GD3 title and artist surface on the RawTrack`() {
        val tracks = reader.readTracksFromFile(
            vgmWithGd3(
                title = "Stage 1",
                game = "Cool Game",
                system = "Sega Mega Drive",
                author = "Yuzo Koshiro",
            ),
            "x.vgm",
        )
        assertNotNull(tracks)
        assertEquals("Stage 1", tracks[0].title)
        assertEquals("Cool Game", tracks[0].game)
        assertEquals("Yuzo Koshiro", tracks[0].artist)
        assertEquals(Platform.GENESIS, tracks[0].platform)
    }

    /**
     * Minimal VGM: 0x40-byte header with the magic and the two sample-count fields at their
     * documented offsets. GD3 offset stays 0 (= no tag block), so the reader skips GD3 parsing.
     */
    private fun minimalVgm(totalSamples: Int, loopSamples: Int = 0): ByteArray {
        val vgm = ByteArray(0x40)
        "Vgm ".encodeToByteArray().copyInto(vgm, 0)
        writeIntLe(vgm, 0x18, totalSamples)
        writeIntLe(vgm, 0x20, loopSamples)
        return vgm
    }

    private fun vgmWithSystem(systemName: String): ByteArray =
        vgmWithGd3(title = "x", game = "x", system = systemName, author = "x")

    /**
     * VGM with a populated GD3 block: header sets the GD3 relative offset to point past the
     * header; the block carries 11 null-terminated UTF-16LE strings in the canonical order
     * (title-en, title-jp, game-en, game-jp, system-en, system-jp, author-en, author-jp,
     * date, converter, notes). Only the four English entries the reader picks up are non-empty.
     */
    private fun vgmWithGd3(title: String, game: String, system: String, author: String): ByteArray {
        val empty = "".encodeUtf16Le() + byteArrayOf(0, 0)
        val payload = cat(
            title.encodeUtf16Le() + byteArrayOf(0, 0),
            empty, // title-jp
            game.encodeUtf16Le() + byteArrayOf(0, 0),
            empty, // game-jp
            system.encodeUtf16Le() + byteArrayOf(0, 0),
            empty, // system-jp
            author.encodeUtf16Le() + byteArrayOf(0, 0),
            empty, // author-jp
            empty, // date
            empty, // converter
            empty, // notes
        )
        val header = ByteArray(0x40)
        "Vgm ".encodeToByteArray().copyInto(header, 0)
        // GD3 offset is *relative* to its own field at 0x14, so point past the header.
        writeIntLe(header, 0x14, 0x40 - 0x14)
        writeIntLe(header, 0x18, 44_100) // 1 second
        val gd3Header = ByteArray(12)
        "Gd3 ".encodeToByteArray().copyInto(gd3Header, 0)
        writeIntLe(gd3Header, 4, 0x100) // version — ignored
        writeIntLe(gd3Header, 8, payload.size)
        return cat(header, gd3Header, payload)
    }

    /**
     * UTF-16LE encoding by hand — common stdlib has no charset converter, but each code unit of a
     * String in the BMP is already a UTF-16 char value, so the bytes drop straight out.
     */
    private fun String.encodeUtf16Le(): ByteArray {
        val out = ByteArray(length * 2)
        for ((i, ch) in this.withIndex()) {
            val code = ch.code
            out[i * 2] = (code and 0xFF).toByte()
            out[i * 2 + 1] = ((code shr 8) and 0xFF).toByte()
        }
        return out
    }
}
