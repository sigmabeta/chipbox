package net.sigmabeta.chipbox.features.home

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.sigmabeta.chipbox.features.home.module.HomeModuleSection
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.CtaListModel
import net.sigmabeta.sage.components.EmptyStateListModel
import net.sigmabeta.sage.components.HorizontalScrollerListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.LoadingType
import net.sigmabeta.sage.components.SectionHeaderListModel
import net.sigmabeta.sage.components.SectionListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.list.ColumnType
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

data class HomeState(
    val sections: ImmutableList<HomeSectionState> = persistentListOf(),
    // How many folders the user has added to their library. Drives the first-run empty state's
    // copy: 0 → "add your first folder", >0 → "your folders turned up nothing, add another".
    val libraryFolderCount: Int = 0,
    // Whether the library database holds any tracks. `null` until the first probe settles, so the
    // first-run empty state never flashes before we know. `false` → show the empty state outright
    // (independent of what the content modules are doing); `true`/`null` → render the modules.
    val hasTracks: Boolean? = null,
) : ListState() {
    override val columnType: ColumnType = ColumnType.One

    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(ChipboxStringId.APP_NAME),
        shouldShowBack = false,
    )

    // The first-run empty state is gated purely on the library being empty (no tracks in the DB),
    // not on whether the content modules happen to have anything to show. With tracks present we
    // always render the modules (an errored module still shows its own inline error card).
    override fun toListItems(stringProvider: StringProvider): List<ListModel> {
        if (hasTracks == false) {
            val error = sections.firstNotNullOfOrNull { it.lce as? LCE.Error }
            return emptyStateItems(stringProvider, error)
        }
        return sections.flatMap { it.toListItems() }
    }

    // Wrap the empty-state copy + CTAs in a single non-unrolled section capped to
    // [EMPTY_STATE_MAX_WIDTH_DP] so it stays centred and readable on wide screens.
    private fun emptyStateItems(stringProvider: StringProvider, error: LCE.Error?): List<ListModel> =
        listOf(
            SectionListModel(
                dataId = EMPTY_STATE_SECTION_ID,
                columns = ListModel.COLUMNS_ALL,
                dontUnroll = true,
                maxContentWidthDp = EMPTY_STATE_MAX_WIDTH_DP,
                backgroundContainer = true,
                sectionItems = emptyStateContent(stringProvider, error).toImmutableList(),
            ),
        )

    private fun emptyStateContent(stringProvider: StringProvider, error: LCE.Error?): List<ListModel> {
        if (error != null) {
            return listOf(
                EmptyStateListModel(
                    icon = Icon.Warning,
                    explanation = stringProvider.getString(ChipboxStringId.HOME_ERROR),
                    debugText = error.error.message,
                ),
            )
        }
        val hasFolders = libraryFolderCount > 0
        val explanation = if (hasFolders) {
            ChipboxStringId.HOME_EMPTY_FOLDERS_EMPTY
        } else {
            ChipboxStringId.HOME_EMPTY_NO_FOLDERS
        }
        val ctas = buildList {
            // When folders exist but turned up nothing, a rescan is the likeliest fix (files added
            // after the last scan), so offer it first; adding another folder is the fallback.
            if (hasFolders) {
                add(
                    CtaListModel(
                        icon = Icon.Refresh,
                        name = stringProvider.getString(ChipboxStringId.HOME_EMPTY_RESCAN_CTA),
                        clickAction = HomeAction.RescanLibraryClicked,
                    ),
                )
            }
            add(
                CtaListModel(
                    icon = Icon.Plus,
                    name = stringProvider.getString(ChipboxStringId.HOME_EMPTY_ADD_FOLDER_CTA),
                    clickAction = HomeAction.AddFolderClicked,
                ),
            )
        }
        return listOf(
            EmptyStateListModel(
                icon = Icon.Library,
                explanation = stringProvider.getString(explanation),
                showCrossOut = false,
            ),
        ) + ctas
    }

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
private const val EMPTY_STATE_MAX_WIDTH_DP = 512
private val EMPTY_STATE_SECTION_ID = "home.empty_state".hashCode().toLong()
