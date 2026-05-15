package net.sigmabeta.chipbox.features.browsebyplatform

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.strings.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.EmptyStateListModel
import net.sigmabeta.sage.components.IconNameCaptionListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.LoadingType
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.list.ColumnType
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

data class BrowseByPlatformState(
    val platforms: LCE<List<Platform>> = LCE.Uninitialized,
) : ListState() {
    override val columnType: ColumnType = ColumnType.One

    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(ChipboxStringId.LIBRARY_BROWSE_BY_PLATFORM),
    )

    override fun toListItems(stringProvider: StringProvider): List<ListModel> =
        platforms.withStandardErrorAndLoading(
            loadingType = LoadingType.TEXT_CAPTION,
            loadingWithHeader = false,
        ) { content(data, stringProvider) }

    private fun content(platforms: List<Platform>, stringProvider: StringProvider) =
        if (platforms.isEmpty()) {
            listOf(
                EmptyStateListModel(
                    icon = Icon.Console,
                    explanation = stringProvider.getString(
                        ChipboxStringId.LIBRARY_BROWSE_BY_PLATFORM_EMPTY,
                    ),
                )
            )
        } else {
            platforms
                .sortedBy { it.ordinal }
                .map { platform ->
                    val media = platform.media()
                    IconNameCaptionListModel(
                        dataId = platform.ordinal.toLong(),
                        name = stringProvider.getString(platform.stringId),
                        caption = stringProvider.getString(media.captionId),
                        icon = media.icon,
                        clickAction = BrowseByPlatformAction.PlatformClicked(platform),
                    )
                }
        }

    /**
     * Groups platforms by storage medium purely to pick a list icon/caption — disc
     * consoles, cartridge/board consoles (anything that doesn't use CDs), and computers.
     */
    private enum class Media(val icon: Icon, val captionId: ChipboxStringId) {
        DISC(Icon.Album, ChipboxStringId.PLATFORM_MEDIA_DISC),
        CARTRIDGE(Icon.Console, ChipboxStringId.PLATFORM_MEDIA_CARTRIDGE),
        COMPUTER(Icon.Computer, ChipboxStringId.PLATFORM_MEDIA_COMPUTER),
        OTHER(Icon.Album, ChipboxStringId.PLATFORM_MEDIA_OTHER),
    }

    private fun Platform.media(): Media = when (this) {
        Platform.DREAMCAST,
        Platform.PS2,
        Platform.PSX,
        Platform.SATURN -> Media.DISC

        Platform.ARCADE,
        Platform.GAMEBOY,
        Platform.GAMEBOY_ADVANCE,
        Platform.GENESIS,
        Platform.NES,
        Platform.N64,
        Platform.NDS,
        Platform.SNES -> Media.CARTRIDGE

        Platform.PC -> Media.COMPUTER

        Platform.OTHER -> Media.OTHER
    }
}
