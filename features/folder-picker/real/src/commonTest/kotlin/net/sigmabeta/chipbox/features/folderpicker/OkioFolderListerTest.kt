package net.sigmabeta.chipbox.features.folderpicker

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises [OkioFolderLister] against [FakeFileSystem]. Covers the three behaviors most worth
 * pinning: alphabetical-case-insensitive sort, the dotfile filter (folders + files), and the
 * one-level peek that fills in each row's [FolderPickerEntry.childFolderCount] /
 * [FolderPickerEntry.childFileCount].
 */
class OkioFolderListerTest {

    @Test
    fun `list returns subfolders alphabetically by lower-cased name plus the file count`() {
        val fs = FakeFileSystem()
        fs.createDirectories("/root/Zelda".toPath())
        fs.createDirectories("/root/chrono".toPath())
        fs.createDirectories("/root/Anthology".toPath())
        fs.write("/root/song1.spc".toPath()) { writeUtf8("a") }
        fs.write("/root/song2.spc".toPath()) { writeUtf8("b") }

        val listing = OkioFolderLister(fs).list("/root")

        assertEquals(
            listOf("Anthology", "chrono", "Zelda"),
            listing.folders.map { it.name },
            "Sort is case-insensitive — 'chrono' falls between 'Anthology' and 'Zelda'",
        )
        assertEquals(2, listing.fileCount)
    }

    @Test
    fun `dotfiles are hidden from folder rows, the file count, and the per-row child counts`() {
        val fs = FakeFileSystem()
        fs.createDirectories("/root/.config".toPath())
        fs.createDirectories("/root/Music".toPath())
        fs.write("/root/.DS_Store".toPath()) { writeUtf8("x") }
        fs.write("/root/visible.txt".toPath()) { writeUtf8("y") }
        // Inside /root/Music: one hidden + one visible folder and a hidden + visible file.
        // The visible row's child counts should report 1 folder and 1 file.
        fs.createDirectories("/root/Music/.cache".toPath())
        fs.createDirectories("/root/Music/PSF".toPath())
        fs.write("/root/Music/.hidden".toPath()) { writeUtf8("h") }
        fs.write("/root/Music/cover.png".toPath()) { writeUtf8("c") }

        val listing = OkioFolderLister(fs).list("/root")

        assertEquals(listOf("Music"), listing.folders.map { it.name })
        assertEquals(1, listing.fileCount, "Hidden .DS_Store must not be counted")

        val music = listing.folders.single()
        assertEquals(1, music.childFolderCount, "Hidden .cache must not inflate the folder count")
        assertEquals(1, music.childFileCount, "Hidden .hidden must not inflate the file count")
    }

    @Test
    fun `an unreadable or non-existent path collapses to an empty listing`() {
        // FakeFileSystem throws on list() against a non-existent path — the production
        // lister catches that and returns an empty listing instead of crashing the screen.
        val listing = OkioFolderLister(FakeFileSystem()).list("/does-not-exist")
        assertTrue(listing.folders.isEmpty())
        assertEquals(0, listing.fileCount)
    }
}
