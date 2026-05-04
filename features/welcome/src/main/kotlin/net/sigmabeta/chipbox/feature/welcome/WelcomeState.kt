package net.sigmabeta.chipbox.feature.welcome

import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.SectionHeaderListModel
import net.sigmabeta.sage.components.SingleTextListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.ui.StringProvider

data class WelcomeState(
    val placeholder: Unit = Unit,
) : ListState() {
    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = "Chipbox",
        shouldShowBack = false,
    )

    override fun toListItems(stringProvider: StringProvider): List<ListModel> = listOf(
        SectionHeaderListModel(title = "About"),
        SingleTextListModel(
            name = "Welcome to Chipbox — now running on SAGE.",
            clickAction = SageAction.Noop,
            dataId = 1L,
        ),
        SingleTextListModel(
            name = "Bare scaffold; music playback not yet implemented.",
            clickAction = SageAction.Noop,
            dataId = 2L,
        ),
    )
}
