package net.sigmabeta.chipbox.features.home

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.sigmabeta.chipbox.features.home.module.HomeModuleSection
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.HorizontalScrollerListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.LoadingType
import net.sigmabeta.sage.components.SectionHeaderListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.list.ColumnType
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.ui.StringProvider

data class HomeState(
    val sections: ImmutableList<HomeSectionState> = persistentListOf(),
) : ListState() {
    override val columnType: ColumnType = ColumnType.One

    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(ChipboxStringId.APP_NAME),
        shouldShowBack = false,
    )

    override fun toListItems(stringProvider: StringProvider): List<ListModel> =
        sections.flatMap { it.toListItems() }

    private fun HomeSectionState.toListItems(): List<ListModel> = lce.withStandardErrorAndLoading(
        loadingType = LoadingType.COVER,
        loadingItemCount = HOME_LOADING_ITEMS,
        loadingWithHeader = true,
        loadingHorizScrollable = true,
        loadingOperationNameOverride = "home.$id.load",
    ) {
        listOf(
            SectionHeaderListModel(data.title),
            HorizontalScrollerListModel(
                dataId = "home.$id.scroller".hashCode().toLong(),
                scrollingItems = data.items,
            ),
        )
    }
}

data class HomeSectionState(
    val id: String,
    val priority: Int,
    val lce: LCE<HomeModuleSection>,
)

private const val HOME_LOADING_ITEMS = 6
