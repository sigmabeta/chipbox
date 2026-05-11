package net.sigmabeta.chipbox.ui.components.subs

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.components.LabelCheckboxItem
import net.sigmabeta.chipbox.ui.components.LabelDropdownListItem
import net.sigmabeta.chipbox.ui.components.LabelNoThingyItem
import net.sigmabeta.chipbox.ui.components.LabelRatingListItem
import net.sigmabeta.chipbox.ui.components.LabelValueListItem
import net.sigmabeta.chipbox.ui.components.previews.PreviewActionSink
import kotlinx.collections.immutable.toImmutableList
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.CheckableListModel
import net.sigmabeta.sage.components.DropdownSettingListModel
import net.sigmabeta.sage.components.LabelRatingStarListModel
import net.sigmabeta.sage.components.LabelValueListModel
import net.sigmabeta.sage.components.SingleTextListModel
import net.sigmabeta.chipbox.ui.components.previews.ChipboxPreview

@Composable
@Suppress("LongParameterList")
fun LabeledThingy(
    label: String,
    thingy: @Composable RowScope.() -> Unit,
    onClick: () -> Unit,
    onClickLabel: String?,
    accyStateDescription: String? = null,
    modifier: Modifier,
    padding: PaddingValues,
    labelColor: Color? = null,
    labelFontWeight: FontWeight? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
                onClickLabel = onClickLabel,
            )
            .padding(padding)
            .semantics {
                accyStateDescription?.let { stateDescription = it }
            },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = labelColor ?: MaterialTheme.colorScheme.onBackground,
            fontWeight = labelFontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(vertical = 16.dp)
                .weight(1.0f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        thingy()
    }
}

@Preview
@Composable
private fun Light() {
    ChipboxPreview {
        Box(
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.background
            )
        ) {
            Sample()
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Dark() {
    ChipboxPreview {
        Box(
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.background
            )
        ) {
            Sample()
        }
    }
}

@Composable
@Suppress("MagicNumber", "LongMethod")
private fun Sample() {
    Column {
        val padding = PaddingValues(horizontal = 16.dp)
        val actionSink = PreviewActionSink { }
        LabelNoThingyItem(
            model = SingleTextListModel(
                dataId = 1234L,
                name = "Paths to the future",
                clickAction = SageAction.Noop
            ),
            actionSink = actionSink,
            modifier = Modifier,
            padding = padding,
        )

        LabelValueListItem(
            LabelValueListModel(
                "Days which are training days",
                "Every",
                SageAction.Noop
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
                clickAction = SageAction.Noop
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
                listOf(
                    "Noah",
                    "Lanz",
                    "Taion",
                    "Eunie",
                    "Mio",
                    "Sena",
                ).toImmutableList()
            ) { selectedPosition = it },
            defaultExpansion = false,
            modifier = Modifier,
            padding = padding,
        )
    }
}
