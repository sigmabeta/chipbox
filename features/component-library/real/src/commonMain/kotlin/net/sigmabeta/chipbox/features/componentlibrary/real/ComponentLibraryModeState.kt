package net.sigmabeta.chipbox.features.componentlibrary.real

import kotlinx.collections.immutable.toImmutableList
import net.sigmabeta.chipbox.features.componentlibrary.LibraryMode
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.CheckableListModel
import net.sigmabeta.sage.components.ConfirmationListModel
import net.sigmabeta.sage.components.CtaListModel
import net.sigmabeta.sage.components.DropdownSettingListModel
import net.sigmabeta.sage.components.EditTextListModel
import net.sigmabeta.sage.components.EmptyStateListModel
import net.sigmabeta.sage.components.GridImageListModel
import net.sigmabeta.sage.components.HeroImageListModel
import net.sigmabeta.sage.components.HorizontalScrollerListModel
import net.sigmabeta.sage.components.IconNameCaptionListModel
import net.sigmabeta.sage.components.IconNameListModel
import net.sigmabeta.sage.components.ImageNameCaptionListModel
import net.sigmabeta.sage.components.ImageNameListModel
import net.sigmabeta.sage.components.LabelRatingStarListModel
import net.sigmabeta.sage.components.LabelValueListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.NameCaptionListModel
import net.sigmabeta.sage.components.NameCaptionValueListModel
import net.sigmabeta.sage.components.NotifListModel
import net.sigmabeta.sage.components.SectionHeaderListModel
import net.sigmabeta.sage.components.SingleTextListModel
import net.sigmabeta.sage.components.SubsectionHeaderListModel
import net.sigmabeta.sage.components.SubsectionListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.components.WideItemListModel
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.list.ColumnType
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

/**
 * Renders the component gallery for a single [mode]. The [mode] drives [columnType], which is the
 * existing mechanism the list host uses to pick a renderer:
 * - [LibraryMode.LIST] -> [ColumnType.One] (single-column `ListScreen`).
 * - [LibraryMode.GRID] -> [ColumnType.Regular] (multi-column `LazyVerticalGrid`).
 * - [LibraryMode.COLUMNS] -> [ColumnType.Staggered] (the detail screens' staggered grid: one column
 *   on phones, two when wider).
 *
 * [content] is null until the ViewModel fills it in `init`; an empty list renders meanwhile.
 */
data class ComponentLibraryModeState(
    val mode: LibraryMode? = null,
    val content: SampleContent? = null,
    // settingId of the single currently-expanded dropdown, or null if collapsed.
    val expandedDropdownId: String? = null,
) : ListState() {

    override val columnType: ColumnType = when (mode) {
        LibraryMode.GRID -> ColumnType.Regular(GRID_CELL_WIDTH_DP)
        LibraryMode.COLUMNS -> ColumnType.Staggered(COLUMN_WIDTH_DP, allowHorizScroller = false)
        LibraryMode.LIST, null -> ColumnType.One
    }

    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(titleId()),
        shouldShowBack = true,
    )

    private fun titleId(): ChipboxStringId = when (mode) {
        LibraryMode.LIST -> ChipboxStringId.COMPONENT_LIBRARY_OPTION_LIST
        LibraryMode.GRID -> ChipboxStringId.COMPONENT_LIBRARY_OPTION_GRID
        LibraryMode.COLUMNS -> ChipboxStringId.COMPONENT_LIBRARY_OPTION_COLUMNS
        null -> ChipboxStringId.COMPONENT_LIBRARY_SCREEN_TITLE
    }

    override fun toListItems(stringProvider: StringProvider): List<ListModel> {
        val content = content ?: return emptyList()
        return when (mode) {
            LibraryMode.LIST -> listItems(content)
            LibraryMode.GRID -> gridItems(content)
            LibraryMode.COLUMNS -> columnItems(content)
            null -> emptyList()
        }
    }

    // Full-width, single-column showcase: one of (most) every component, plus a horizontal
    // scroller of grid items at the end.
    private fun listItems(content: SampleContent): List<ListModel> = buildList {
        add(SectionHeaderListModel("HeroImage / ImageName*"))
        add(
            HeroImageListModel(
                sourceInfo = SourceInfo("$IMAGE_SEED/hero"),
                contentDescription = content.title(0),
                imagePlaceholder = Icon.Album,
                clickAction = SageAction.Noop,
            )
        )
        add(
            ImageNameCaptionListModel(
                dataId = id(0),
                name = content.name(0),
                caption = content.caption(0),
                sourceInfo = SourceInfo("$IMAGE_SEED/inc/0"),
                imagePlaceholder = Icon.Person,
                clickAction = SageAction.Noop,
            )
        )
        add(
            ImageNameListModel(
                dataId = id(1),
                name = content.name(1),
                sourceInfo = SourceInfo("$IMAGE_SEED/in/1"),
                imagePlaceholder = Icon.Person,
                clickAction = SageAction.Noop,
            )
        )

        add(SectionHeaderListModel("Icon / text rows"))
        add(IconNameCaptionListModel(id(2), content.title(1), content.caption(1), Icon.MusicNote, SageAction.Noop))
        add(IconNameListModel(id(3), content.title(2), Icon.Tag, SageAction.Noop))
        add(NameCaptionListModel(id(4), content.title(3), content.caption(2), SageAction.Noop))
        add(NameCaptionValueListModel(id(5), content.title(4), content.caption(3), "42", SageAction.Noop))
        add(LabelValueListModel(label = content.title(5), value = content.name(2), clickAction = SageAction.Noop, dataId = id(10)))
        add(SingleTextListModel(name = content.title(6), clickAction = SageAction.Noop, dataId = id(11)))

        add(SectionHeaderListModel("Setting controls"))
        add(
            CheckableListModel(
                settingId = "cl-check",
                name = content.title(7),
                checked = true,
                clickAction = SageAction.Noop,
            )
        )
        add(LabelRatingStarListModel(label = content.title(8), value = STAR_RATING, clickAction = SageAction.Noop, dataId = id(12)))
        add(
            DropdownSettingListModel.ofLabels(
                settingId = DROPDOWN_SETTING_ID,
                name = content.title(9),
                selectedPosition = 0,
                labels = content.titles.take(DROPDOWN_OPTION_COUNT).toImmutableList(),
                expanded = expandedDropdownId == DROPDOWN_SETTING_ID,
                onExpandClicked = ComponentLibraryAction.DropdownExpandClicked(DROPDOWN_SETTING_ID),
            )
        )
        add(CtaListModel(icon = Icon.Search, name = content.title(10), clickAction = SageAction.Noop, dataId = id(13)))
        add(EditTextListModel(id = id(14), header = content.title(11), hint = content.title(12), allowEmpty = false))
        add(ConfirmationListModel(id = id(15), header = content.title(0), bodyText = content.caption(0), confirmLabel = content.title(1)))

        add(SectionHeaderListModel("WideItem / Notif"))
        add(
            WideItemListModel(
                dataId = id(6),
                name = content.name(3),
                sourceInfo = "$IMAGE_SEED/wide/0",
                imagePlaceholder = Icon.Person,
                clickAction = SageAction.Noop,
            )
        )
        add(
            NotifListModel(
                dataId = id(7),
                title = content.title(11),
                description = content.caption(4),
                actionLabel = "OK",
                action = SageAction.Noop,
                isError = false,
            )
        )
        add(
            NotifListModel(
                dataId = id(8),
                title = content.name(4),
                description = content.caption(5),
                actionLabel = "Retry",
                action = SageAction.Noop,
                isError = true,
            )
        )

        add(SectionHeaderListModel("HorizontalScroller (grid items)"))
        add(
            HorizontalScrollerListModel(
                dataId = id(9),
                scrollingItems = gridCells(content, "$IMAGE_SEED/scroll", SCROLLER_CELL_COUNT, Icon.Album),
            )
        )

        add(
            EmptyStateListModel(
                icon = Icon.Warning,
                explanation = content.caption(6),
                showCrossOut = false,
            )
        )
    }

    // Multi-column grid of square cells, with a full-width header and a full-width notif mixed in
    // to show that COLUMNS_ALL items still span the whole row inside the grid.
    private fun gridItems(content: SampleContent): List<ListModel> = buildList {
        add(SectionHeaderListModel("Grid cells"))
        addAll(gridCells(content, "$IMAGE_SEED/grid", GRID_CELL_COUNT, Icon.Album))
        add(
            NotifListModel(
                dataId = id(0),
                title = content.title(0),
                description = content.caption(0),
                actionLabel = "OK",
                action = SageAction.Noop,
                isError = false,
            )
        )
        addAll(gridCells(content, "$IMAGE_SEED/grid2", GRID_CELL_COUNT, Icon.Person))
    }

    // Staggered layout matching the detail screens (one lane on a phone, two when wider): a
    // full-width header, a Subsection (itself a 2-up flow of grid items), a horizontal scroller,
    // then wide items + grid cells that tile into the available lanes.
    private fun columnItems(content: SampleContent): List<ListModel> = buildList {
        add(SectionHeaderListModel("Detail-style columns"))
        add(CtaListModel(icon = Icon.MusicNote, name = content.title(0), clickAction = SageAction.Noop, dataId = id(2)))
        add(
            SubsectionListModel(
                id = id(0),
                titleModel = SubsectionHeaderListModel(content.title(1)),
                children = gridCells(content, "$IMAGE_SEED/sub", SUBSECTION_CELL_COUNT, Icon.Person),
            )
        )
        add(
            HorizontalScrollerListModel(
                dataId = id(1),
                scrollingItems = gridCells(content, "$IMAGE_SEED/colscroll", SCROLLER_CELL_COUNT, Icon.Album),
            )
        )
        repeat(COLUMN_WIDE_COUNT) { i ->
            add(
                WideItemListModel(
                    dataId = id(WIDE_ID_BASE + i),
                    name = content.name(i),
                    sourceInfo = "$IMAGE_SEED/colwide/$i",
                    imagePlaceholder = Icon.Person,
                    clickAction = SageAction.Noop,
                )
            )
        }
        addAll(gridCells(content, "$IMAGE_SEED/colgrid", GRID_CELL_COUNT, Icon.Album))
    }

    private fun gridCells(content: SampleContent, seed: String, count: Int, placeholder: Icon) =
        List(count) { i ->
            GridImageListModel(
                dataId = "$seed/$i".hashCode().toLong(),
                name = content.title(i),
                sourceInfo = "$seed/$i",
                imagePlaceholder = placeholder,
                clickAction = SageAction.Noop,
            )
        }.toImmutableList()

    // Stable, locally-unique dataIds for the explicitly-keyed rows. Offset well clear of the
    // hash-derived ids the image cells use.
    private fun id(offset: Int): Long = ID_BASE + offset

    private companion object {
        const val GRID_CELL_WIDTH_DP = 160

        // Same staggered width the detail screens use (GameDetail / ArtistDetail): one column on a
        // phone, two on a tablet/desktop. Matches their layout rather than forcing two columns.
        const val COLUMN_WIDTH_DP = 320
        const val GRID_CELL_COUNT = 6
        const val SCROLLER_CELL_COUNT = 8
        const val SUBSECTION_CELL_COUNT = 4
        const val COLUMN_WIDE_COUNT = 4
        const val DROPDOWN_OPTION_COUNT = 6
        const val DROPDOWN_SETTING_ID = "cl-dropdown"
        const val STAR_RATING = 3
        const val ID_BASE = 1_000_000L
        const val WIDE_ID_BASE = 100
        const val IMAGE_SEED = "component-library"
    }
}
