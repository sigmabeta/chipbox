package net.sigmabeta.chipbox.features.componentlibrary.real

import net.sigmabeta.chipbox.features.componentlibrary.LibraryMode
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.components.IconNameListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

/** Landing menu for the component gallery: one row per [LibraryMode]. */
class ComponentLibraryState : ListState() {
    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(ChipboxStringId.COMPONENT_LIBRARY_SCREEN_TITLE),
        shouldShowBack = true,
    )

    override fun toListItems(stringProvider: StringProvider): List<ListModel> = listOf(
        option(stringProvider, ChipboxStringId.COMPONENT_LIBRARY_OPTION_LIST, Icon.Description, LibraryMode.LIST),
        option(stringProvider, ChipboxStringId.COMPONENT_LIBRARY_OPTION_GRID, Icon.Album, LibraryMode.GRID),
        option(stringProvider, ChipboxStringId.COMPONENT_LIBRARY_OPTION_COLUMNS, Icon.Browse, LibraryMode.COLUMNS),
    )

    private fun option(
        stringProvider: StringProvider,
        labelId: ChipboxStringId,
        icon: Icon,
        mode: LibraryMode,
    ) = IconNameListModel(
        dataId = mode.ordinal.toLong(),
        name = stringProvider.getString(labelId),
        icon = icon,
        clickAction = ComponentLibraryAction.OpenMode(mode),
    )
}
