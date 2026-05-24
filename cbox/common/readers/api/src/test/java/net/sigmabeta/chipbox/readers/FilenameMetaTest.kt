package net.sigmabeta.chipbox.readers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FilenameMetaTest {

    @Test
    fun `strips leading track number and extension`() {
        val meta = deriveMetaFromFilename("01 Prologue.minincsf")
        assertEquals(1, meta.trackNumber)
        assertEquals("Prologue", meta.title)
    }

    @Test
    fun `keeps separators inside the title`() {
        val meta = deriveMetaFromFilename("12 Godot ~ The Fragrance of Dark-Colored Coffee.minincsf")
        assertEquals(12, meta.trackNumber)
        assertEquals("Godot ~ The Fragrance of Dark-Colored Coffee", meta.title)
    }

    @Test
    fun `handles dot and dash separators`() {
        assertEquals(FilenameMeta(3, "Trial"), deriveMetaFromFilename("03. Trial.psf"))
        assertEquals(FilenameMeta(4, "Trial"), deriveMetaFromFilename("04 - Trial.psf"))
        assertEquals(FilenameMeta(5, "Trial"), deriveMetaFromFilename("05-Trial.psf"))
    }

    @Test
    fun `no leading number keeps whole base name as title`() {
        val meta = deriveMetaFromFilename("End.minincsf")
        assertNull(meta.trackNumber)
        assertEquals("End", meta.title)
    }

    @Test
    fun `does not split a number glued to a word`() {
        val meta = deriveMetaFromFilename("1up.psf")
        assertNull(meta.trackNumber)
        assertEquals("1up", meta.title)
    }

    @Test
    fun `keeps a title that merely starts with the word Unknown`() {
        val meta = deriveMetaFromFilename("99 Unknown 1.minincsf")
        assertEquals(99, meta.trackNumber)
        assertEquals("Unknown 1", meta.title)
    }

    @Test
    fun `title may contain dots`() {
        val meta = deriveMetaFromFilename("02 Mr. Smith.psf")
        assertEquals(2, meta.trackNumber)
        assertEquals("Mr. Smith", meta.title)
    }
}
