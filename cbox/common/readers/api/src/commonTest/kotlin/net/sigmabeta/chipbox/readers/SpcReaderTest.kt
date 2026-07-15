package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SpcReaderTest {

    private val reader = SpcReader(BluntHatchet())

    @Test
    fun `parses standard ID666 tags`() {
        val tracks = reader.readTracksFromFile(
            spcFile(
                songTitle = "Memory's Skyscraper",
                gameTitle = "Persona",
                artistName = "Hidehito Aoki",
                lengthSeconds = "120",
                fadeMillis = "5000",
            ),
            "track.spc",
        )
        assertNotNull(tracks)
        assertEquals(1, tracks.size)
        val track = tracks[0]
        assertEquals("Memory's Skyscraper", track.title)
        assertEquals("Persona", track.game)
        assertEquals("Hidehito Aoki", track.artist)
        assertEquals(Platform.SNES, track.platform)
        // SPC stores length as a string-encoded seconds value (per format quirk); reader multiplies
        // by 1000 to get ms.
        assertEquals(120_000L, track.length)
        assertEquals(5_000L, track.fadeLengthMs)
    }

    @Test
    fun `missing length string yields LENGTH_UNKNOWN_MS`() {
        // Empty length field → toLongOrNull returns null → sentinel comes out the other side
        // rather than 0 (which would look like a real "instant" track).
        val tracks = reader.readTracksFromFile(
            spcFile(songTitle = "x", gameTitle = "x", artistName = "x", lengthSeconds = "", fadeMillis = ""),
            "x.spc",
        )
        assertNotNull(tracks)
        assertEquals(LENGTH_UNKNOWN_MS, tracks[0].length)
    }

    @Test
    fun `surfaces dumper, comment and dump date from the ID666 tag`() {
        val tracks = reader.readTracksFromFile(
            spcFile(
                songTitle = "x",
                gameTitle = "x",
                artistName = "x",
                lengthSeconds = "1",
                fadeMillis = "0",
                dumper = "Datschge",
                comments = "Ripped from cart",
                dumpDate = "08/15/2001",
            ),
            "meta.spc",
        )
        assertNotNull(tracks)
        assertEquals("Datschge", tracks[0].dumper)
        assertEquals("Ripped from cart", tracks[0].comment)
        assertEquals("08/15/2001", tracks[0].dumpDate)
    }

    @Test
    fun `absent optional ID666 fields come back null`() {
        val tracks = reader.readTracksFromFile(
            spcFile(songTitle = "x", gameTitle = "x", artistName = "x", lengthSeconds = "1", fadeMillis = "0"),
            "bare.spc",
        )
        assertNotNull(tracks)
        assertNull(tracks[0].dumper)
        assertNull(tracks[0].comment)
        assertNull(tracks[0].dumpDate)
    }

    @Test
    fun `ID666 text fields decode as Latin-1, not UTF-8`() {
        val bytes = spcFile(
            songTitle = "x",
            gameTitle = "x",
            artistName = "x",
            lengthSeconds = "1",
            fadeMillis = "0",
        )
        // Place a lone 0xED byte (Latin-1 'í') at the start of the 16-byte dumper field. As UTF-8
        // it's an invalid lead byte and would decode to the replacement char; as Latin-1 it's 'í'.
        bytes[110] = 0xED.toByte()

        val tracks = reader.readTracksFromFile(bytes, "latin1.spc")

        assertNotNull(tracks)
        assertEquals("í", tracks[0].dumper)
    }

    @Test
    fun `rejects a file with the wrong header magic`() {
        // Has SPC-shaped header layout but the magic string is wrong.
        val bytes = ByteArray(0x10_200)
        "Not an SPC file at all, no sirree".encodeToByteArray().copyInto(bytes, 0)
        assertNull(reader.readTracksFromFile(bytes, "notspc.spc"))
    }

    @Test
    fun `rejects a file where the 0x1A metadata flag byte is missing`() {
        // The 3-byte header-info field's last byte must be 0x1A for the reader to consider
        // ID666 tags present. Without it the reader bails before reading any tag.
        val bytes = spcFile(
            songTitle = "x",
            gameTitle = "x",
            artistName = "x",
            lengthSeconds = "1",
            fadeMillis = "0",
        )
        bytes[35] = 0 // overwrite the 0x1A flag
        assertNull(reader.readTracksFromFile(bytes, "no_meta.spc"))
    }

    @Test
    fun `accepts a header whose version digit is a NUL byte`() {
        // Some rippers write the 33-byte magic field as "SNES-SPC700 Sound File Data v0.3\0",
        // NUL where the final version digit belongs. Real SPC files, so the reader must accept them.
        val bytes = spcFile(
            songTitle = "Red Falcon's Revenge",
            gameTitle = "Contra III",
            artistName = "x",
            lengthSeconds = "120",
            fadeMillis = "5000",
        )
        bytes[0x20] = 0 // overwrite the '0' of "v0.30" with NUL

        val tracks = reader.readTracksFromFile(bytes, "contra.spc")
        assertNotNull(tracks)
        assertEquals("Red Falcon's Revenge", tracks[0].title)
        assertEquals("Contra III", tracks[0].game)
    }

    @Test
    fun `xid6 string fields override the truncated ID666 names`() {
        // ID666 caps each name at 32 bytes; xid6 carries the full string. The reader must prefer
        // the xid6 entries when present.
        val full = spcFile(
            songTitle = "Truncated title",
            gameTitle = "Truncated game",
            artistName = "Truncated artist",
            lengthSeconds = "1",
            fadeMillis = "0",
            xid6 = xid6Chunk(
                song = "The actual full title that wouldn't fit in 32 bytes",
                game = "The actual full game name",
                artist = "The actual full artist credit",
            ),
        )
        val tracks = reader.readTracksFromFile(full, "long.spc")
        assertNotNull(tracks)
        assertEquals("The actual full title that wouldn't fit in 32 bytes", tracks[0].title)
        assertEquals("The actual full game name", tracks[0].game)
        assertEquals("The actual full artist credit", tracks[0].artist)
    }

    /**
     * Build a minimal SPC file: 33-byte magic, the 0x1A flag, version/registers, the six fixed-
     * width tag fields the reader consumes, then zero padding out to [SpcReader] 's hard-coded
     * xid6 offset (`0x10200`). [xid6] is appended after — empty by default.
     */
    private fun spcFile(
        songTitle: String,
        gameTitle: String,
        artistName: String,
        lengthSeconds: String,
        fadeMillis: String,
        dumper: String = "",
        comments: String = "",
        dumpDate: String = "",
        xid6: ByteArray = ByteArray(0),
    ): ByteArray {
        val xid6Offset = 0x10_200
        val bytes = ByteArray(xid6Offset + xid6.size)
        val magic = "SNES-SPC700 Sound File Data v0.30"
        writeFixedAscii(bytes, 0, magic, 33)

        // 3-byte header info field at offset 33: any leading values, last byte 0x1A = has tags.
        bytes[33] = 0
        bytes[34] = 0
        bytes[35] = 0x1A

        bytes[36] = 30 // minor version (ignored by the reader)
        // 9 bytes SPC registers (ignored, leave as zero).

        // Sequential fixed-width tag fields.
        writeFixedAscii(bytes, 46, songTitle, 32)
        writeFixedAscii(bytes, 78, gameTitle, 32)
        writeFixedAscii(bytes, 110, dumper, 16) // dumper name
        writeFixedAscii(bytes, 126, comments, 32) // comments
        writeFixedAscii(bytes, 158, dumpDate, 11) // dump date
        writeFixedAscii(bytes, 169, lengthSeconds, 3)
        writeFixedAscii(bytes, 172, fadeMillis, 5)
        writeFixedAscii(bytes, 177, artistName, 32)

        if (xid6.isNotEmpty()) xid6.copyInto(bytes, xid6Offset)
        return bytes
    }

    /**
     * Build an xid6 chunk holding string subchunks for song / game / artist. Layout:
     *   "xid6" + chunkSize(LE32) + repeated (id|type|dataLen LE16 | payload | pad-to-4) subchunks.
     */
    private fun xid6Chunk(song: String, game: String, artist: String): ByteArray {
        val subchunks = cat(
            stringSubchunk(id = 0x01, text = song),
            stringSubchunk(id = 0x02, text = game),
            stringSubchunk(id = 0x03, text = artist),
        )
        return cat("xid6".encodeToByteArray(), intLe(subchunks.size), subchunks)
    }

    private fun stringSubchunk(id: Int, text: String): ByteArray {
        // xid6 strings include a trailing 0 byte and the reader trims at the first null.
        val payload = text.encodeToByteArray() + byteArrayOf(0)
        val padded = payload.size + (4 - payload.size % 4) % 4
        val out = ByteArray(4 + padded)
        out[0] = id.toByte()
        out[1] = 1 // type = STRING
        out[2] = payload.size.toByte() // dataLen low
        out[3] = (payload.size shr 8).toByte() // dataLen high
        payload.copyInto(out, 4)
        // Padding bytes already zero.
        return out
    }
}
