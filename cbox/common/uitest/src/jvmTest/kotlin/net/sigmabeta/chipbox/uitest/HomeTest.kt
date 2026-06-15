package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.artistdetail.ArtistDetail
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.features.home.Home
import net.sigmabeta.chipbox.player.director.SessionRequest
import kotlin.test.Test

/**
 * Home shows a "games of the day" carousel and the "RNG take the wheel" shortcuts. Tapping a game
 * opens its detail; the RNG shortcuts start a random song (Director) or jump to a random game/artist.
 */
class HomeTest {
    @Test
    fun showsGamesOfTheDayAndRng() = runChipboxUiTest {
        startAtScreen(Home)

        assertSectionHeader("Games of the day")
        assertGridImageItemDisplayed("Random Song")
    }

    @Test
    fun tappingGameOpensDetail() = runChipboxUiTest {
        startAtScreen(Home)

        // The "games of the day" carousel is date-shuffled, so click whichever card is first (always
        // on-screen) and assert it opens *a* game detail.
        clickFirstCardInHomeSection("Games of the day")

        assertNavigationEventOfType<GameDetail>()
    }

    @Test
    fun randomSongStartsASession() = runChipboxUiTest {
        startAtScreen(Home)

        click("Random Song")

        assertDirectorReceived<SessionRequest.Start>()
    }

    @Test
    fun randomGameOpensAGameDetail() = runChipboxUiTest {
        startAtScreen(Home)

        click("Random Game")

        assertNavigationEventOfType<GameDetail>()
    }

    @Test
    fun randomArtistOpensAnArtistDetail() = runChipboxUiTest {
        startAtScreen(Home)

        click("Random Artist")

        assertNavigationEventOfType<ArtistDetail>()
    }
}
