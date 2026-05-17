package net.sigmabeta.chipbox.features.search.real

import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.SearchHistory
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.strings.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.EmptyStateListModel
import net.sigmabeta.sage.components.ImageNameCaptionListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.LoadingType
import net.sigmabeta.sage.components.SearchHistoryListModel
import net.sigmabeta.sage.components.SectionHeaderListModel
import net.sigmabeta.sage.components.SquareItemListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

/**
 * Search rendered through the standard SAGE list pipeline (like every other browse
 * screen, and like VGLS's search). The query lives here so a history-row tap can refill
 * it through the action pipeline; per-category [LCE]s drive their own loading/error/empty
 * states via [withStandardErrorAndLoading].
 */
data class SearchState(
    val query: String = "",
    // Last submitted query — set ~300ms after typing settles; drives the results view.
    val submittedQuery: String = "",
    val gameResults: LCE<List<Game>> = LCE.Uninitialized,
    val songResults: LCE<List<Track>> = LCE.Uninitialized,
    val artistResults: LCE<List<Artist>> = LCE.Uninitialized,
    val history: LCE<List<SearchHistory>> = LCE.Uninitialized,
) : ListState() {

    // Top bar is hidden for this screen (the in-screen SearchBar owns chrome), so the
    // title is unused — kept for pipeline correctness.
    override fun title(stringProvider: StringProvider) = TitleBarModel()

    override fun toListItems(stringProvider: StringProvider): List<ListModel> = if (submittedQuery.isBlank()) {
            // No query submitted yet → recent searches, plus a prompt when sparse.
            val historyItems = historyItems()
            if (historyItems.size < HISTORY_CTA_THRESHOLD) {
                historyItems + EmptyStateListModel(
                    icon = Icon.Search,
                    explanation = stringProvider.getString(ChipboxStringId.SEARCH_EMPTY_PROMPT),
                    showCrossOut = false,
                )
            } else {
                historyItems
            }
        } else {
            val results = gameItems(stringProvider) +
                songItems(stringProvider) +
                artistItems(stringProvider)
            results.ifEmpty {
                listOf(
                    EmptyStateListModel(
                        icon = Icon.Search,
                        explanation = stringProvider
                            .getString(ChipboxStringId.SEARCH_NO_RESULTS)
                            .format(submittedQuery),
                        showCrossOut = false,
                    )
                )
            }
        }

    private fun historyItems(): List<ListModel> = history.withStandardErrorAndLoading(
        loadingType = LoadingType.SINGLE_TEXT,
        loadingItemCount = HISTORY_LOADING_COUNT,
        loadingWithHeader = false,
    ) {
        data.map { entry ->
            SearchHistoryListModel(
                dataId = entry.id,
                name = entry.query,
                clickAction = SearchAction.HistoryClicked(entry.query),
                removeAction = SearchAction.HistoryRemoved(entry.id),
            )
        }
    }

    private fun gameItems(stringProvider: StringProvider): List<ListModel> = gameResults.withStandardErrorAndLoading(
            loadingType = LoadingType.SQUARE,
            loadingItemCount = RESULT_LOADING_COUNT,
        ) {
            if (data.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    SectionHeaderListModel(
                        stringProvider.getString(ChipboxStringId.SEARCH_SECTION_GAMES),
                    ),
                ) + data.map { game ->
                    SquareItemListModel(
                        dataId = game.id + ID_OFFSET_GAME,
                        name = game.title,
                        sourceInfo = game.photoUrl,
                        imagePlaceholder = Icon.Album,
                        clickAction = SearchAction.GameClicked(game.id),
                    )
                }
            }
        }

    private fun songItems(stringProvider: StringProvider): List<ListModel> = songResults.withStandardErrorAndLoading(
            loadingType = LoadingType.TEXT_CAPTION_IMAGE,
            loadingItemCount = RESULT_LOADING_COUNT,
        ) {
            if (data.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    SectionHeaderListModel(
                        stringProvider.getString(ChipboxStringId.SEARCH_SECTION_SONGS),
                    ),
                ) + data.map { track ->
                    ImageNameCaptionListModel(
                        dataId = track.id,
                        name = track.title,
                        caption = track.game?.title.orEmpty(),
                        sourceInfo = SourceInfo(info = track.game?.photoUrl),
                        imagePlaceholder = Icon.MusicNote,
                        clickAction = SearchAction.SongClicked(track.game?.id),
                    )
                }
            }
        }

    private fun artistItems(stringProvider: StringProvider): List<ListModel> = artistResults.withStandardErrorAndLoading(
            loadingType = LoadingType.SQUARE,
            loadingItemCount = RESULT_LOADING_COUNT,
        ) {
            if (data.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    SectionHeaderListModel(
                        stringProvider.getString(ChipboxStringId.SEARCH_SECTION_ARTISTS),
                    ),
                ) + data.map { artist ->
                    SquareItemListModel(
                        dataId = artist.id + ID_OFFSET_ARTIST,
                        name = artist.name,
                        sourceInfo = artist.photoUrl,
                        imagePlaceholder = Icon.Person,
                        clickAction = SearchAction.ArtistClicked(artist.id),
                    )
                }
            }
        }

    private companion object {
        const val HISTORY_CTA_THRESHOLD = 5
        const val HISTORY_LOADING_COUNT = 6
        const val RESULT_LOADING_COUNT = 2

        // Keep dataIds unique across sections (game/song/artist ids can overlap).
        const val ID_OFFSET_GAME = 1_000_000_000L
        const val ID_OFFSET_ARTIST = 2_000_000_000L
    }
}
