package net.sigmabeta.chipbox.features.browsebygame

import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.EmptyStateListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.LoadingItemListModel
import net.sigmabeta.sage.components.LoadingType
import net.sigmabeta.sage.components.GridImageListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.list.ColumnType
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.list.PaginationType
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

data class BrowseByGameState(
    val games: LCE<List<Game>> = LCE.Uninitialized,
    // Paging window: [windowStart, windowStart + games.size) within the title-ordered catalog.
    val windowStart: Int = 0,
    val hasMoreBefore: Boolean = false,
    val hasMoreAfter: Boolean = true,
    val loadingPrevious: Boolean = false,
    val loadingMore: Boolean = false,
) : ListState() {
    override val columnType: ColumnType = ColumnType.Regular(SQUARE_SIZE_DP)

    override val paginationType: PaginationType = PaginationType.Standard()

    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(ChipboxStringId.LIBRARY_BROWSE_BY_GAME),
    )

    override fun toListItems(stringProvider: StringProvider): List<ListModel> {
        val content = games.withStandardErrorAndLoading(
            loadingType = LoadingType.COVER,
            loadingWithHeader = false,
        ) { content(data, stringProvider) }
        // Inline header/footer spinners shown while paging a non-empty window further up or down.
        return pageLoader(loadingPrevious, LOAD_PREVIOUS_OP) +
            content +
            pageLoader(loadingMore, LOAD_MORE_OP)
    }

    private fun pageLoader(show: Boolean, operationName: String): List<ListModel> {
        if (!show) return emptyList()
        val type = paginationType as? PaginationType.Paginating ?: return emptyList()
        return listOf(
            LoadingItemListModel(
                loadingType = type.loadingType,
                loadOperationName = operationName,
                loadPositionOffset = 0,
            )
        )
    }

    private fun content(games: List<Game>, stringProvider: StringProvider) = if (games.isEmpty()) {
        listOf(
            EmptyStateListModel(
                icon = Icon.Album,
                explanation = stringProvider.getString(ChipboxStringId.LIBRARY_BROWSE_BY_GAME_EMPTY),
            )
        )
    } else {
        games.map { game ->
            GridImageListModel(
                dataId = game.id,
                name = game.title,
                sourceInfo = game.photoUrl,
                imagePlaceholder = Icon.Album,
                clickAction = BrowseByGameAction.GameClicked(game.id),
                aspectRatio = GAME_COVER_ASPECT_RATIO,
            )
        }
    }

    private companion object {
        const val SQUARE_SIZE_DP = 160

        const val LOAD_PREVIOUS_OP = "browse_by_game.load_previous"
        const val LOAD_MORE_OP = "browse_by_game.load_more"

        // 3:4 — IGDB serves covers at 528×704. Kept literal here because the feature
        // layer doesn't depend on the cbox UI module where CoverArtConstants lives.
        const val GAME_COVER_ASPECT_RATIO = 0.75f
    }
}
