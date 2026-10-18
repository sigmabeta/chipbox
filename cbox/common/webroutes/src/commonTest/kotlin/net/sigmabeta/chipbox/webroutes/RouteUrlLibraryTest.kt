package net.sigmabeta.chipbox.webroutes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.sigmabeta.chipbox.features.browsealltracks.BrowseAllTracks
import net.sigmabeta.chipbox.features.browsebyartist.BrowseByArtist
import net.sigmabeta.chipbox.features.browsebygame.BrowseByGame
import net.sigmabeta.chipbox.features.browsebyplatform.BrowseByPlatform
import net.sigmabeta.chipbox.features.favorites.Favorites
import net.sigmabeta.chipbox.features.playlistdetail.PlaylistDetail
import net.sigmabeta.chipbox.features.playlists.Playlists

/**
 * Library sub-screens. Each is a pushed destination under `/library`, except the tab root itself.
 */
class RouteUrlLibraryTest {

    @Test
    fun `library destinations encode to their documented paths`() {
        assertEquals("/library/favorites", encode(Favorites))
        assertEquals("/library/playlists", encode(Playlists()))
        assertEquals("/library/playlists/7", encode(PlaylistDetail(7L)))
        assertEquals("/library/games", encode(BrowseByGame))
        assertEquals("/library/artists", encode(BrowseByArtist))
        assertEquals("/library/platforms", encode(BrowseByPlatform))
        assertEquals("/library/tracks", encode(BrowseAllTracks))
    }

    @Test
    fun `library paths decode to their destinations`() {
        assertEquals(Favorites, decode("/library/favorites"))
        assertEquals(Playlists(), decode("/library/playlists"))
        assertEquals(PlaylistDetail(7L), decode("/library/playlists/7"))
        assertEquals(BrowseByGame, decode("/library/games"))
        assertEquals(BrowseByArtist, decode("/library/artists"))
        assertEquals(BrowseByPlatform, decode("/library/platforms"))
        assertEquals(BrowseAllTracks, decode("/library/tracks"))
    }

    @Test
    fun `a picker-mode playlists screen is never encoded`() {
        assertNull(RouteUrl.encode(ChipboxTarget.Deep(Playlists(pendingTrackIds = listOf(1L, 2L)))))
        assertNull(
            RouteUrl.encode(
                ChipboxTarget.Deep(Playlists(pendingTrackIds = listOf(1L), suggestedName = "From game")),
            ),
        )
        assertNull(RouteUrl.encode(ChipboxTarget.Deep(Playlists(suggestedName = "From game"))))
    }

    @Test
    fun `playlist ids must be positive`() {
        assertNull(RouteUrl.encode(ChipboxTarget.Deep(PlaylistDetail(0L))))
        assertNull(RouteUrl.encode(ChipboxTarget.Deep(PlaylistDetail(-3L))))
        assertNull(decode("/library/playlists/0"))
        assertNull(decode("/library/playlists/-3"))
        assertNull(decode("/library/playlists/not-a-number"))
        assertNull(decode("/library/playlists/999999999999999999999999"))
    }

    private fun encode(destination: Any): String? = RouteUrl.encode(ChipboxTarget.Deep(destination))

    private fun decode(path: String): Any? = (RouteUrl.decode(path) as? ChipboxTarget.Deep)?.destination
}
