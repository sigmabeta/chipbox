package net.sigmabeta.chipbox.perfbench

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.sigmabeta.chipbox.models.ChainFile
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.repository.RawGame
import net.sigmabeta.chipbox.repository.RawTrack

/**
 * Behaviour of the [LibrarySnapshot] contract: it round-trips through the shared JSON dialect, and
 * the paths it stores are relative to the snapshot's corpus root so the same snapshot is valid on
 * any host or device.
 */
class LibrarySnapshotTest {
    @Test
    fun `round-trips a fully populated snapshot`() {
        val snapshot = snapshot(games = listOf(rawGame()))

        val json = PerfbenchJson.encodeToString(snapshot)
        val back = PerfbenchJson.decodeFromString<LibrarySnapshot>(json)

        assertEquals(snapshot, back)
        assertTrue(json.contains("\"scanner_version\""), "scan metadata key missing")
        assertTrue(json.contains("\"reader_versions\""), "reader versions key missing")
        assertTrue(json.contains("\"folderSignature\""), "raw scan metadata not serialized")
    }

    @Test
    fun `carries scan metadata and the root reference`() {
        val snapshot = snapshot(games = listOf(rawGame()))

        assertEquals("/corpus", snapshot.root)
        assertEquals(7, snapshot.scannerVersion)
        assertEquals(mapOf("psf" to 3), snapshot.readerVersions)
    }

    @Test
    fun `relativizes track, folder, chain and photo paths against the root`() {
        val relativized = rawGame().relativized("/corpus")

        assertEquals("Game", relativized.folderKey)
        assertEquals("Game/cover.png", relativized.photoUrl)
        val track = relativized.tracks.single()
        assertEquals("Game/track.psf", track.path)
        assertEquals("Game/lib.psflib", track.chainFiles.single().uri)
        assertEquals("lib.psflib", track.chainFiles.single().filename)
    }

    @Test
    fun `tolerates a root with a trailing separator`() {
        assertEquals("Game/track.psf", rawGame().relativized("/corpus/").tracks.single().path)
    }

    @Test
    fun `rebasing on the original root restores absolute paths`() {
        val original = rawGame()

        val roundTripped = original.relativized("/corpus").rebased("/corpus")

        assertEquals(original, roundTripped)
    }

    @Test
    fun `rebasing on a different root moves the whole corpus`() {
        val rebased = rawGame().relativized("/corpus").rebased("/sdcard/corpus")

        assertEquals("/sdcard/corpus/Game", rebased.folderKey)
        assertEquals("/sdcard/corpus/Game/track.psf", rebased.tracks.single().path)
        assertEquals("/sdcard/corpus/Game/lib.psflib", rebased.tracks.single().chainFiles.single().uri)
    }

    @Test
    fun `leaves a null photo null through relativize and rebase`() {
        val game = rawGame().copy(photoUrl = null)

        assertNull(game.relativized("/corpus").photoUrl)
        assertNull(game.relativized("/corpus").rebased("/corpus").photoUrl)
    }

    @Test
    fun `preserves non-path scan metadata through the transforms`() {
        val track = rawGame().relativized("/corpus").tracks.single()

        assertEquals(1, track.scannerVersion)
        assertEquals(3, track.readerVersion)
        assertEquals(Platform.PSX, track.platform)
        assertEquals(1_000L, track.length)
    }

    @Test
    fun `leaves a path outside the root untouched rather than mangling it`() {
        val game = rawGame().copy(photoUrl = "/elsewhere/cover.png")

        val relatived = game.relativized("/corpus")

        assertEquals("/elsewhere/cover.png", relatived.photoUrl)
        assertEquals("/elsewhere/cover.png", relatived.rebased("/sdcard/corpus").photoUrl)
    }

    private fun snapshot(games: List<RawGame>) = LibrarySnapshot(
        scannerVersion = 7,
        readerVersions = mapOf("psf" to 3),
        root = "/corpus",
        games = games,
    )

    private fun rawGame() = RawGame(
        title = "Game",
        photoUrl = "/corpus/Game/cover.png",
        folderKey = "/corpus/Game",
        folderSignature = "signature-hash",
        tracks = listOf(rawTrack()),
        copyright = "2026 Example",
    )

    private fun rawTrack() = RawTrack(
        path = "/corpus/Game/track.psf",
        source = "file",
        title = "Track",
        artist = "Unknown",
        game = "Game",
        length = 1_000L,
        trackNumber = 0,
        fadeLengthMs = 0L,
        chainFiles = listOf(ChainFile(filename = "lib.psflib", uri = "/corpus/Game/lib.psflib")),
        extension = "psf",
        platform = Platform.PSX,
        scannerVersion = 1,
        readerVersion = 3,
    )
}
