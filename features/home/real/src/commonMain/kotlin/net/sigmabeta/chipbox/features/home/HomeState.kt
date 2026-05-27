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
        loadingWithHeader = showHeader,
        loadingHorizScrollable = true,
        loadingOperationNameOverride = "home.$id.load",
    ) {
        // Modules emitting a single item get the full screen width for that item — no carousel
        // wrapping — so a one-of-a-kind tile (e.g. a hero "now playing" card) can stand alone.
        val header: List<ListModel> = if (showHeader) {
            listOf(SectionHeaderListModel(data.title))
        } else {
            emptyList()
        }
        if (data.items.size == 1) {
            header + data.items
        } else {
            header + HorizontalScrollerListModel(
                dataId = "home.$id.scroller".hashCode().toLong(),
                scrollingItems = data.items,
            )
        }
    }
}

data class HomeSectionState(
    val id: String,
    val priority: Int,
    val lce: LCE<HomeModuleSection>,
    val showHeader: Boolean = true,
)

private const val HOME_LOADING_ITEMS = 6
