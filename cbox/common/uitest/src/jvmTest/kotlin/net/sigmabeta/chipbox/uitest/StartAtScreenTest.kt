package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import kotlin.test.Test

/**
 * The first script written purely against the DSL: start at a screen, assert its title.
 */
class StartAtScreenTest {
    @Test
    fun startingAtAGameShowsItsTitle() = runChipboxUiTest {
        val gameId = seedGame("Metal Slug")

        startAtScreen(GameDetail(gameId))

        assertTitle("Metal Slug")
    }
}
