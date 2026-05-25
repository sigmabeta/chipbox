package net.sigmabeta.chipbox.features.browsebyartist

import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.EmptyStateListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.LoadingType
import net.sigmabeta.sage.components.SquareItemListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.list.ColumnType
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

data class BrowseByArtistState(
    val artists: LCE<List<Artist>> = LCE.Uninitialized,
) : ListState() {
    override val columnType: ColumnType = ColumnType.Regular(SQUARE_SIZE_DP)

    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(ChipboxStringId.LIBRARY_BROWSE_BY_ARTIST),
    )

    override fun toListItems(stringProvider: StringProvider): List<ListModel> = artists.withStandardErrorAndLoading(
            loadingType = LoadingType.SQUARE,
            loadingWithHeader = false,
        ) { content(data, stringProvider) }

    private fun content(artists: List<Artist>, stringProvider: StringProvider) = if (artists.isEmpty()) {
        listOf(
            EmptyStateListModel(
                icon = Icon.Person,
                explanation = stringProvider.getString(ChipboxStringId.LIBRARY_BROWSE_BY_ARTIST_EMPTY),
            )
        )
    } else {
        artists.map { artist ->
            SquareItemListModel(
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
    }
}
