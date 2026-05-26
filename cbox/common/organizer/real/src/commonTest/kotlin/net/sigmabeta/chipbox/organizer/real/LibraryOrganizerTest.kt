package net.sigmabeta.chipbox.organizer.real

import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.organizer.INVALID_CATEGORY
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Covers both halves of [LibraryOrganizer]: the pure `plan()` that turns scanned games into a
 * route plan, and the on-disk `commit()` that applies it through an [okio.FileSystem]. Uses
 * [FakeFileSystem] so the move-and-cleanup logic runs end-to-end on JVM/JS without touching the
 * real disk.
 */
class LibraryOrganizerTest {

    /** Returns the SageStringId's enum `name` so tests can assert against readable categories. */
    private val strings = object : StringProvider {
        override fun getString(string: SageStringId): String = (string as Enum<*>).name
        override fun getStringOneArg(string: SageStringId, arg: String): String = (string as Enum<*>).name
        override fun getStringOneInt(string: SageStringId, arg: Int): String = (string as Enum<*>).name
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = (string as Enum<*>).name
    }

    private val destination = "/library/sorted".toPath()

    // ---- plan() ----

    @Test
    fun `game with no tracks produces no moves`() {
        val fs = FakeFileSystem()
        val organizer = LibraryOrganizer(fs, strings)
        val moves = organizer.plan(
            games = listOf(Game(id = 1, title = "Empty", photoUrl = null, artists = null, tracks = emptyList())),
            destination = destination,
            libraryLocations = emptySet(),
        )
        assertTrue(moves.isEmpty())
    }

    @Test
    fun `single-folder game routes to dest-category-title`() {
        val fs = FakeFileSystem()
        fs.createDirectories("/library/scan/MyGame".toPath())
        fs.write("/library/scan/MyGame/01.psf".toPath()) { writeUtf8("data") }

        val game = gameWithTracks("My Game", tracks = listOf(track("/library/scan/MyGame/01.psf", Platform.PSX)))
        val moves = LibraryOrganizer(fs, strings).plan(listOf(game), destination, libraryLocations = emptySet())

        assertEquals(1, moves.size)
        val move = moves.single()
        assertEquals("PLATFORM_PSX", move.category)
        assertEquals("My Game", move.folderName)
        assertEquals("/library/sorted/PLATFORM_PSX/My Game".toPath(), move.destination)
        assertEquals("/library/scan/MyGame".toPath(), move.source)
        assertFalse(move.sourceIsRoot)
    }

    @Test
    fun `game whose tracks span multiple folders is routed to Invalid Folders`() {
        // A game shouldn't be physically split — when it is, we don't try to merge it; each
        // folder goes to a numbered Invalid Folders slot for the user to sort out manually.
        val fs = FakeFileSystem()
        fs.createDirectories("/scan/A".toPath())
        fs.createDirectories("/scan/B".toPath())
        fs.write("/scan/A/1.psf".toPath()) { writeUtf8("x") }
        fs.write("/scan/B/2.psf".toPath()) { writeUtf8("x") }

        val game = gameWithTracks(
            "Scattered",
            tracks = listOf(
                track("/scan/A/1.psf", Platform.PSX),
                track("/scan/B/2.psf", Platform.PSX),
            ),
        )
        val moves = LibraryOrganizer(fs, strings).plan(listOf(game), destination, emptySet())

        assertEquals(2, moves.size)
        moves.forEach { assertEquals(INVALID_CATEGORY, it.category) }
        // Names carry a running 1-based index — `Scattered-1`, `Scattered-2`.
        assertEquals(setOf("Scattered-1", "Scattered-2"), moves.map { it.folderName }.toSet())
    }

    @Test
    fun `illegal characters in the game title are replaced with underscores`() {
        // Per ILLEGAL_CHARS in the implementation: \/:*?"<>| become _, control chars too.
        val fs = FakeFileSystem()
        fs.createDirectories("/scan/X".toPath())
        fs.write("/scan/X/1.psf".toPath()) { writeUtf8("x") }
        val game = gameWithTracks("Bad: Title/With*Chars", listOf(track("/scan/X/1.psf", Platform.OTHER)))

        val move = LibraryOrganizer(fs, strings).plan(listOf(game), destination, emptySet()).single()
        assertEquals("Bad_ Title_With_Chars", move.folderName)
    }

    @Test
    fun `blank title falls back to Unknown`() {
        // A title that sanitises to the empty string (trimEnd('.') strips trailing dots) hits
        // the documented `ifBlank { "Unknown" }` fallback. Illegal chars only map to '_', so
        // "///" stays "___" — not blank. Use trailing dots to actually clear the buffer.
        val fs = FakeFileSystem()
        fs.createDirectories("/scan/Y".toPath())
        fs.write("/scan/Y/1.psf".toPath()) { writeUtf8("x") }
        val game = gameWithTracks("...", listOf(track("/scan/Y/1.psf", Platform.OTHER)))

        val move = LibraryOrganizer(fs, strings).plan(listOf(game), destination, emptySet()).single()
        assertEquals("Unknown", move.folderName)
    }

    @Test
    fun `duplicate titles in the same category get -2 -3 suffixes`() {
        // Two distinct games with the same sanitised name landing under the same platform must
        // get unique folder names so one doesn't clobber the other when commit() runs.
        val fs = FakeFileSystem()
        fs.createDirectories("/scan/A".toPath())
        fs.createDirectories("/scan/B".toPath())
        fs.createDirectories("/scan/C".toPath())
        fs.write("/scan/A/1.psf".toPath()) { writeUtf8("x") }
        fs.write("/scan/B/1.psf".toPath()) { writeUtf8("x") }
        fs.write("/scan/C/1.psf".toPath()) { writeUtf8("x") }

        val games = listOf(
            gameWithTracks("Game", listOf(track("/scan/A/1.psf", Platform.PSX))),
            gameWithTracks("Game", listOf(track("/scan/B/1.psf", Platform.PSX))),
            gameWithTracks("Game", listOf(track("/scan/C/1.psf", Platform.PSX))),
        )
        val names = LibraryOrganizer(fs, strings).plan(games, destination, emptySet()).map { it.folderName }
        assertEquals(listOf("Game", "Game-2", "Game-3"), names)
    }

    @Test
    fun `same title under different platforms does not collide`() {
        // Each category has its own used-names set, so "Game" on PSX and "Game" on SNES both
        // get the bare name — no -2 suffix needed because they're in different folders.
        val fs = FakeFileSystem()
        fs.createDirectories("/scan/A".toPath())
        fs.createDirectories("/scan/B".toPath())
        fs.write("/scan/A/1.psf".toPath()) { writeUtf8("x") }
        fs.write("/scan/B/1.psf".toPath()) { writeUtf8("x") }

        val moves = LibraryOrganizer(fs, strings).plan(
            games = listOf(
                gameWithTracks("Game", listOf(track("/scan/A/1.psf", Platform.PSX))),
                gameWithTracks("Game", listOf(track("/scan/B/1.psf", Platform.SNES))),
            ),
            destination = destination,
            libraryLocations = emptySet(),
        )
        assertEquals(setOf("Game"), moves.map { it.folderName }.toSet())
        assertEquals(setOf("PLATFORM_PSX", "PLATFORM_SNES"), moves.map { it.category }.toSet())
    }

    @Test
    fun `category is the most common platform among the game's tracks`() {
        // Tracks vote: 2 PSX, 1 SNES → routed under PSX. Ties broken arbitrarily by maxByOrNull.
        val fs = FakeFileSystem()
        fs.createDirectories("/scan/Mixed".toPath())
        listOf("a.psf", "b.psf", "c.spc").forEach {
            fs.write("/scan/Mixed/$it".toPath()) { writeUtf8("x") }
        }
        val game = gameWithTracks(
            "Mixed",
            listOf(
                track("/scan/Mixed/a.psf", Platform.PSX),
                track("/scan/Mixed/b.psf", Platform.PSX),
                track("/scan/Mixed/c.spc", Platform.SNES),
            ),
        )
        val move = LibraryOrganizer(fs, strings).plan(listOf(game), destination, emptySet()).single()
        assertEquals("PLATFORM_PSX", move.category)
    }

    @Test
    fun `source-as-library-root only includes loose files in entries`() {
        // Library-location roots aren't a single game's own folder — they're the top of the
        // scan tree. Sub-folders of a root are *other* games and must be left for their own
        // moves, so a root-source move's entries are only the loose files.
        val fs = FakeFileSystem()
        val root = "/library".toPath()
        fs.createDirectories(root)
        fs.createDirectories("/library/SubGame".toPath())
        fs.write("/library/SubGame/inner.psf".toPath()) { writeUtf8("x") }
        fs.write("/library/loose.psf".toPath()) { writeUtf8("x") }
        fs.write("/library/loose2.psf".toPath()) { writeUtf8("x") }

        // The root-level game's tracks live in the root, no parent folder of their own.
        val game = gameWithTracks(
            "Root Game",
            listOf(
                track("/library/loose.psf", Platform.PSX),
                track("/library/loose2.psf", Platform.PSX),
            ),
        )
        val move = LibraryOrganizer(fs, strings).plan(
            games = listOf(game),
            destination = destination,
            libraryLocations = setOf("/library"),
        ).single()
        assertTrue(move.sourceIsRoot, "source folder == library root must be flagged")
        // SubGame/ must not be listed as an entry — only the two loose files.
        assertEquals(
            setOf("/library/loose.psf".toPath(), "/library/loose2.psf".toPath()),
            move.entries.toSet(),
        )
    }

    // ---- commit() ----

    @Test
    fun `commit moves a track folder into the destination layout`() {
        val fs = FakeFileSystem()
        fs.createDirectories("/scan/MyGame".toPath())
        fs.write("/scan/MyGame/01.psf".toPath()) { writeUtf8("hello") }
        fs.write("/scan/MyGame/02.psf".toPath()) { writeUtf8("world") }

        val organizer = LibraryOrganizer(fs, strings)
        val game = gameWithTracks(
            "My Game",
            tracks = listOf(
                track("/scan/MyGame/01.psf", Platform.PSX),
                track("/scan/MyGame/02.psf", Platform.PSX),
            ),
        )
        val moves = organizer.plan(listOf(game), destination, emptySet())
        val result = organizer.commit(moves)

        assertEquals(1, result.movedFolders)
        assertEquals(0, result.failedFolders)
        // Files exist at the destination, source folder is gone.
        assertTrue(fs.exists("/library/sorted/PLATFORM_PSX/My Game/01.psf".toPath()))
        assertTrue(fs.exists("/library/sorted/PLATFORM_PSX/My Game/02.psf".toPath()))
        assertFalse(fs.exists("/scan/MyGame".toPath()), "emptied source folder should be removed")
    }

    @Test
    fun `commit preserves library-root source folders even after emptying them`() {
        // The root itself is not a "game folder" — it's the scan root, must survive the move.
        val fs = FakeFileSystem()
        fs.createDirectories("/library".toPath())
        fs.write("/library/loose.psf".toPath()) { writeUtf8("x") }

        val organizer = LibraryOrganizer(fs, strings)
        val game = gameWithTracks("Root", listOf(track("/library/loose.psf", Platform.PSX)))
        organizer.commit(organizer.plan(listOf(game), destination, libraryLocations = setOf("/library")))

        assertTrue(fs.exists("/library".toPath()), "library root must not be deleted")
        assertTrue(fs.exists("/library/sorted/PLATFORM_PSX/Root/loose.psf".toPath()))
    }

    @Test
    fun `commit recursively moves nested directories`() {
        // moveInto descends into directories, creates the matching destination tree, and removes
        // the source after — pin that traversal in for a single-game two-level fixture.
        val fs = FakeFileSystem()
        fs.createDirectories("/scan/G/sub".toPath())
        fs.write("/scan/G/top.psf".toPath()) { writeUtf8("a") }
        fs.write("/scan/G/sub/nested.psf".toPath()) { writeUtf8("b") }

        val organizer = LibraryOrganizer(fs, strings)
        val game = gameWithTracks("G", listOf(track("/scan/G/top.psf", Platform.PSX)))
        organizer.commit(organizer.plan(listOf(game), destination, emptySet()))

        assertTrue(fs.exists("/library/sorted/PLATFORM_PSX/G/top.psf".toPath()))
        assertTrue(fs.exists("/library/sorted/PLATFORM_PSX/G/sub/nested.psf".toPath()))
        assertFalse(fs.exists("/scan/G".toPath()))
    }

    @Test
    fun `commit is a no-op when source and destination already match`() {
        // Idempotency: re-running the organizer after a successful run should not move or
        // re-fail anything. Source canonicalises to the same path as destination → skip.
        val fs = FakeFileSystem()
        fs.createDirectories("/library/sorted/PLATFORM_PSX/Already".toPath())
        fs.write("/library/sorted/PLATFORM_PSX/Already/01.psf".toPath()) { writeUtf8("x") }

        val organizer = LibraryOrganizer(fs, strings)
        val game = gameWithTracks(
            "Already",
            listOf(track("/library/sorted/PLATFORM_PSX/Already/01.psf", Platform.PSX)),
        )
        val result = organizer.commit(organizer.plan(listOf(game), destination, emptySet()))
        assertEquals(1, result.movedFolders, "the move is counted, even though it was a no-op on disk")
        assertEquals(0, result.failedFolders)
        // File is still in place.
        assertTrue(fs.exists("/library/sorted/PLATFORM_PSX/Already/01.psf".toPath()))
    }

    @Test
    fun `commit returns failedFolders count for moves it cannot apply`() {
        // One of the entries in the move plan disappears between plan() and commit() (e.g.
        // user moved a file manually). The source folder still resolves — so canonicalOrNull
        // doesn't short-circuit — but moveInto throws when atomicMove + copy both fail on the
        // missing entry. The runCatching in commit() must turn that into a counted failure
        // without aborting the rest of the batch.
        val fs = FakeFileSystem()
        fs.createDirectories("/scan/Ghost".toPath())
        fs.write("/scan/Ghost/1.psf".toPath()) { writeUtf8("x") }
        val organizer = LibraryOrganizer(fs, strings)
        val game = gameWithTracks("Ghost", listOf(track("/scan/Ghost/1.psf", Platform.PSX)))
        val moves = organizer.plan(listOf(game), destination, emptySet())
        // Yank the inner file, but leave the source directory so canonicalize still works.
        fs.delete("/scan/Ghost/1.psf".toPath())

        val result = organizer.commit(moves)
        assertEquals(0, result.movedFolders)
        assertEquals(1, result.failedFolders)
    }

    @Test
    fun `multiple games batch together and report aggregate counts`() {
        val fs = FakeFileSystem()
        listOf("A", "B", "C").forEach {
            fs.createDirectories("/scan/$it".toPath())
            fs.write("/scan/$it/1.psf".toPath()) { writeUtf8("x") }
        }
        val games = listOf(
            gameWithTracks("A", listOf(track("/scan/A/1.psf", Platform.PSX))),
            gameWithTracks("B", listOf(track("/scan/B/1.psf", Platform.SNES))),
            gameWithTracks("C", listOf(track("/scan/C/1.psf", Platform.NES))),
        )
        val organizer = LibraryOrganizer(fs, strings)
        val result = organizer.commit(organizer.plan(games, destination, emptySet()))
        assertEquals(3, result.movedFolders)
        assertEquals(0, result.failedFolders)
        listOf("PLATFORM_PSX/A", "PLATFORM_SNES/B", "PLATFORM_NES/C").forEach {
            assertNotNull(fs.metadataOrNull("/library/sorted/$it/1.psf".toPath()))
        }
    }

    // ---- helpers ----

    private fun gameWithTracks(title: String, tracks: List<Track>): Game = Game(
        id = title.hashCode().toLong(),
        title = title,
        photoUrl = null,
        artists = null,
        tracks = tracks,
    )

    private fun track(path: String, platform: Platform): Track = Track(
        id = path.hashCode().toLong(),
        path = path,
        source = "test",
        title = path.substringAfterLast('/'),
        trackLengthMs = 0,
        trackNumber = 0,
        fadeLengthMs = 0,
        game = null,
        artists = null,
        platform = platform,
    )

    /** Recursively delete [path] — handy in tests that yank a folder out from under commit(). */
    @Suppress("unused")
    private fun FileSystem.deleteRecursively(path: Path) {
        if (metadataOrNull(path)?.isDirectory == true) {
            listOrNull(path).orEmpty().forEach { deleteRecursively(it) }
        }
        if (exists(path)) delete(path)
    }
}
