package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import kotlin.test.Test

/**
 * The first script written purely against the DSL: pick a game from the pre-populated
 * (deterministic) library, start at its screen, and assert its title.
 */
class StartAtScreenTest {
    @Test
    fun startingAtAGameShowsItsTitle() = runChipboxUiTest {
        val game = firstGame()

        startAtScreen(GameDetail(game.id))

        assertTitle(game.title)
    }
}
