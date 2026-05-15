package net.sigmabeta.chipbox.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.sigmabeta.sage.android.perf.DURATION_THRESHOLD_ERROR_COMPONENT_DEVICE
import net.sigmabeta.sage.android.perf.DURATION_THRESHOLD_WARNING_COMPONENT_DEVICE
import net.sigmabeta.sage.android.perf.WithMeasurementComponent
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.components.CheckableListModel
import net.sigmabeta.sage.components.CollapsibleDetailsListModel
import net.sigmabeta.sage.components.CtaListModel
import net.sigmabeta.sage.components.DropdownSettingListModel
import net.sigmabeta.sage.components.EmptyStateListModel
import net.sigmabeta.sage.components.ErrorStateListModel
import net.sigmabeta.sage.components.HeroImageListModel
import net.sigmabeta.sage.components.HorizontalScrollerListModel
import net.sigmabeta.sage.components.IconNameCaptionListModel
import net.sigmabeta.sage.components.IconNameListModel
import net.sigmabeta.sage.components.ImageNameCaptionListModel
import net.sigmabeta.sage.components.ImageNameListModel
import net.sigmabeta.sage.components.LabelRatingStarListModel
import net.sigmabeta.sage.components.LabelValueListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.LoadingItemListModel
import net.sigmabeta.sage.components.LoadingType
import net.sigmabeta.sage.components.MenuItemListModel
import net.sigmabeta.sage.components.NameCaptionListModel
import net.sigmabeta.sage.components.NameCaptionValueListModel
import net.sigmabeta.sage.components.NotifListModel
import net.sigmabeta.sage.components.SearchHistoryListModel
import net.sigmabeta.sage.components.SearchResultListModel
import net.sigmabeta.sage.components.SectionHeaderListModel
import net.sigmabeta.sage.components.SectionListModel
import net.sigmabeta.sage.components.SingleTextListModel
import net.sigmabeta.sage.components.SmallTextListModel
import net.sigmabeta.sage.components.SquareItemListModel
import net.sigmabeta.sage.components.SubsectionHeaderListModel
import net.sigmabeta.sage.components.SubsectionListModel
import net.sigmabeta.sage.components.WideItemListModel

@Suppress("MaxLineLength")
@Composable
fun ListModel.Content(
    sink: ActionSink,
    debug: Boolean,
    mod: Modifier,
    pad: PaddingValues,
) {
    WithMeasurementComponent(
        this.javaClass.simpleName,
        DURATION_THRESHOLD_WARNING_COMPONENT_DEVICE,
        DURATION_THRESHOLD_ERROR_COMPONENT_DEVICE,
    ) {
        when (this) {
            is CheckableListModel -> LabelCheckboxItem(model = this, actionSink = sink, modifier = mod, padding = pad)

            is CollapsibleDetailsListModel -> CollapsibleDetailsListItem(model = this, modifier = mod, padding = pad)

            is CtaListModel -> ActionItem(model = this, actionSink = sink, modifier = mod, padding = pad)

            is DropdownSettingListModel -> LabelDropdownListItem(model = this, modifier = mod, padding = pad)

            is EmptyStateListModel -> EmptyListIndicator(model = this, modifier = mod)

            is ErrorStateListModel -> EmptyListIndicator(model = this, showDebug = debug, modifier = mod)

            is HeroImageListModel -> BigImage(model = this, actionSink = sink, modifier = mod, padding = pad)

            is HorizontalScrollerListModel -> HorizontalScroller(model = this, actionSink = sink, showDebug = debug, modifier = mod, padding = pad)

            is IconNameCaptionListModel -> IconNameCaptionListItem(model = this, actionSink = sink, modifier = mod, padding = pad)

            is IconNameListModel -> IconNameListItem(model = this, actionSink = sink, modifier = mod, padding = pad)

            is ImageNameCaptionListModel -> ImageNameCaptionListItem(model = this, actionSink = sink, modifier = mod, padding = pad)

            is ImageNameListModel -> ImageNameListItem(model = this, actionSink = sink, modifier = mod, padding = pad)

            is LabelRatingStarListModel -> LabelRatingListItem(model = this, actionSink = sink, modifier = mod, padding = pad)

            is LabelValueListModel -> LabelValueListItem(model = this, actionSink = sink, modifier = mod, padding = pad)

            is MenuItemListModel -> MenuItem(model = this, actionSink = sink, modifier = mod, padding = pad)

            is NameCaptionListModel -> NameCaptionListItem(model = this, actionSink = sink, modifier = mod, padding = pad)

            is NameCaptionValueListModel -> NameCaptionValueListItem(model = this, actionSink = sink, modifier = mod, padding = pad)

            is NotifListModel -> NotifListItem(model = this, actionSink = sink, modifier = mod)

            is SearchResultListModel -> ImageNameCaptionListItem(model = this, actionSink = sink, modifier = mod, padding = pad)

            is SectionListModel -> SectionListItem(model = this, actionSink = sink, showDebug = debug, modifier = mod, padding = pad)

            is SectionHeaderListModel -> SectionHeader(name = title, modifier = mod, padding = pad)

            is SearchHistoryListModel -> SearchHistoryListItem(model = this, modifier = mod, actionSink = sink, padding = pad)

            is SingleTextListModel -> LabelNoThingyItem(model = this, actionSink = sink, modifier = mod, padding = pad)

            is SmallTextListModel -> SmallText(model = this, actionSink = sink, modifier = mod, padding = pad)

            is SquareItemListModel -> SquareItem(model = this, actionSink = sink, modifier = mod, padding = pad)

            is SubsectionHeaderListModel -> SubsectionHeader(model = this, modifier = mod)

            is SubsectionListModel -> Subsection(model = this, actionSink = sink, modifier = mod, padding = pad)

            is WideItemListModel -> WideItem(model = this, actionSink = sink, modifier = mod, padding = pad)

            is LoadingItemListModel -> {
                when (loadingType) {
                    LoadingType.PAGE, LoadingType.SQUARE, LoadingType.NOTIF, LoadingType.WIDE_ITEM, LoadingType.BIG_IMAGE -> LoadingItem(
                        seed = dataId,
                        loadingType = loadingType,
                        modifier = mod,
                        padding = pad
                    )

                    LoadingType.SECTION_HEADER -> LoadingSectionHeader(seed = dataId, modifier = mod, padding = pad)

                    else -> LoadingTextItem(seed = dataId, loadingType = loadingType, modifier = mod, padding = pad)
                }
            }

            else -> Spacer(modifier = mod)
        }
    }
}
