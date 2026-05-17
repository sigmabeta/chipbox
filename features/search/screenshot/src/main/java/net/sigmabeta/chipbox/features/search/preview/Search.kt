package net.sigmabeta.chipbox.features.search.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import net.sigmabeta.chipbox.features.search.real.SearchContent
import net.sigmabeta.chipbox.features.search.real.SearchState
import net.sigmabeta.chipbox.models.SearchHistory
import net.sigmabeta.chipbox.ui.previews.DevicePreviews
import net.sigmabeta.chipbox.ui.previews.ScreenPreview
import net.sigmabeta.chipbox.ui.previews.fake.FakeModelGenerator
import net.sigmabeta.chipbox.ui.previews.previewWidthClass
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.list.WidthClass

private val NoOpSink = ActionSink { }

@DevicePreviews
@Composable
internal fun Search(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    val state = searchResultsState()
    ScreenPreview(
        darkTheme = darkTheme,
        syntheticWidthClass = syntheticWidthClass,
        screenName = "Search",
    ) { stringProvider ->
        SearchContent(
            listItems = state.toActual(stringProvider).listItems,
            query = state.query,
            showDebug = false,
            actionSink = NoOpSink,
        )
    }
}

@DevicePreviews
@Composable
internal fun SearchHistoryAndPrompt(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    val state = searchHistoryState()
    ScreenPreview(
        darkTheme = darkTheme,
        syntheticWidthClass = syntheticWidthClass,
        screenName = "Search",
    ) { stringProvider ->
        SearchContent(
            listItems = state.toActual(stringProvider).listItems,
            query = state.query,
            showDebug = false,
            actionSink = NoOpSink,
        )
    }
}

private fun searchResultsState(): SearchState {
    val generator = FakeModelGenerator()
    val artists = generator.randomArtists()
    val games = generator.randomGames()
    val tracks = generator.randomTracks(artists, games)

    return SearchState(
        query = SAMPLE_QUERY,
        submittedQuery = SAMPLE_QUERY,
        gameResults = LCE.Content(games),
        songResults = LCE.Content(tracks),
        artistResults = LCE.Content(artists),
    )
}

private fun searchHistoryState(): SearchState = SearchState(
    history = LCE.Content(
        listOf(
            SearchHistory(id = 1L, query = "Chrono Trigger"),
            SearchHistory(id = 2L, query = "Final Fantasy VI"),
            SearchHistory(id = 3L, query = "Sonic the Hedgehog"),
        ),
    ),
)

private const val SAMPLE_QUERY = "Chrono"
