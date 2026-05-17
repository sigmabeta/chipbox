package net.sigmabeta.chipbox.features.search.real

/**
 * Pre-resolved static strings for the Search screen. All resolution happens in
 * [SearchState.toContent]; the live query is *not* part of this model — it is hoisted
 * local state passed straight to [SearchContent], so typing never touches the ViewModel.
 */
data class SearchModel(
    val hint: String,
    val emptyPrompt: String,
    val fakeRecentSearches: List<String>,
    val comingSoonTemplate: String,
) {
    companion object {
        val Empty = SearchModel(
            hint = "",
            emptyPrompt = "",
            fakeRecentSearches = emptyList(),
            comingSoonTemplate = "",
        )
    }
}
