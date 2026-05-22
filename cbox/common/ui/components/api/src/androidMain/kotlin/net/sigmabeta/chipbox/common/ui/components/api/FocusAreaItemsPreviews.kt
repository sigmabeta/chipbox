package net.sigmabeta.chipbox.common.ui.components.api

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toImmutableList
import net.sigmabeta.chipbox.common.ui.components.api.previews.ChipboxPreview
import net.sigmabeta.chipbox.common.ui.components.api.previews.PreviewActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.CheckableListModel
import net.sigmabeta.sage.components.CtaListModel
import net.sigmabeta.sage.components.DropdownSettingListModel
import net.sigmabeta.sage.components.IconNameCaptionListModel
import net.sigmabeta.sage.components.IconNameListModel
import net.sigmabeta.sage.components.ImageNameCaptionListModel
import net.sigmabeta.sage.components.ImageNameListModel
import net.sigmabeta.sage.components.LabelRatingStarListModel
import net.sigmabeta.sage.components.LabelValueListModel
import net.sigmabeta.sage.components.NameCaptionListModel
import net.sigmabeta.sage.components.NameCaptionValueListModel
import net.sigmabeta.sage.components.SearchHistoryListModel
import net.sigmabeta.sage.components.SingleTextListModel
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.ui.Icon

@Preview
@Composable
private fun Light() {
    ChipboxPreview {
        Showcase()
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Dark() {
    ChipboxPreview {
        Showcase()
    }
}

/**
 * One of every clickable list row that adopted the split-padding focus area (see
 * [net.sigmabeta.chipbox.common.ui.components.api.utils.outerFocusPadding]). All share the same
 * 16.dp horizontal padding so the inset highlight reads consistently across rows.
 */
@Composable
@Suppress("MagicNumber", "LongMethod")
private fun Showcase() {
    val sink = PreviewActionSink { }
    val padding = PaddingValues(horizontal = 16.dp)

    Box(
        modifier = Modifier.background(color = MaterialTheme.colorScheme.background),
    ) {
        Column {
            IconNameListItem(
                model = IconNameListModel(
                    dataId = 2L,
                    name = "Nintendo Entertainment System",
                    icon = Icon.Chip,
                    clickAction = SageAction.Noop,
                    active = false,
                ),
                actionSink = sink,
                modifier = Modifier,
                padding = padding,
            )

            IconNameCaptionListItem(
                model = IconNameCaptionListModel(
                    dataId = 3L,
                    name = "Moebius Battle",
                    caption = "ACE+",
                    icon = Icon.Description,
                    clickAction = SageAction.Noop,
                    active = false,
                ),
                actionSink = sink,
                modifier = Modifier,
                padding = padding,
            )

            ImageNameListItem(
                model = ImageNameListModel(
                    dataId = 4L,
                    name = "Carrying the Weight of Life",
                    sourceInfo = SourceInfo("https://randomfox.ca/images/12.jpg"),
                    imagePlaceholder = Icon.Description,
                    actionableId = null,
                    clickAction = SageAction.Noop,
                    active = false,
                ),
                actionSink = sink,
                modifier = Modifier,
                padding = padding,
            )

            ImageNameCaptionListItem(
                model = ImageNameCaptionListModel(
                    dataId = 5L,
                    name = "Xenoblade Chronicles 3",
                    caption = "Yasunori Mitsuda, Mariam Abounnasr, Manami Kiyota, ACE+",
                    sourceInfo = SourceInfo("https://randomfox.ca/images/12.jpg"),
                    imagePlaceholder = Icon.Person,
                    actionableId = null,
                    clickAction = SageAction.Noop,
                    active = false,
                ),
                actionSink = sink,
                modifier = Modifier,
                padding = padding,
            )

            ActionItem(
                model = CtaListModel(Icon.FavoriteEmpty, "Find a path to the future", SageAction.Noop),
                actionSink = sink,
                modifier = Modifier,
                padding = padding,
            )

            NameCaptionListItem(
                model = NameCaptionListModel(
                    dataId = 6L,
                    name = "Xenoblade Chronicles 3",
                    caption = "Yasunori Mitsuda, Mariam Abounnasr, Manami Kiyota, ACE+",
                    clickAction = SageAction.Noop,
                    active = false,
                ),
                actionSink = sink,
                modifier = Modifier,
                padding = padding,
            )

            NameCaptionValueListItem(
                model = NameCaptionValueListModel(
                    dataId = 7L,
                    name = "The Super Shinobi",
                    caption = "Yuzo Koshiro",
                    value = "2:18",
                    clickAction = SageAction.Noop,
                    active = false,
                ),
                actionSink = sink,
                modifier = Modifier,
                padding = padding,
            )

            SearchHistoryListItem(
                model = SearchHistoryListModel(1L, "Stickerbush Symphony", SageAction.Noop, SageAction.Noop),
                actionSink = sink,
                modifier = Modifier,
                padding = padding,
            )

            // LabeledThingy consumers — all render through the same shared focus-area helper.
            LabelValueListItem(
                model = LabelValueListModel(
                    label = "App version",
                    value = "9.0.0",
                    clickAction = SageAction.Noop,
                    active = false,
                ),
                actionSink = sink,
                modifier = Modifier,
                padding = padding,
            )

            LabelCheckboxItem(
                model = CheckableListModel(
                    settingId = "",
                    name = "Sena seen in action",
                    checked = true,
                    clickAction = SageAction.Noop,
                ),
                actionSink = sink,
                modifier = Modifier,
                padding = padding,
            )

            LabelDropdownListItem(
                model = DropdownSettingListModel(
                    settingId = "",
                    name = "Who the bus is",
                    selectedPosition = 3,
                    settingsLabels = listOf("Noah", "Lanz", "Taion", "Eunie", "Mio", "Sena").toImmutableList(),
                ),
                actionSink = sink,
                defaultExpansion = false,
                modifier = Modifier,
                padding = padding,
            )

            LabelRatingListItem(
                model = LabelRatingStarListModel("Days which are training days", 3, SageAction.Noop),
                actionSink = sink,
                modifier = Modifier,
                padding = padding,
            )

            LabelNoThingyItem(
                model = SingleTextListModel(name = "Open source licenses", clickAction = SageAction.Noop),
                actionSink = sink,
                modifier = Modifier,
                padding = padding,
            )
        }
    }
}
