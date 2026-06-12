package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import net.sigmabeta.chipbox.common.ui.components.api.subs.LabeledThingy
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.chipbox.strings.api.text
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.SingleTextListModel

@Composable
fun LabelNoThingyItem(
    model: SingleTextListModel,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    val action = model.clickAction
    val onClickLabel = if (action !is SageAction.Noop) {
        ChipboxStringId.ACCY_OCL_SINGLE_LINE.text(model.name)
    } else {
        null
    }

    val labelColor by animateColorAsState(
        targetValue = if (model.active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onBackground
        },
        label = "LabelNoThingyItem.labelColor",
    )
    val fontWeightValue by animateIntAsState(
        targetValue = if (model.active) FontWeight.Bold.weight else FontWeight.Normal.weight,
        label = "LabelNoThingyItem.fontWeight",
    )

    LabeledThingy(
        label = model.name,
        thingy = {},
        onClick = { actionSink.sendAction(action) },
        onClickLabel = onClickLabel,
        modifier = modifier,
        padding = padding,
        labelColor = labelColor,
        labelFontWeight = FontWeight(fontWeightValue),
    )
}
