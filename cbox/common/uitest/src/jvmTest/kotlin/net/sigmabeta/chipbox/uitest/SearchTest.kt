package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.search.Search
import kotlin.test.Test

/**
 * Search shows its empty prompt initially; typing a query submits it after the debounce.
 *
 * (Result rows — and the navigate/play actions on them — aren't asserted here: the in-memory fake's
 * `searchGames`/`searchSongs`/`searchArtists` return empty, so a query always resolves to the
 * "no results" state regardless of the library.)
 */
class SearchTest {
    @Test
    fun showsEmptyPrompt() = runChipboxUiTest {
        startAtScreen(Search)

        assertDisplayed("Search your library")
    }

    @Test
    fun typingAQuerySubmitsIt() = runChipboxUiTest {
        startAtScreen(Search)

        typeSearch("mega")

        // The composeResources string keeps the XML `\"` escapes literally, so the rendered text has
        // backslashes around the query.
        assertDisplayed("No results for \\\"mega\\\"")
    }
}
