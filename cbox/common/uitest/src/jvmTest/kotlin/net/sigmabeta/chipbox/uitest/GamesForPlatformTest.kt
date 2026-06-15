package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.features.gamesforplatform.GamesForPlatform
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.player.director.SessionRequest
import kotlin.test.Test

/**
 * Games-for-platform shows "play all"/"shuffle all" CTAs and the platform's games. The CTAs start a
 * session through the Director; tapping a game opens its detail.
 */
class GamesForPlatformTest {
    @Test
    fun showsCtasAndGames() = runChipboxUiTest {
        startAtScreen(GamesForPlatform(Platform.OTHER))

        assertCtaDisplayed("Play All")
        assertGridImageItemDisplayed(firstGame().title)
    }

    @Test
    fun playAllStartsASession() = runChipboxUiTest {
        startAtScreen(GamesForPlatform(Platform.OTHER))

        click("Play All")

        assertDirectorReceived<SessionRequest.Start>()
    }

    @Test
    fun shuffleAllStartsASession() = runChipboxUiTest {
        startAtScreen(GamesForPlatform(Platform.OTHER))

        click("Shuffle All")

        assertDirectorReceived<SessionRequest.Start>()
    }

    @Test
    fun tappingGameOpensDetail() = runChipboxUiTest {
        val game = firstGame()

        startAtScreen(GamesForPlatform(Platform.OTHER))
        click(game.title)

        assertNavigationEvent(GameDetail(game.id))
    }
}
