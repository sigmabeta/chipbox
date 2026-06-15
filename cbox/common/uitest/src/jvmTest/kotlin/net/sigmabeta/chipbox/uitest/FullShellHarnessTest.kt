package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import kotlin.test.Test

/**
 * Drives the full `ChipboxAppUi` shell through the DSL: start at a `GameDetail` inside the active
 * tab and assert both its title (top bar) and its content (an artist + a track) render. Uses the
 * pre-populated library's "Iron Quest" (the first game for seed 1234) rather than a seeded fixture.
 */
class FullShellHarnessTest {
    @Test
    fun startingAtAGameShowsTitleAndContent() = runChipboxUiTest {
        startAtScreen(GameDetail(gameId("Iron Quest")))

        assertTitle("Iron Quest")
        // The typed verbs pin the model type, so the artist row and the track row are matched
        // unambiguously even though "Jake Shimomura" also appears as a song row's caption.
        assertWideItemDisplayed("Jake Shimomura")
        assertNameCaptionValueItemDisplayed("Castle 36")
    }
}
