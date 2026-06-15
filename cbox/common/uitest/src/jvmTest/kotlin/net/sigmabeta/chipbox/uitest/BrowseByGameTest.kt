package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.browsebygame.BrowseByGame
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import kotlin.test.Test

/** Browse-by-game lists every game; tapping one opens its detail screen. */
class BrowseByGameTest {
    @Test
    fun listsGames() = runChipboxUiTest {
        startAtScreen(BrowseByGame)

        assertDisplayed(firstGame().title)
    }

    @Test
    fun tappingGameOpensDetail() = runChipboxUiTest {
        val game = firstGame()

        startAtScreen(BrowseByGame)
        click(game.title)

        assertNavigationEvent(GameDetail(game.id))
    }
}
