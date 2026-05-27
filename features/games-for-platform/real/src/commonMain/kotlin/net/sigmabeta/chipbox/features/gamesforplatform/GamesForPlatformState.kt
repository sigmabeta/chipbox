package net.sigmabeta.chipbox.features.gamesforplatform

import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.CtaListModel
import net.sigmabeta.sage.components.EmptyStateListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.LoadingType
import net.sigmabeta.sage.components.GridImageListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.list.ColumnType
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

data class GamesForPlatformState(
    val platform: Platform? = null,
    val games: LCE<List<Game>> = LCE.Uninitialized,
) : ListState() {
    override val columnType: ColumnType = ColumnType.Regular(SQUARE_SIZE_DP)

    override fun title(stringProvider: StringProvider) = if (platform != null) {
        TitleBarModel(
            title = stringProvider.getStringOneArg(
                ChipboxStringId.GAMES_FOR_PLATFORM_TITLE,
                stringProvider.getString(platform.stringId),
            ),
        )
    } else {
        TitleBarModel()
    }

    override fun toListItems(stringProvider: StringProvider): List<ListModel> = games.withStandardErrorAndLoading(
            loadingType = LoadingType.COVER,
            loadingWithHeader = false,
        ) { content(data, stringProvider) }

    private fun content(games: List<Game>, stringProvider: StringProvider) = if (games.isEmpty()) {
        listOf(
            EmptyStateListModel(
                icon = Icon.Album,
                explanation = stringProvider.getString(ChipboxStringId.GAMES_FOR_PLATFORM_EMPTY),
            )
        )
    } else {
        listOf(
            CtaListModel(
                icon = Icon.MusicNote,
                name = stringProvider.getString(ChipboxStringId.GAMES_FOR_PLATFORM_CTA_PLAY_ALL),
                clickAction = GamesForPlatformAction.PlayAllClicked,
            ),
            CtaListModel(
                icon = Icon.Shuffle,
                name = stringProvider.getString(ChipboxStringId.GAMES_FOR_PLATFORM_CTA_SHUFFLE_ALL),
                clickAction = GamesForPlatformAction.ShuffleAllClicked,
            ),
        ) + games.map { game ->
            GridImageListModel(
                dataId = game.id,
                name = game.title,
                sourceInfo = game.photoUrl,
                imagePlaceholder = Icon.Album,
                clickAction = GamesForPlatformAction.GameClicked(game.id),
                aspectRatio = GAME_COVER_ASPECT_RATIO,
            )
        }
    }

    private companion object {
        const val SQUARE_SIZE_DP = 160

        // 3:4 — IGDB serves covers at 528×704. Kept literal here because the feature
        // layer doesn't depend on the cbox UI module where CoverArtConstants lives.
        const val GAME_COVER_ASPECT_RATIO = 0.75f
    }
}
