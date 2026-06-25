package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NsfeReaderTest {

    private val reader = NsfeReader(BluntHatchet())

    @Test
    fun `parses track names and lengths from tlbl and time chunks`() {
        // INFO trackCount=2, tlbl carries two titles separated by 0x00, time gives 1 s and 2 s.
        val nsfe = nsfeFile(
            chunks = listOf(
                infoChunk(trackCount = 2),
                chunk("tlbl", nullSep("Title 1", "Title 2")),
                chunk("auth", nullSep("Game Name", "Artist Name", "Copyright")),
                chunk("time", intLe(1_000) + intLe(2_000)),
                chunk("NEND", ByteArray(0)),
            ),
        )
        val tracks = reader.readTracksFromFile(nsfe, "x.nsfe")
        assertNotNull(tracks)
        assertEquals(2, tracks.size)
        assertEquals("Title 1", tracks[0].title)
        assertEquals("Title 2", tracks[1].title)
        assertEquals(1_000L, tracks[0].length)
        assertEquals(2_000L, tracks[1].length)
        assertEquals("Game Name", tracks[0].game)
        // taut is absent → all tracks fall back to the game-level artist from auth.
        assertEquals("Artist Name", tracks[0].artist)
        assertEquals("Artist Name", tracks[1].artist)
        assertEquals(Platform.NES, tracks[0].platform)
    }

    @Test
    fun `tlbl row count is used as a fallback when INFO is absent`() {
        // Real .nsfe files sometimes omit INFO; the reader falls back to tlbl's length. Pin it.
        val nsfe = nsfeFile(
            chunks = listOf(
                chunk("tlbl", nullSep("Only Track", "Second Track")),
                chunk("NEND", ByteArray(0)),
            ),
        )
        val tracks = reader.readTracksFromFile(nsfe, "no_info.nsfe")
        assertNotNull(tracks)
        assertEquals(2, tracks.size)
    }

    @Test
    fun `plst remaps subtune indexes to playlist position`() {
        // Three subtunes physically present; plst plays them out of order [2, 0]. The resulting
        // RawTrack list mirrors the playlist length, with trackNumber set to the playlist position
        // (not the subtune index) so GME's start_track_(N) lines up.
        val nsfe = nsfeFile(
            chunks = listOf(
                infoChunk(trackCount = 3),
                chunk("tlbl", nullSep("Track A", "Track B", "Track C")),
                chunk("time", intLe(1_000) + intLe(2_000) + intLe(3_000)),
                chunk("plst", byteArrayOf(2, 0)),
                chunk("NEND", ByteArray(0)),
            ),
        )
        val tracks = reader.readTracksFromFile(nsfe, "plst.nsfe")
        assertNotNull(tracks)
        assertEquals(2, tracks.size)
        assertEquals("Track C", tracks[0].title)
        assertEquals(0, tracks[0].trackNumber)
        assertEquals("Track A", tracks[1].title)
        assertEquals(1, tracks[1].trackNumber)
    }

    @Test
    fun `per-track fade chunk produces non-zero fadeLengthMs`() {
        // Fade chunk has one LE32 per subtune. Single-track NSFE → single fade value.
        val nsfe = nsfeFile(
            chunks = listOf(
                infoChunk(trackCount = 1),
                chunk("tlbl", "One".encodeToByteArray()),
                chunk("time", intLe(5_000)),
                chunk("fade", intLe(750)),
                chunk("NEND", ByteArray(0)),
            ),
        )
        val tracks = reader.readTracksFromFile(nsfe, "fade.nsfe")
        assertNotNull(tracks)
        assertEquals(750L, tracks[0].fadeLengthMs)
    }

    @Test
    fun `taut chunk overrides the auth-derived artist per-track`() {
        val nsfe = nsfeFile(
            chunks = listOf(
                infoChunk(trackCount = 2),
                chunk("tlbl", nullSep("One", "Two")),
                chunk("auth", nullSep("Game", "Game-level Artist")),
                chunk("taut", nullSep("Per-Track Artist 1", "Per-Track Artist 2")),
                chunk("NEND", ByteArray(0)),
            ),
        )
        val tracks = reader.readTracksFromFile(nsfe, "taut.nsfe")
        assertNotNull(tracks)
        assertEquals("Per-Track Artist 1", tracks[0].artist)
        assertEquals("Per-Track Artist 2", tracks[1].artist)
    }

    @Test
    fun `auth chunk copyright and ripper surface on every track`() {
        // 'auth' is game, artist, copyright, ripper. The last two are optional descriptive metadata
        // (copyright is release-level; ripper maps to the track's dumper).
        val nsfe = nsfeFile(
            chunks = listOf(
                infoChunk(trackCount = 1),
                chunk("tlbl", "One".encodeToByteArray()),
                chunk("auth", nullSep("Game", "Artist", "(C)1990 Capcom", "Mr. Ripper")),
                chunk("NEND", ByteArray(0)),
            ),
        )
        val tracks = reader.readTracksFromFile(nsfe, "auth.nsfe")
        assertNotNull(tracks)
        assertEquals("(C)1990 Capcom", tracks[0].copyright)
        assertEquals("Mr. Ripper", tracks[0].dumper)
    }

    @Test
    fun `auth chunk without copyright or ripper leaves them null`() {
        val nsfe = nsfeFile(
            chunks = listOf(
                infoChunk(trackCount = 1),
                chunk("tlbl", "One".encodeToByteArray()),
                chunk("auth", nullSep("Game", "Artist")),
                chunk("NEND", ByteArray(0)),
            ),
        )
        val tracks = reader.readTracksFromFile(nsfe, "noauth.nsfe")
        assertNotNull(tracks)
        assertNull(tracks[0].copyright)
        assertNull(tracks[0].dumper)
    }

    @Test
    fun `rejects a file with the wrong magic`() {
        assertNull(reader.readTracksFromFile("NOPE".encodeToByteArray() + ByteArray(64), "x.nsfe"))
    }

    @Test
    fun `returns null when neither INFO nor tlbl is present`() {
        // Reader has nothing to base a track count on; documented `return null`.
        val nsfe = nsfeFile(chunks = listOf(chunk("NEND", ByteArray(0))))
        assertNull(reader.readTracksFromFile(nsfe, "empty.nsfe"))
    }

    /** "NSFE" magic + each chunk's `length(LE32) | name(4 ASCII) | content` concatenated. */
    private fun nsfeFile(chunks: List<ByteArray>): ByteArray =
        cat("NSFE".encodeToByteArray(), *chunks.toTypedArray())

    private fun chunk(name: String, content: ByteArray): ByteArray {
        require(name.length == 4) { "NSFE chunk names are 4 ASCII bytes; got '$name'" }
        return cat(intLe(content.size), name.encodeToByteArray(), content)
    }

    /** INFO chunk with the track count parked at byte offset 8 (per spec). Other fields zeroed. */
    private fun infoChunk(trackCount: Int): ByteArray {
        val content = ByteArray(9)
        content[8] = trackCount.toByte()
        return chunk("INFO", content)
    }

    /** Pack [values] into a single null-byte-separated payload — the on-disk layout NSFE uses
     *  for string-list chunks (tlbl, auth, taut). */
    private fun nullSep(vararg values: String): ByteArray {
        // Don't use string-join + encode: the U+0000 char would round-trip fine in UTF-8 here,
        // but it's clearer to write the bytes directly and avoid surprising any encoder.
        val parts = values.map { it.encodeToByteArray() }
        val total = parts.sumOf { it.size } + (parts.size - 1).coerceAtLeast(0)
        val out = ByteArray(total)
        var pos = 0
        for ((i, p) in parts.withIndex()) {
            if (i > 0) {
                out[pos] = 0
                pos++
            }
            p.copyInto(out, pos)
            pos += p.size
        }
        return out
    }
}
