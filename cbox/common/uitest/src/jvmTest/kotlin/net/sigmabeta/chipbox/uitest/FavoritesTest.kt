package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.artistdetail.ArtistDetail
import net.sigmabeta.chipbox.features.favorites.Favorites
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.player.director.SessionRequest
import kotlin.test.Test

/**
 * The Favorites screen hydrates the favorited track/game/artist ids (seeded into the fake favorites
 * store) against the library and renders them in their sections; tapping a song starts a session,
 * tapping a game or artist opens its detail screen. With nothing favorited it shows the empty state.
 *
 * Data comes from the deterministic library (seed 1234): game "Iron Quest", its track "Castle 36",
 * and artist "Jake Shimomura".
 */
class FavoritesTest {
    @Test
    fun showsEmptyStateWhenNothingFavorited() = runChipboxUiTest {
        startAtScreen(Favorites)

        assertTitle("Favorites")
        assertEmptyStateDisplayed("Add a game, song, or artist to your favorites to find them here quickly.")
    }

    @Test
    fun favoritedSongIsListed() = runChipboxUiTest {
        favoriteTrack(trackId("Castle 36"))
        startAtScreen(Favorites)

        assertSectionHeader("Songs")
        assertNameCaptionValueItemDisplayed("Castle 36")
    }

    @Test
    fun tappingFavoritedSongStartsASession() = runChipboxUiTest {
        favoriteTrack(trackId("Castle 36"))
        startAtScreen(Favorites)

        clickNameCaptionValueItem("Castle 36")

        assertDirectorReceived<SessionRequest.Start>()
    }

    @Test
    fun favoritedGameOpensGameDetail() = runChipboxUiTest {
        val id = gameId("Iron Quest")
        favoriteGame(id)
        startAtScreen(Favorites)

        assertSectionHeader("Games")
        assertWideItemDisplayed("Iron Quest")
        clickWideItem("Iron Quest")

        assertNavigationEvent(GameDetail(id))
    }

    @Test
    fun favoritedArtistOpensArtistDetail() = runChipboxUiTest {
        val id = artistId("Jake Shimomura")
        favoriteArtist(id)
        startAtScreen(Favorites)

        assertSectionHeader("Artists")
        assertWideItemDisplayed("Jake Shimomura")
        clickWideItem("Jake Shimomura")

        assertNavigationEvent(ArtistDetail(id))
    }
}
