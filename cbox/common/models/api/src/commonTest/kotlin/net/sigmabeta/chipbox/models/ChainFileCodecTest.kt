package net.sigmabeta.chipbox.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChainFileCodecTest {

    @Test
    fun `empty list encodes to an empty string`() {
        assertEquals("", encodeChainFiles(emptyList()))
    }

    @Test
    fun `empty string decodes to an empty list`() {
        // Symmetric with the encode side — the storage layer round-trips "no chain files" as "".
        assertTrue(decodeChainFiles("").isEmpty())
    }

    @Test
    fun `single entry round-trips`() {
        val files = listOf(ChainFile(filename = "game.psflib", uri = "file:///library/game.psflib"))
        assertEquals(files, decodeChainFiles(encodeChainFiles(files)))
    }

    @Test
    fun `multiple entries round-trip in order`() {
        val files = listOf(
            ChainFile("a.psflib", "file:///a.psflib"),
            ChainFile("b.psflib", "content://b.psflib"),
            ChainFile("c.psflib", "https://example/c.psflib"),
        )
        assertEquals(files, decodeChainFiles(encodeChainFiles(files)))
    }

    @Test
    fun `lines without a tab are skipped`() {
        // A line that's just "name\turi" produces an entry; anything else is malformed and
        // dropped so a corrupt row can't crash the reader.
        val files = decodeChainFiles("good\tfile:///good\nmalformed-no-tab\nalso-good\tfile:///also")
        assertEquals(
            listOf(
                ChainFile("good", "file:///good"),
                ChainFile("also-good", "file:///also"),
            ),
            files,
        )
    }

    @Test
    fun `tab at the very start is treated as malformed`() {
        // First tab at index 0 means empty filename — `tab < 1` filters that out.
        assertTrue(decodeChainFiles("\tonly-uri").isEmpty())
    }

    @Test
    fun `uri can contain extra tabs without breaking the split`() {
        // Decoder splits on the *first* tab, so a tab inside the URI ends up in the URI field
        // intact. (Encode side never inserts extra tabs, but a hand-edited row shouldn't crash.)
        val decoded = decodeChainFiles("name\turi\twith\ttabs")
        assertEquals(listOf(ChainFile("name", "uri\twith\ttabs")), decoded)
    }
}
