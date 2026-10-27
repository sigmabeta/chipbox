package net.sigmabeta.chipbox.contentsource

import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [LocalFileContentSource] refuses a root that overlaps one it already holds. Overlapping roots
 * would emit shared folders twice in one scan, and the parallel readers would then race on the
 * unique `game.folder_key` index. Canonicalization happens here (not in the shared scanner) because
 * the file system — symlinks, `..`, trailing separators — is this source's concern.
 */
class LocalFileContentSourceLocationsTest {

    private val fileSystem = FakeFileSystem().apply { allowSymlinks = true }
    private val locationsFile = "/work/locations.txt".toPath()

    private fun source() = LocalFileContentSource(fileSystem, locationsFile)

    private fun dir(path: String): Path {
        fileSystem.createDirectories(path.toPath())
        return path.toPath()
    }

    private fun persisted(): String = fileSystem.read(locationsFile) { readUtf8() }

    @Test
    fun `adding a folder nested inside an existing root is refused`() {
        dir("/music/SNES/Album")
        val source = source()
        source.addLocation("/music".toPath())

        source.addLocation("/music/SNES/Album".toPath())

        assertEquals(listOf("/music"), source.locations.value.map { it.identifier })
        assertEquals("/music", persisted(), "a refused location is never persisted")
    }

    @Test
    fun `adding a root that contains an existing one is refused`() {
        dir("/music/SNES")
        val source = source()
        source.addLocation("/music/SNES".toPath())

        source.addLocation("/music".toPath())

        assertEquals(listOf("/music/SNES"), source.locations.value.map { it.identifier })
    }

    @Test
    fun `adding the same folder again is refused, not duplicated`() {
        dir("/music/SNES")
        val source = source()
        source.addLocation("/music/SNES".toPath())

        source.addLocation("/music/SNES".toPath())
        source.addLibraryLocation("/music/SNES/")

        assertEquals(listOf("/music/SNES"), source.locations.value.map { it.identifier })
    }

    @Test
    fun `sibling roots are accepted and survive a reload`() {
        dir("/music/SNES")
        dir("/music/Genesis")
        val source = source()
        source.addLocation("/music/SNES".toPath())
        source.addLocation("/music/Genesis".toPath())

        assertEquals(
            listOf("/music/SNES", "/music/Genesis"),
            source().locations.value.map { it.identifier },
        )
        assertNull(source.findOverlappingLocations())
    }

    @Test
    fun `a hand-edited locations file is checked against canonical paths`() {
        dir("/music/SNES")
        dir("/music/Genesis")
        fileSystem.createSymlink("/alias".toPath(), "/music".toPath())
        // The admin hand-edited the file into an overlap the add-time check never saw.
        fileSystem.createDirectories(locationsFile.parent!!)
        fileSystem.write(locationsFile) {
            writeUtf8("/music/SNES\n/alias/SNES")
        }
        val source = source()

        assertEquals(
            LibraryLocationOverlap("/music/SNES", "/alias/SNES"),
            source.findOverlappingLocations(),
            "the two roots only overlap after the symlink is resolved",
        )
    }

    @Test
    fun `a trailing separator and a dot segment do not hide an overlap`() {
        dir("/music/SNES")
        val source = source()
        source.addLocation("/music".toPath())

        assertEquals("/music", source.findOverlappingLocation("/music/SNES/.")?.identifier)
        assertEquals("/music", source.findOverlappingLocation("/music/./SNES/")?.identifier)
    }

    @Test
    fun `the picker asks findOverlappingLocation about the candidate before adding`() {
        dir("/music/SNES/Album")
        val source = source()
        source.addLocation("/music".toPath())

        assertEquals("/music", source.findOverlappingLocation("/music/SNES/Album")?.identifier)
        assertNull(source.findOverlappingLocation("/other/Album"))
    }
}
