package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.artistdetail.ArtistDetail
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.features.search.Search
import net.sigmabeta.chipbox.player.director.SessionRequest
import kotlin.test.Test

/**
 * Search shows its empty prompt, then results once a query is typed (after the debounce). Tapping a
 * game/artist result opens its detail; tapping a song result starts playback of the result setlist.
 */
class SearchTest {
    @Test
    fun showsEmptyPrompt() = runChipboxUiTest {
        startAtScreen(Search)

        assertDisplayed("Search your library")
    }

    @Test
    fun typingAQueryShowsResults() = runChipboxUiTest {
        startAtScreen(Search)

        typeSearch("mega")

        assertSectionHeader("Games")
        assertDisplayed("Mega Dungeon")
    }

    @Test
    fun aQueryWithNoMatchesShowsNoResults() = runChipboxUiTest {
        startAtScreen(Search)

        typeSearch("zzzznope")

        // The composeResources string keeps the XML `\"` escapes literally (backslashes around it).
        assertDisplayed("No results for \\\"zzzznope\\\"")
    }

    @Test
    fun gameResultOpensDetail() = runChipboxUiTest {
        startAtScreen(Search)

        typeSearch("mega")
        click("Mega Dungeon")

        assertNavigationEventOfType<GameDetail>()
    }

    @Test
    fun artistResultOpensDetail() = runChipboxUiTest {
        startAtScreen(Search)

        typeSearch("Shimomura")
        click("Jake Shimomura")

        assertNavigationEvent(ArtistDetail(artistId("Jake Shimomura")))
    }

    @Test
    fun songResultStartsPlayback() = runChipboxUiTest {
        startAtScreen(Search)

        typeSearch("Battle")
        click("Battle 19")

        assertDirectorReceived<SessionRequest.StartSetlist>()
    }
}
