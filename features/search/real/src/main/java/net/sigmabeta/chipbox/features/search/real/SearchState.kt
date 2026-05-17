package net.sigmabeta.chipbox.features.search.real

import net.sigmabeta.chipbox.strings.ChipboxStringId
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.freeform.FreeformState
import net.sigmabeta.sage.ui.StringProvider

/**
 * Search has no real state yet (UI-only): the query lives in hoisted Compose state, not
 * here. This exists solely so the freeform pipeline has a [FreeformState] to render the
 * title bar and resolve the screen's static strings into a [SearchModel].
 */
data class SearchState(
    val placeholder: Boolean = true,
) : FreeformState<SearchModel>() {

    // The top bar is hidden for this screen (the in-screen SearchBar owns the back
    // affordance), so this title is effectively unused — kept for pipeline correctness.
    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(ChipboxStringId.SEARCH_SCREEN_TITLE),
        shouldShowBack = false,
    )

    override fun toContent(stringProvider: StringProvider): SearchModel = SearchModel(
        hint = stringProvider.getString(ChipboxStringId.SEARCH_HINT),
        emptyPrompt = stringProvider.getString(ChipboxStringId.SEARCH_EMPTY_PROMPT),
        fakeRecentSearches = listOf(
            stringProvider.getString(ChipboxStringId.SEARCH_FAKE_RECENT_1),
            stringProvider.getString(ChipboxStringId.SEARCH_FAKE_RECENT_2),
            stringProvider.getString(ChipboxStringId.SEARCH_FAKE_RECENT_3),
        ),
        comingSoonTemplate = stringProvider.getString(ChipboxStringId.SEARCH_COMING_SOON),
    )

    override fun errorContent(error: Throwable): SearchModel = SearchModel.Empty
}
