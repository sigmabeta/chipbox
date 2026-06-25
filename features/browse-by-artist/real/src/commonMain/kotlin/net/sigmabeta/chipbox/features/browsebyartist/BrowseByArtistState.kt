package net.sigmabeta.chipbox.features.browsebyartist

import net.sigmabeta.chipbox.models.Artist
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

data class BrowseByArtistState(
    val artists: LCE<List<Artist>> = LCE.Uninitialized,
    // Paging window: [windowStart, windowStart + artists.size) within the name-ordered catalog.
    val windowStart: Int = 0,
    val hasMoreBefore: Boolean = false,
    val hasMoreAfter: Boolean = true,
    val loadingPrevious: Boolean = false,
    val loadingMore: Boolean = false,
) : ListState() {
    override val columnType: ColumnType = ColumnType.Regular(SQUARE_SIZE_DP)

    override val paginationType: PaginationType = PaginationType.Standard()

    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(ChipboxStringId.LIBRARY_BROWSE_BY_ARTIST),
    )

    override fun toListItems(stringProvider: StringProvider): List<ListModel> {
        val content = artists.withStandardErrorAndLoading(
            loadingType = LoadingType.SQUARE,
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

    private fun content(artists: List<Artist>, stringProvider: StringProvider) = if (artists.isEmpty()) {
        listOf(
            EmptyStateListModel(
                icon = Icon.Person,
                explanation = stringProvider.getString(ChipboxStringId.LIBRARY_BROWSE_BY_ARTIST_EMPTY),
            )
        )
    } else {
        artists.map { artist ->
            GridImageListModel(
                dataId = artist.id,
                name = artist.name,
                sourceInfo = artist.photoUrl,
                imagePlaceholder = Icon.Person,
                clickAction = BrowseByArtistAction.ArtistClicked(artist.id),
            )
        }
    }

    private companion object {
        const val SQUARE_SIZE_DP = 160

        const val LOAD_PREVIOUS_OP = "browse_by_artist.load_previous"
        const val LOAD_MORE_OP = "browse_by_artist.load_more"
    }
}
