package net.sigmabeta.chipbox.features.library

import net.sigmabeta.chipbox.strings.ChipboxStringId
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.MenuItemListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.list.ColumnType
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

data object LibraryState : ListState() {
    override val columnType: ColumnType = ColumnType.One

    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(ChipboxStringId.APPUI_TAB_LIBRARY),
        shouldShowBack = false,
    )

    override fun toListItems(stringProvider: StringProvider): List<ListModel> = listOf(
        menuItem(
            stringProvider,
            ChipboxStringId.LIBRARY_BROWSE_BY_GAME,
            Icon.ALBUM,
            LibraryAction.BrowseByGameClicked,
        ),
        menuItem(
            stringProvider,
            ChipboxStringId.LIBRARY_BROWSE_BY_ARTIST,
            Icon.PERSON,
            LibraryAction.BrowseByArtistClicked,
        ),
        menuItem(
            stringProvider,
            ChipboxStringId.LIBRARY_BROWSE_ALL_TRACKS,
            Icon.MUSIC_NOTE,
            LibraryAction.BrowseAllTracksClicked,
        ),
    )

    private fun menuItem(
        stringProvider: StringProvider,
        labelId: ChipboxStringId,
        icon: Icon,
        action: LibraryAction,
    ) = MenuItemListModel(
        name = stringProvider.getString(labelId),
        caption = null,
        icon = icon,
        clickAction = action,
    )
}
