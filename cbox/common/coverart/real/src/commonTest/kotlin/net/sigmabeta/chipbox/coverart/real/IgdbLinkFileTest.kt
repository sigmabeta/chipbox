package net.sigmabeta.chipbox.coverart.real

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import net.sigmabeta.chipbox.coverart.COVER_ART_TTL_DAYS
import net.sigmabeta.chipbox.coverart.CoverLookup
import net.sigmabeta.chipbox.models.Platform

/**
 * Multiplatform tests for [IgdbLinkFile]. Uses Okio's [FakeFileSystem] so the same suite covers the
 * Android, JVM, and JS targets without poking the real disk. The covered surface: round-trip of a
 * Found / NoCover match, the TTL gate, the refreshDate semantics that keep the Matched timestamp
 * stable on re-confirms, and the no-op behaviour when the on-disk file already matches the body.
 */
@OptIn(ExperimentalTime::class)
class IgdbLinkFileTest {

    private val now = Instant.parse("2026-05-26T12:00:00Z")
    private val folder = "/library/games/My Game".toPath()

    @Test
    fun `read returns null when no descriptor file exists`() {
        val fs = FakeFileSystem()
        val link = IgdbLinkFile(fs)
        assertNull(link.read(folders = listOf(folder), now = now))
    }

    @Test
    fun `writeInto then read round-trips a Found match`() {
        val fs = FakeFileSystem()
        val link = IgdbLinkFile(fs)
        val match = CoverLookup.Found(
            imageId = "co1abc",
            igdbId = "12345",
            igdbName = "My Game",
            igdbSlug = "my-game",
        )
        link.writeInto(
            folders = listOf(folder),
            title = "My Game",
            platforms = setOf(Platform.SNES),
            match = match,
            refreshDate = true,
            now = now,
        )
        val read = link.read(folders = listOf(folder), now = now)
        assertEquals(match, read, "round-trip should preserve every Found field")
    }

    @Test
    fun `read returns NoCover when no Cover line is present but IGDB id is`() {
        // NoCover means "matched a game but it has no cover art on IGDB". The descriptor still
        // records the IGDB identifiers so we don't re-query on the next run.
        val fs = FakeFileSystem()
        val link = IgdbLinkFile(fs)
        val match = CoverLookup.NoCover(igdbId = "999", igdbName = "Obscure Title", igdbSlug = "obscure")
        link.writeInto(folders = listOf(folder), title = "Obscure Title", platforms = emptySet(), match = match, refreshDate = true, now = now)
        val read = link.read(folders = listOf(folder), now = now)
        assertEquals(match, read)
    }

    @Test
    fun `read returns null when the descriptor is past the TTL`() {
        // Write a record stamped "now"; advance the clock past the TTL window; the read should
        // refuse it so the caller re-queries IGDB.
        val fs = FakeFileSystem()
        val link = IgdbLinkFile(fs)
        link.writeInto(
            folders = listOf(folder),
            title = "My Game",
            platforms = setOf(Platform.PSX),
            match = CoverLookup.Found(imageId = "id", igdbId = "1", igdbName = "n", igdbSlug = "s"),
            refreshDate = true,
            now = now,
        )
        val stale = now + (COVER_ART_TTL_DAYS + 1).days
        assertNull(link.read(folders = listOf(folder), now = stale))
    }

    @Test
    fun `read returns the record at the TTL boundary`() {
        // Boundary exactly at COVER_ART_TTL_DAYS should still count as valid — the comparison is
        // strict "older than", not "older than or equal to". Pin it so a future "off by one second"
        // doesn't quietly cull entries.
        val fs = FakeFileSystem()
        val link = IgdbLinkFile(fs)
        link.writeInto(
            folders = listOf(folder),
            title = "My Game",
            platforms = setOf(Platform.PSX),
            match = CoverLookup.Found(imageId = "id", igdbId = "1", igdbName = "n", igdbSlug = "s"),
            refreshDate = true,
            now = now,
        )
        val onBoundary = now + COVER_ART_TTL_DAYS.days
        assertNotNull(link.read(folders = listOf(folder), now = onBoundary))
    }

    @Test
    fun `writeInto with refreshDate=false preserves the existing Matched timestamp`() {
        // Re-confirming the same match should NOT bump the timestamp — otherwise re-runs would
        // hold a stale match indefinitely with each re-confirm pushing the TTL forward.
        val fs = FakeFileSystem()
        val link = IgdbLinkFile(fs)
        val match = CoverLookup.Found(imageId = "id", igdbId = "1", igdbName = "n", igdbSlug = "s")
        link.writeInto(folders = listOf(folder), title = "My Game", platforms = setOf(Platform.PSX), match = match, refreshDate = true, now = now)
        val firstContent = fs.read("$folder/igdb.txt".toPath())
        val matchedLineFirst = firstContent.lineSequence().first { it.startsWith("Matched:") }

        val later = now + 10.days
        link.writeInto(folders = listOf(folder), title = "My Game", platforms = setOf(Platform.PSX), match = match, refreshDate = false, now = later)
        val secondContent = fs.read("$folder/igdb.txt".toPath())
        val matchedLineSecond = secondContent.lineSequence().first { it.startsWith("Matched:") }

        assertEquals(matchedLineFirst, matchedLineSecond, "the Matched timestamp must stay put on a non-refresh re-write")
    }

    @Test
    fun `writeInto with refreshDate=true updates the Matched timestamp`() {
        // Mirror of the previous test: when the lookup is actually fresh, the timestamp should
        // move so the TTL window resets.
        val fs = FakeFileSystem()
        val link = IgdbLinkFile(fs)
        val match = CoverLookup.Found(imageId = "id", igdbId = "1", igdbName = "n", igdbSlug = "s")
        link.writeInto(folders = listOf(folder), title = "My Game", platforms = setOf(Platform.PSX), match = match, refreshDate = true, now = now)

        val later = now + 10.days
        link.writeInto(folders = listOf(folder), title = "My Game", platforms = setOf(Platform.PSX), match = match, refreshDate = true, now = later)
        val content = fs.read("$folder/igdb.txt".toPath())
        val matched = content.lineSequence().first { it.startsWith("Matched:") }
        assertTrue(matched.contains(later.toString()), "expected Matched stamped at $later, got: $matched")
    }

    @Test
    fun `writeInto writes a human-readable descriptor with the expected fields`() {
        val fs = FakeFileSystem()
        val link = IgdbLinkFile(fs)
        val match = CoverLookup.Found(
            imageId = "co1abc",
            igdbId = "42",
            igdbName = "Chrono Trigger",
            igdbSlug = "chrono-trigger",
        )
        link.writeInto(
            folders = listOf(folder),
            title = "Chrono Trigger",
            platforms = setOf(Platform.SNES, Platform.PSX),
            match = match,
            refreshDate = true,
            now = now,
        )
        val content = fs.read("$folder/igdb.txt".toPath())
        assertTrue(content.contains("Game:"), "should label the game line")
        assertTrue(content.contains("Chrono Trigger"))
        assertTrue(content.contains("IGDB:"))
        assertTrue(content.contains("Chrono Trigger (#42)"))
        assertTrue(content.contains("URL:"))
        assertTrue(content.contains("https://www.igdb.com/games/chrono-trigger"))
        assertTrue(content.contains("Platforms:"))
        // Platform list is alphabetised — PSX before SNES because we sort by name.
        assertTrue(content.contains("PSX, SNES"))
        assertTrue(content.contains("Cover:"))
        assertTrue(content.contains("co1abc"))
    }

    @Test
    fun `write to a folder whose parent doesn't exist creates the directory`() {
        val fs = FakeFileSystem()
        val link = IgdbLinkFile(fs)
        val deepFolder = "/library/categories/snes/missing/parent".toPath()
        link.writeInto(
            folders = listOf(deepFolder),
            title = "x",
            platforms = setOf(Platform.SNES),
            match = CoverLookup.Found(imageId = "id"),
            refreshDate = true,
            now = now,
        )
        // The descriptor should now exist where we'd expect.
        assertTrue(fs.exists("$deepFolder/igdb.txt".toPath()))
    }

    @Test
    fun `read uses the first folder whose descriptor is current`() {
        // Game lives in two physical folders; only one carries a fresh descriptor. The reader
        // should pick that one and ignore the absent / stale siblings.
        val fs = FakeFileSystem()
        val link = IgdbLinkFile(fs)
        val folderA = "/library/games/A".toPath()
        val folderB = "/library/games/B".toPath()
        val match = CoverLookup.Found(imageId = "id", igdbId = "1", igdbName = "n", igdbSlug = "s")
        link.writeInto(folders = listOf(folderB), title = "n", platforms = emptySet(), match = match, refreshDate = true, now = now)
        val read = link.read(folders = listOf(folderA, folderB), now = now)
        assertEquals(match, read)
    }

    /** Read the entire UTF-8 contents at [path] — wraps Okio's [FileSystem.read] block form so the
     *  call sites stay terse. */
    private fun FakeFileSystem.read(path: okio.Path): String =
        read(path) { readUtf8() }
}
