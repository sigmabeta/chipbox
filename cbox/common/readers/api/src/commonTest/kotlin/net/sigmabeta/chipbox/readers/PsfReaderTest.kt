package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * commonTest companion to the JVM-side `NcsfReaderTest`/`PsfReaderTest`: same coverage shape, but
 * built from plain `ByteArray` so the format-parsing path also runs under the JS target where the
 * java.io.* helpers don't exist. Focuses on the parts of [PsfReader] that are pure spec
 * interpretation (header, platform mapping, tag/lib extraction, malformed-file rejection).
 */
class PsfReaderTest {

    private val reader = PsfReader(BluntHatchet())

    @Test
    fun `PSX platform code 0x01 maps to PSX`() {
        val info = reader.readTagInfo(psfFile(platformCode = 0x01, tagText = "title=Anthem\n"))
        assertNotNull(info)
        assertEquals(Platform.PSX, info.platform)
        assertEquals("Anthem", info.tags["title"])
    }

    @Test
    fun `NCSF platform code 0x25 maps to NDS and tags parse`() {
        val info = reader.readTagInfo(
            psfFile(
                platformCode = 0x25,
                tagText = "_lib=game.ncsflib\ntitle=Prologue\ngame=PWAA 3\nlength=0:20\n",
            ),
        )
        assertNotNull(info)
        assertEquals(Platform.NDS, info.platform)
        assertEquals("Prologue", info.tags["title"])
        assertEquals("PWAA 3", info.tags["game"])
        assertEquals(listOf("game.ncsflib"), info.libReferences)
    }

    @Test
    fun `2SF platform code 0x24 also maps to NDS`() {
        // Both Nitro Composer (NCSF, 0x25) and the older 2SF (0x24) play through the same DS
        // emulator — they share a Platform.
        val info = reader.readTagInfo(psfFile(platformCode = 0x24, tagText = "title=DS\n"))
        assertNotNull(info)
        assertEquals(Platform.NDS, info.platform)
    }

    @Test
    fun `unsupported platform code returns null`() {
        // Anything not in the documented mapping table is rejected up front rather than handed
        // downstream with Platform.OTHER — the rest of the player expects a real emulator match.
        assertNull(reader.readTagInfo(psfFile(platformCode = 0x99, tagText = "title=x\n")))
    }

    @Test
    fun `lib references come back in _lib then _lib2 _lib3 order`() {
        // libKeyToIndex treats bare "_lib" as 1 so it always sorts first; numbered libs follow in
        // numeric (not lexical) order. Build the tags out of order on purpose to prove the sort
        // is doing the work.
        val info = reader.readTagInfo(
            psfFile(
                platformCode = 0x01,
                tagText = "_lib3=c.psflib\n_lib=a.psflib\n_lib2=b.psflib\ntitle=x\n",
            ),
        )
        assertNotNull(info)
        assertEquals(listOf("a.psflib", "b.psflib", "c.psflib"), info.libReferences)
    }

    @Test
    fun `tags after a utf8=1 flag decode as UTF-8 rather than Latin-1`() {
        // "é" in UTF-8 is two bytes (0xC3 0xA9); without the flag PSF tags decode as Latin-1 and
        // those bytes come back as "Ã©". The flag flips the decoder.
        val info = reader.readTagInfo(
            psfFile(platformCode = 0x01, tagText = "utf8=1\ntitle=Café\n"),
        )
        assertNotNull(info)
        assertEquals("Café", info.tags["title"])
    }

    @Test
    fun `file smaller than the 4-byte signature returns null`() {
        // nextBytesAsString returns null for an underflow; the reader logs and bails.
        assertNull(reader.readTagInfo(ByteArray(2)))
    }

    @Test
    fun `wrong magic returns null`() {
        // Anything not starting with "PSF" is rejected up front.
        val bytes = "XXXX".encodeToByteArray() + ByteArray(20)
        assertNull(reader.readTagInfo(bytes))
    }

    @Test
    fun `data section declared past file end returns null`() {
        // Hand-crafted: reservedAreaSize is way larger than the file. The bounds check fires
        // before we'd otherwise read past the end and underflow.
        val bytes = ByteArray(16) // signature + 3 size fields, no body
        "PSF".encodeToByteArray().copyInto(bytes, 0)
        bytes[3] = 0x01 // PSX
        writeIntLe(bytes, 4, 9_999) // reserved size — file isn't anywhere near this big
        writeIntLe(bytes, 8, 0)
        writeIntLe(bytes, 12, 0)
        assertNull(reader.readTagInfo(bytes))
    }

    @Test
    fun `untagged file parses to empty metadata instead of failing`() {
        // The [TAG] block is optional: many sequentially-ripped .psf files and most .psflib files
        // carry none. A header-only PSF is still a valid, playable track, so it must parse (with
        // empty tags) rather than be rejected — otherwise the scanner drops it. Regression test for
        // ~half a PS1 rip set failing to scan.
        val bytes = headerOnlyPsf(platformCode = 0x01)

        val info = reader.readTagInfo(bytes)

        assertNotNull(info)
        assertEquals(Platform.PSX, info.platform)
        assertTrue(info.tags.isEmpty())
        assertTrue(info.libReferences.isEmpty())
    }

    @Test
    fun `untagged file still yields a playable track`() {
        val tracks = reader.readTracksFromFile(headerOnlyPsf(platformCode = 0x01), "/music/BGM00_0000.psf")

        assertNotNull(tracks)
        assertEquals(1, tracks.size)
        assertEquals(Platform.PSX, tracks.first().platform)
    }

    private fun headerOnlyPsf(platformCode: Int): ByteArray {
        val bytes = ByteArray(16) // signature + 3 size fields, no body and no [TAG] section
        "PSF".encodeToByteArray().copyInto(bytes, 0)
        bytes[3] = platformCode.toByte()
        writeIntLe(bytes, 4, 0)
        writeIntLe(bytes, 8, 0)
        writeIntLe(bytes, 12, 0)
        return bytes
    }

    @Test
    fun `buildRawTrack maps PSF tag keys onto the RawTrack fields`() {
        // Confirms the small adapter (and the orUnknown fallback for missing keys). Title is
        // present, artist isn't — the missing field comes back as "Unknown" rather than null.
        val track = reader.buildRawTrack(
            tags = mapOf(
                "title" to "Memory's Skyscraper",
                "game" to "Persona 1",
                "length" to "1:30",
                "fade" to "0:05",
            ),
            identifier = "track.psf",
            platform = Platform.PSX,
        )
        assertEquals("Memory's Skyscraper", track.title)
        assertEquals(TAG_UNKNOWN, track.artist)
        assertEquals("Persona 1", track.game)
        assertEquals(90_000L, track.length)
        assertEquals(5_000L, track.fadeLengthMs)
        assertEquals(Platform.PSX, track.platform)
    }

    @Test
    fun `buildRawTrack maps the optional descriptive PSF tags`() {
        val track = reader.buildRawTrack(
            tags = mapOf(
                "title" to "x",
                "copyright" to "1999 Squaresoft",
                "year" to "1999",
                "genre" to "RPG",
                "comment" to "ripped by foo",
            ),
            identifier = "track.psf",
            platform = Platform.PSX,
        )
        assertEquals("1999 Squaresoft", track.copyright)
        assertEquals("1999", track.releaseDate)
        assertEquals("RPG", track.genre)
        assertEquals("ripped by foo", track.comment)
    }

    @Test
    fun `buildRawTrack leaves missing optional tags null`() {
        val track = reader.buildRawTrack(tags = mapOf("title" to "x"), identifier = "t.psf", platform = Platform.PSX)
        assertNull(track.copyright)
        assertNull(track.releaseDate)
        assertNull(track.genre)
        assertNull(track.comment)
    }

    @Test
    fun `buildRawTrack treats a literal Unknown optional tag as absent`() {
        // Some rips write the placeholder word literally (e.g. a USF `comment=Unknown`); it carries
        // no information, so the optional field comes back null rather than the word "Unknown".
        val track = reader.buildRawTrack(
            tags = mapOf("title" to "x", "comment" to "Unknown", "copyright" to "Unknown"),
            identifier = "t.psf",
            platform = Platform.PSX,
        )
        assertNull(track.comment)
        assertNull(track.copyright)
    }

    /**
     * Builds a minimal, valid PSF container: "PSF" + platform byte, empty reserved/program areas
     * (so the tag section is at offset 16), then "[TAG]" + [tagText]. Mirrors what a real .psf /
     * .minincsf would look like when stripped of its compressed program body.
     */
    private fun psfFile(platformCode: Int, tagText: String): ByteArray {
        val tagBytes = "[TAG]".encodeToByteArray() + tagText.encodeToByteArray()
        val out = ByteArray(16 + tagBytes.size)
        "PSF".encodeToByteArray().copyInto(out, 0)
        out[3] = platformCode.toByte()
        writeIntLe(out, 4, 0) // reservedAreaSize
        writeIntLe(out, 8, 0) // programAreaSize
        writeIntLe(out, 12, 0) // program crc — ignored by reader
        tagBytes.copyInto(out, 16)
        return out
    }
}
