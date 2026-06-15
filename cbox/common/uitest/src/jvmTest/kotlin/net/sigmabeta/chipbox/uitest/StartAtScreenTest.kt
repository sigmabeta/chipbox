package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import kotlin.test.Test

/**
 * The first script written purely against the DSL: drive to a known game from the pre-populated
 * (deterministic) library and assert its title.
 */
class StartAtScreenTest {
    @Test
    fun startingAtAGameShowsItsTitle() = runChipboxUiTest {
        startAtScreen(GameDetail(gameId("Iron Quest")))

        assertTitle("Iron Quest")
    }
}
