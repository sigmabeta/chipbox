package net.sigmabeta.chipbox.features.browsebyplatform

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.strings.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.EmptyStateListModel
import net.sigmabeta.sage.components.IconNameListModel
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

    override fun toListItems(stringProvider: StringProvider): List<ListModel> = platforms.withStandardErrorAndLoading(
            loadingType = LoadingType.TEXT_CAPTION,
            loadingWithHeader = false,
        ) { content(data, stringProvider) }

    private fun content(platforms: List<Platform>, stringProvider: StringProvider) = if (platforms.isEmpty()) {
            listOf(
                EmptyStateListModel(
                    icon = Icon.Chip,
                    explanation = stringProvider.getString(
                        ChipboxStringId.LIBRARY_BROWSE_BY_PLATFORM_EMPTY,
                    ),
                )
            )
        } else {
            platforms
                .sortedBy { it.ordinal }
                .map { platform ->
                    IconNameListModel(
                        dataId = platform.ordinal.toLong(),
                        name = stringProvider.getString(platform.stringId),
                        icon = platform.icon(),
                        clickAction = BrowseByPlatformAction.PlatformClicked(platform),
                    )
                }
        }

    /**
     * Picks a list icon by storage medium — a disc for CD consoles, a gamepad for
     * anything cartridge/board based (doesn't use CDs), and a computer for PC.
     */
    private fun Platform.icon(): Icon = when (this) {
        Platform.DREAMCAST,
        Platform.PS2,
        Platform.PSX,
        Platform.SATURN -> Icon.Album

        Platform.ARCADE,
        Platform.GAMEBOY,
        Platform.GAMEBOY_ADVANCE,
        Platform.GENESIS,
        Platform.NES,
        Platform.N64,
        Platform.NDS,
        Platform.SNES -> Icon.Chip

        Platform.PC -> Icon.Computer

        Platform.OTHER -> Icon.Album
    }
}
