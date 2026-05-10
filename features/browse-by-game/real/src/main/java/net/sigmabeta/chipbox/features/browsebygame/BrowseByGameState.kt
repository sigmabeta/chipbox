package net.sigmabeta.chipbox.features.browsebygame

import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.strings.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.LoadingType
import net.sigmabeta.sage.components.SquareItemListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.list.ColumnType
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

data class BrowseByGameState(
    val games: LCE<List<Game>> = LCE.Uninitialized,
) : ListState() {
    override val columnType: ColumnType = ColumnType.Regular(SQUARE_SIZE_DP)

    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(ChipboxStringId.LIBRARY_BROWSE_BY_GAME),
    )

    override fun toListItems(stringProvider: StringProvider): List<ListModel> =
        games.withStandardErrorAndLoading(
            loadingType = LoadingType.SQUARE,
            loadingWithHeader = false,
        ) { content(data) }

    private fun content(games: List<Game>) = games.map { game ->
        SquareItemListModel(
            dataId = game.id,
            name = game.title,
            sourceInfo = game.photoUrl,
            imagePlaceholder = Icon.ALBUM,
            clickAction = BrowseByGameAction.GameClicked(game.id),
        )
    }

    private companion object {
        const val SQUARE_SIZE_DP = 160
    }
}
