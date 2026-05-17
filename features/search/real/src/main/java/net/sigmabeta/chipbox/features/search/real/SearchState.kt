package net.sigmabeta.chipbox.features.search.real

import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.SearchHistory
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.strings.ChipboxStringId
import net.sigmabeta.sage.components.ImageNameCaptionListModel
import net.sigmabeta.sage.components.ImageNameListModel
import net.sigmabeta.sage.components.SearchHistoryListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.freeform.FreeformState
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

/**
 * Holds the live query, recent-search history, and games/songs search results — all
 * sourced from the DB via the repository. The query lives here (not in hoisted Compose
 * state) so a history-row tap can refill it through the action pipeline, matching the
 * shared `SearchHistoryListItem` contract.
 */
data class SearchState(
    val query: String = "",
    // Last submitted query — updated ~300ms after typing settles; drives the results.
    val submittedQuery: String = "",
    val gameResults: List<Game> = emptyList(),
    val gamesLoading: Boolean = false,
    val songResults: List<Track> = emptyList(),
    val songsLoading: Boolean = false,
    val artistResults: List<Artist> = emptyList(),
    val artistsLoading: Boolean = false,
    val history: List<SearchHistory> = emptyList(),
) : FreeformState<SearchModel>() {

    // The top bar is hidden for this screen (the in-screen SearchBar owns the back
    // affordance), so this title is effectively unused — kept for pipeline correctness.
    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(ChipboxStringId.SEARCH_SCREEN_TITLE),
        shouldShowBack = false,
    )

    override fun toContent(stringProvider: StringProvider): SearchModel = SearchModel(
        query = query,
        submittedQuery = submittedQuery,
        hint = stringProvider.getString(ChipboxStringId.SEARCH_HINT),
        emptyPrompt = stringProvider.getString(ChipboxStringId.SEARCH_EMPTY_PROMPT),
        noResultsTemplate = stringProvider.getString(ChipboxStringId.SEARCH_NO_RESULTS),
        searchingLabel = stringProvider.getString(ChipboxStringId.SEARCH_SEARCHING),
        gamesSectionLabel = stringProvider.getString(ChipboxStringId.SEARCH_SECTION_GAMES),
        songsSectionLabel = stringProvider.getString(ChipboxStringId.SEARCH_SECTION_SONGS),
        artistsSectionLabel = stringProvider.getString(ChipboxStringId.SEARCH_SECTION_ARTISTS),
        searching = gamesLoading || songsLoading || artistsLoading,
        gameItems = gameResults.map { game ->
            ImageNameListModel(
                dataId = game.id,
                name = game.title,
                sourceInfo = SourceInfo(info = game.photoUrl),
                imagePlaceholder = Icon.Album,
                clickAction = SearchAction.GameClicked(game.id),
            )
        },
        songItems = songResults.map { track ->
            ImageNameCaptionListModel(
                dataId = track.id,
                name = track.title,
                caption = track.game?.title.orEmpty(),
                sourceInfo = SourceInfo(info = track.game?.photoUrl),
                imagePlaceholder = Icon.MusicNote,
                clickAction = SearchAction.SongClicked(track.game?.id),
            )
        },
        artistItems = artistResults.map { artist ->
            ImageNameListModel(
                dataId = artist.id,
                name = artist.name,
                sourceInfo = SourceInfo(info = artist.photoUrl),
                imagePlaceholder = Icon.Person,
                clickAction = SearchAction.ArtistClicked(artist.id),
            )
        },
        historyItems = history.map { entry ->
            SearchHistoryListModel(
                dataId = entry.id,
                name = entry.query,
                clickAction = SearchAction.HistoryClicked(entry.query),
                removeAction = SearchAction.HistoryRemoved(entry.id),
            )
        },
    )

    override fun errorContent(error: Throwable): SearchModel = SearchModel.Empty
}
