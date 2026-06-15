package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.artistdetail.ArtistDetail
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import kotlin.test.Test

/**
 * Exercises the typed click verbs + navigation assertion: clicking a game's artist (a WideItem)
 * navigates to that artist's screen. Selection is by item type + name (the testTag seam in
 * ListModel.Content), so `clickWideItem` targets the artist row specifically.
 */
class ClickItemTest {
    @Test
    fun clickingAnArtistNavigatesToArtistDetail() = runChipboxUiTest {
        val gameId = 11L
        val gameName = "Silent Saga"
        val artistId = 9L
        val artistName = "Jake Shimomura"

        startAtScreen(GameDetail(gameId))

        assertTitle(gameName)
        clickWideItem(artistName)

        assertNavigationEvent(ArtistDetail(artistId))
        assertTitle(artistName)
    }
}
