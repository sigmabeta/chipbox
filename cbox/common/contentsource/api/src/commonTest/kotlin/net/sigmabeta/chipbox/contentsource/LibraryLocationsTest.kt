package net.sigmabeta.chipbox.contentsource

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Library roots must be disjoint: two readers emitting the same folder key race on its game row
 * (the unique `game.folder_key` index), which aborts the whole scan. These cover the pure
 * path-segment rules that decide whether a configured list is acceptable.
 */
class LibraryLocationsTest {

    @Test
    fun `the same folder twice overlaps`() {
        assertTrue(LibraryLocations.overlaps("/music/SNES", "/music/SNES"))
        assertEquals(
            LibraryLocationOverlap("/music/SNES", "/music/SNES"),
            LibraryLocations.firstOverlap(listOf("/music/SNES", "/music/SNES")),
        )
    }

    @Test
    fun `a nested root overlaps in either order`() {
        assertTrue(LibraryLocations.overlaps("/music", "/music/SNES"))
        assertTrue(LibraryLocations.overlaps("/music/SNES", "/music"))
    }

    @Test
    fun `a shared name prefix that is not a path segment is disjoint`() {
        assertFalse(LibraryLocations.overlaps("/music/SNES", "/music/SNES2"))
        assertNull(LibraryLocations.firstOverlap(listOf("/music/SNES", "/music/SNES2")))
    }

    @Test
    fun `sibling roots are disjoint`() {
        assertNull(
            LibraryLocations.firstOverlap(
                listOf("/music/SNES", "/music/Genesis", "/music/NES"),
            ),
        )
    }

    @Test
    fun `redundant separators do not hide an overlap`() {
        assertTrue(LibraryLocations.overlaps("/music/SNES/", "/music/SNES"))
        assertTrue(LibraryLocations.overlaps("/music//SNES", "/music/SNES/"))
    }

    @Test
    fun `the filesystem root contains every absolute root`() {
        assertTrue(LibraryLocations.overlaps("/", "/music/SNES"))
    }

    @Test
    fun `windows separators and drive letters compare by segment`() {
        assertTrue(LibraryLocations.overlaps("C:\\Music", "C:\\Music\\SNES"))
        assertFalse(LibraryLocations.overlaps("C:\\Music", "D:\\Music"))
        assertTrue(LibraryLocations.overlaps("C:\\Music", "C:/Music/SNES"))
    }

    @Test
    fun `firstOverlap reports the first configured pair in order`() {
        assertEquals(
            LibraryLocationOverlap("/music/SNES", "/music/SNES"),
            LibraryLocations.firstOverlap(listOf("/music/SNES", "/music/Genesis", "/music/SNES")),
        )
    }

    @Test
    fun `an empty or single-element list has no overlap`() {
        assertNull(LibraryLocations.firstOverlap(emptyList()))
        assertNull(LibraryLocations.firstOverlap(listOf("/music/SNES")))
    }
}
