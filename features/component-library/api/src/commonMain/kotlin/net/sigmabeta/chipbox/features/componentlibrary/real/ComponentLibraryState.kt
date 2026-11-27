package net.sigmabeta.chipbox.features.componentlibrary.real

import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.ui.StringProvider
import net.sigmabeta.sage.ui.componentlibrary.ComponentLibraryConfig
import net.sigmabeta.sage.ui.componentlibrary.componentLibraryItems

/**
 * The component gallery: SAGE's catalog for [config], followed by Chipbox's own cards
 * ([chipboxSampleCards]) under the catalog's "App rows" header.
 */
data class ComponentLibraryState(
    val config: ComponentLibraryConfig = ComponentLibraryConfig(),
) : ListState() {
    override val columnType = config.layout.columnType

    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(ChipboxStringId.COMPONENT_LIBRARY_SCREEN_TITLE),
        shouldShowBack = true,
    )

    override fun toListItems(stringProvider: StringProvider): List<ListModel> =
        componentLibraryItems(config, extraSamples = chipboxSampleCards())
}
