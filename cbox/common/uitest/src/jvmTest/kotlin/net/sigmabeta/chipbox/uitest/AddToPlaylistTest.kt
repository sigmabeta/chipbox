package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.artistdetail.ArtistDetail
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.features.playlists.Playlists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The "Add to Playlist" affordance: the CTAs added to existing detail screens open the Playlists
 * screen in picker mode (carrying the tracks + a suggested name), and picker mode either drops the
 * tracks into a chosen playlist or creates a new one holding them.
 */
class AddToPlaylistTest {

    @Test
    fun gameDetailAddToPlaylistOpensThePickerWithTheGamesTracks() = runChipboxUiTest {
        startAtScreen(GameDetail(gameId("Iron Quest")))
        waitForContent("Add to Playlist")

        clickCta("Add to Playlist")

        val picker = lastNavigationOfType<Playlists>()
        assertNotNull(picker, "expected a navigation to the Playlists picker")
        assertTrue(picker.pendingTrackIds.isNotEmpty(), "picker should carry the game's tracks")
        assertEquals("From game Iron Quest", picker.suggestedName)
    }

    @Test
    fun artistDetailAddToPlaylistOpensThePickerWithASuggestedName() = runChipboxUiTest {
        startAtScreen(ArtistDetail(artistId("Jake Shimomura")))
        waitForContent("Add to Playlist")

        clickCta("Add to Playlist")

        val picker = lastNavigationOfType<Playlists>()
        assertNotNull(picker, "expected a navigation to the Playlists picker")
        assertTrue(picker.pendingTrackIds.isNotEmpty(), "picker should carry the artist's tracks")
        assertEquals("From artist Jake Shimomura", picker.suggestedName)
    }

    // The picker only carries the pending track ids (it never renders them), so plain ids suffice.
    private val pending = listOf(101L, 102L)

    @Test
    fun pickerModeShowsTheAddTitleAndAddsTracksToTheChosenPlaylist() = runChipboxUiTest {
        val target = seedPlaylist("Existing")

        startAtScreen(Playlists(pendingTrackIds = pending))
        assertTitle("Add to Playlist")

        clickIconNameCaptionItem("Existing")

        waitForPlaylistTrackCount(target, pending.size)
        assertEquals(pending, playlistTrackIds(target))
    }

    @Test
    fun pickerNewPlaylistCtaCreatesAPlaylistHoldingTheTracks() = runChipboxUiTest {
        startAtScreen(Playlists(pendingTrackIds = pending, suggestedName = "My Bulk Add"))

        clickCta("New Playlist")

        val id = waitForPlaylistNamed("My Bulk Add")
        assertEquals(pending, playlistTrackIds(id))
    }
}
