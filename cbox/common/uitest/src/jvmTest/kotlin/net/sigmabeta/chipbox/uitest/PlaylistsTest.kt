package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.playlistdetail.PlaylistDetail
import net.sigmabeta.chipbox.features.playlists.Playlists
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Playlists list screen (browse mode): empty state, seeded rows, opening a playlist, and the
 * "New Playlist" CTA. Picker mode (Add to Playlist) is covered in [AddToPlaylistTest].
 */
class PlaylistsTest {

    @Test
    fun showsEmptyStateWhenNoPlaylistsExist() = runChipboxUiTest {
        startAtScreen(Playlists())
        assertEmptyStateDisplayed("You haven't created any playlists yet. Tap New Playlist to start one.")
    }

    @Test
    fun seededPlaylistsAreListedWithTheirSongCount() = runChipboxUiTest {
        seedPlaylist("Road Trip", listOf(libraryTracks().first().id))
        seedPlaylist("Boss Themes")

        startAtScreen(Playlists())

        assertIconNameCaptionItemDisplayed("Road Trip", "1 song")
        assertIconNameCaptionItemDisplayed("Boss Themes")
    }

    @Test
    fun tappingAPlaylistOpensItsDetail() = runChipboxUiTest {
        val id = seedPlaylist("Road Trip")
        startAtScreen(Playlists())

        clickIconNameCaptionItem("Road Trip")

        assertNavigationEvent(PlaylistDetail(id))
    }

    @Test
    fun newPlaylistCtaCreatesAndOpensAPlaylist() = runChipboxUiTest {
        startAtScreen(Playlists())

        clickCta("New Playlist")

        // Browse mode: the CTA creates an empty, default-named playlist and opens it.
        val id = waitForPlaylistNamed("New Playlist")
        assertEquals(listOf("New Playlist"), playlistNames())
        assertNavigationEvent(PlaylistDetail(id))
    }
}
