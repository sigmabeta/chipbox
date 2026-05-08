package net.sigmabeta.chipbox.ui.components.subs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import kotlinx.collections.immutable.toImmutableList
import net.sigmabeta.chipbox.ui.components.LabelCheckboxItem
import net.sigmabeta.chipbox.ui.components.LabelDropdownListItem
import net.sigmabeta.chipbox.ui.components.LabelNoThingyItem
import net.sigmabeta.chipbox.ui.components.LabelRatingListItem
import net.sigmabeta.chipbox.ui.components.LabelValueListItem
import net.sigmabeta.chipbox.ui.components.previews.ChipboxPreview
import net.sigmabeta.chipbox.ui.components.previews.PreviewActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.CheckableListModel
import net.sigmabeta.sage.components.DropdownSettingListModel
import net.sigmabeta.sage.components.LabelRatingStarListModel
import net.sigmabeta.sage.components.LabelValueListModel
import net.sigmabeta.sage.components.SingleTextListModel
import org.junit.Rule
import org.junit.Test

class LabeledThingyPaparazziTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    @Test
    fun light() {
        paparazzi.snapshot {
            ChipboxPreview {
                Box(
                    modifier = Modifier.background(MaterialTheme.colorScheme.background),
                ) {
                    Sample()
                }
            }
        }
    }
}

@Composable
private fun Sample() {
    Column {
        val padding = PaddingValues(horizontal = 16.dp)
        val actionSink = PreviewActionSink { }
        LabelNoThingyItem(
            model = SingleTextListModel(
                dataId = 1234L,
                name = "Paths to the future",
                clickAction = SageAction.Noop,
            ),
            actionSink = actionSink,
            modifier = Modifier,
            padding = padding,
        )

        LabelValueListItem(
            LabelValueListModel(
                "Days which are training days",
                "Every",
                SageAction.Noop,
            ),
            PreviewActionSink {},
            Modifier,
            padding = padding,
        )

        LabelRatingListItem(
            LabelRatingStarListModel(
                "Meatiness of current thing",
                3,
                SageAction.Noop,
            ),
            PreviewActionSink {},
            Modifier,
            padding = padding,
        )

        var isChecked by remember { mutableStateOf(true) }
        LabelCheckboxItem(
            CheckableListModel(
                "someId",
                "Sena seen in action",
                checked = isChecked,
                clickAction = SageAction.Noop,
            ),
            PreviewActionSink { isChecked = !isChecked },
            Modifier,
            padding = padding,
        )

        var selectedPosition by remember { mutableStateOf(3) }
        LabelDropdownListItem(
            model = DropdownSettingListModel(
                "",
                "Who the bus is",
                selectedPosition,
                listOf("Noah", "Lanz", "Taion", "Eunie", "Mio", "Sena").toImmutableList(),
            ) { selectedPosition = it },
            defaultExpansion = false,
            modifier = Modifier,
            padding = padding,
        )
    }
}
