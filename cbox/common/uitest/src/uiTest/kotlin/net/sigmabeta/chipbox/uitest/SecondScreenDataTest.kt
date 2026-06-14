package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import kotlin.test.Test

/**
 * Regression for the in-memory repository bug where the second GameDetail/ArtistDetail screen
 * re-showed the first one's data (a load-once flag + shared replay flow). Navigating to a second
 * game must load that game, not the first.
 */
class SecondScreenDataTest {
    @Test
    fun navigatingToASecondGameLoadsItsOwnData() = runChipboxUiTest {
        val first = seedGame("Mega Dungeon")
        val second = seedGame("Silent Saga")

        startAtScreen(GameDetail(first))
        assertTitle("Mega Dungeon")

        startAtScreen(GameDetail(second))
        assertTitle("Silent Saga")
    }
}
