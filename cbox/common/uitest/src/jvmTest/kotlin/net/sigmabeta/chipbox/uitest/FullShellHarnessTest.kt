package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import kotlin.test.Test

/**
 * Drives the full `ChipboxAppUi` shell through the DSL: start at a seeded `GameDetail` inside the
 * active tab and assert both its title (top bar) and its content (artist + track) render. This is
 * the same path `startAtScreen` exercises, kept as a slightly fuller smoke test of the real screen.
 */
class FullShellHarnessTest {
    @Test
    fun startingAtSeededGameShowsTitleAndContent() = runChipboxUiTest {
        val gameId = seedGame(title = "Metal Slug", tracks = listOf("Stage 1"), artists = listOf("JIM"))

        startAtScreen(GameDetail(gameId))

        assertTitle("Metal Slug")
        assertDisplayed("JIM")
        assertDisplayed("Stage 1")
    }
}
