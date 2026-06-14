package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import kotlin.test.Test

/**
 * Exercises the typed click verbs: clicking a game's artist (a WideItem) navigates to that
 * artist's screen. Selection is by item type + name (the testTag seam in ListModel.Content), so
 * `clickWideItem` targets the artist row specifically.
 */
class ClickItemTest {
    @Test
    fun clickingAnArtistOpensTheArtistScreen() = runChipboxUiTest {
        val gameId = seedGame(title = "Metal Slug", tracks = listOf("Stage 1"), artist = "JIM")
        startAtScreen(GameDetail(gameId))

        clickWideItem("JIM")

        // ArtistDetail's title is the artist's name.
        assertTitle("JIM")
    }
}
