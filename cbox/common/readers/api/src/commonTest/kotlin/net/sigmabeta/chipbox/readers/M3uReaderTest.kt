package net.sigmabeta.chipbox.readers

import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class M3uReaderTest {

    private val reader = M3uReader(BluntHatchet())

    @Test
    fun `PSF subtune index is converted from 1-based to 0-based`() {
        val entries = parse("game.psf::PSF,1,Opening,1:00")
        assertEquals(1, entries.size)
        // PSF/NSF/NSFE m3u files index from 1 (matching the format's "song number"), but our
        // reader uses 0-based trackNumber everywhere — verify the off-by-one shift happens.
        assertEquals(0, entries[0].trackNumber)
        assertEquals("game.psf", entries[0].filename)
        assertEquals("Opening", entries[0].title)
    }

    @Test
    fun `GBS subtune index is kept 0-based`() {
        // GBS m3u files in the wild are 0-based, matching the GBS internal subtune index —
        // subtracting 1 here would push the first track to -1 and drop it.
        val entries = parse("game.gbs::GBS,0,Stage 1,1:30")
        assertEquals(1, entries.size)
        assertEquals(0, entries[0].trackNumber)
        assertEquals("Stage 1", entries[0].title)
    }

    @Test
    fun `PSF index zero drops the entry instead of producing a negative track number`() {
        // index 0 in PSF format would mean trackNumber = -1 after the shift — invalid, must be
        // dropped rather than handed downstream.
        assertTrue(parse("game.psf::PSF,0,Title,1:00").isEmpty())
    }

    @Test
    fun `comment and empty lines are skipped`() {
        val text = """
            # this is a header comment

            game.gbs::GBS,0,Stage 1,1:00
            # another comment
            game.gbs::GBS,1,Stage 2,1:30
        """.trimIndent()
        val entries = parse(text)
        assertEquals(2, entries.size)
        assertEquals("Stage 1", entries[0].title)
        assertEquals("Stage 2", entries[1].title)
    }

    @Test
    fun `lines without double-colon are silently skipped`() {
        // The filter requires `::` — anything else is metadata or junk we shouldn't try to parse.
        val text = """
            some random text
            valid.gbs::GBS,0,OK,1:00
            another junk line
        """.trimIndent()
        val entries = parse(text)
        assertEquals(1, entries.size)
        assertEquals("OK", entries[0].title)
    }

    @Test
    fun `simple Title - Artist - Game tag splits into three fields`() {
        val entries = parse("game.nsf::NSF,1,My Title - My Artist - My Game,2:00")
        assertEquals(1, entries.size)
        val entry = entries[0]
        assertEquals("My Title", entry.title)
        assertEquals("My Artist", entry.artist)
        assertEquals("My Game", entry.game)
    }

    @Test
    fun `Zophar compound tag with copyright glyph parses Title-Artist-Game`() {
        val entries = parse("game.nsf::NSF,1,Theme - Composer - Game Name - ©1992 HAL,2:30")
        assertEquals(1, entries.size)
        val entry = entries[0]
        assertEquals("Theme", entry.title)
        assertEquals("Composer", entry.artist)
        assertEquals("Game Name", entry.game)
    }

    @Test
    fun `Zophar compound tag captures the copyright field`() {
        // Pure-ASCII copyright (anchored by the year) so the Latin-1 tag decode round-trips it
        // verbatim — a '©' glyph would mojibake to "Â©" through convert().
        val entry = parse("game.nsf::NSF,1,Theme - Composer - Game Name - (C) 1992 HAL,2:30").single()
        assertEquals("(C) 1992 HAL", entry.copyright)
    }

    @Test
    fun `simple and plain tags leave copyright null`() {
        assertNull(parse("game.nsf::NSF,1,My Title - My Artist - My Game,2:00").single().copyright)
        assertNull(parse("game.nsf::NSF,1,Just A Title,2:00").single().copyright)
    }

    @Test
    fun `compound parse anchors from the right so titles can contain dash separators`() {
        // "Stage 3 - Float Islands" survives because the parser anchors on the trailing copyright
        // field and works backwards — splitting from the left would have eaten "Float Islands"
        // and shifted everything one slot.
        val entries = parse("game.nsf::NSF,1,Stage 3 - Float Islands - Hip Tanaka - Kirby - ©1992,1:30")
        assertEquals(1, entries.size)
        val entry = entries[0]
        assertEquals("Stage 3 - Float Islands", entry.title)
        assertEquals("Hip Tanaka", entry.artist)
        assertEquals("Kirby", entry.game)
    }

    @Test
    fun `mangled copyright character is also a valid right-anchor`() {
        // Upstream encoding errors mangle '©' into the replacement char '�'; the parser
        // accepts it as a copyright marker so the rest of the line still parses.
        val text = "game.nsf::NSF,1,Title - Artist - Game - �1992 HAL,1:00"
        val entry = parse(text).single()
        assertEquals("Title", entry.title)
        assertEquals("Artist", entry.artist)
        assertEquals("Game", entry.game)
    }

    @Test
    fun `bare year is enough to anchor the copyright field`() {
        // No © sigil, just "1990" in the trailing field — the regex `\b(19|20)\d{2}\b` is the
        // last-resort signal.
        val entries = parse("game.nsf::NSF,1,Title - Artist - Game - Released 1990,1:00")
        assertEquals(1, entries.size)
        val entry = entries[0]
        assertEquals("Title", entry.title)
        assertEquals("Artist", entry.artist)
        assertEquals("Game", entry.game)
    }

    @Test
    fun `non-compound dash strings without three parts stay as plain titles`() {
        // "A - B" doesn't match the simple-3 or compound-4 shapes — must be treated as a plain
        // title, not silently dropped or split into a bogus 2-tuple.
        val entries = parse("game.nsf::NSF,1,Track A - Variation B,1:00")
        assertEquals(1, entries.size)
        val entry = entries[0]
        assertEquals("Track A - Variation B", entry.title)
        assertNull(entry.artist)
        assertNull(entry.game)
    }

    @Test
    fun `escaped commas are kept inside a single field`() {
        // m3u tag fields are comma-separated, but a backslash-escaped comma stays inside the
        // field. The split regex uses a negative lookbehind for `\` so "Hello\, World" stays one
        // piece; the backslashes themselves are filtered out after splitting.
        val entries = parse("game.nsf::NSF,1,Hello\\, World - Artist - Game - ©1992,2:00")
        assertEquals(1, entries.size)
        val entry = entries[0]
        assertEquals("Hello, World", entry.title)
        assertEquals("Artist", entry.artist)
    }

    @Test
    fun `length parses minutes-and-seconds into milliseconds`() {
        val entries = parse("game.nsf::NSF,1,Title,2:30,2:20,0:05")
        assertEquals(1, entries.size)
        val entry = entries[0]
        assertEquals(150_000L, entry.lengthMs)
        // Fade length is field index 5 (skipping the fade-start at index 4).
        assertEquals(5_000L, entry.fadeLengthMs)
    }

    @Test
    fun `missing length and fade fall back to LENGTH_UNKNOWN_MS and zero`() {
        val entries = parse("game.nsf::NSF,1,Title")
        assertEquals(1, entries.size)
        val entry = entries[0]
        assertEquals(LENGTH_UNKNOWN_MS, entry.lengthMs)
        assertEquals(0L, entry.fadeLengthMs)
    }

    @Test
    fun `non-integer subtune index drops the line`() {
        // No exception thrown, just a dropped row — corrupt rows shouldn't take the whole
        // playlist down with them.
        assertTrue(parse("game.psf::PSF,abc,Title,1:00").isEmpty())
    }

    private fun parse(text: String): List<M3uEntry> = reader.parse(text.encodeToByteArray())
}
