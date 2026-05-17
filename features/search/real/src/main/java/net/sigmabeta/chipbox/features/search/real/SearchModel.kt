package net.sigmabeta.chipbox.features.search.real

import net.sigmabeta.sage.components.ImageNameCaptionListModel
import net.sigmabeta.sage.components.ImageNameListModel
import net.sigmabeta.sage.components.SearchHistoryListModel

/**
 * Fully-resolved render model for the Search screen. The query lives in [SearchState]
 * (so history clicks can refill it through the action pipeline); the list models here
 * are handed straight to the shared list-item composables by [SearchContent].
 */
data class SearchModel(
    /** Live text-field contents. */
    val query: String,
    /** Last *submitted* query (the one that actually ran the search). */
    val submittedQuery: String,
    val hint: String,
    val emptyPrompt: String,
    /** "No results for %s" — shown when a submitted search returns nothing. */
    val noResultsTemplate: String,
    val searchingLabel: String,
    val gamesSectionLabel: String,
    val songsSectionLabel: String,
    val artistsSectionLabel: String,
    val searching: Boolean,
    val gameItems: List<ImageNameListModel>,
    val songItems: List<ImageNameCaptionListModel>,
    val artistItems: List<ImageNameListModel>,
    val historyItems: List<SearchHistoryListModel>,
) {
    companion object {
        val Empty = SearchModel(
            query = "",
            submittedQuery = "",
            hint = "",
            emptyPrompt = "",
            noResultsTemplate = "",
            searchingLabel = "",
            gamesSectionLabel = "",
            songsSectionLabel = "",
            artistsSectionLabel = "",
            searching = false,
            gameItems = emptyList(),
            songItems = emptyList(),
            artistItems = emptyList(),
            historyItems = emptyList(),
        )
    }
}
