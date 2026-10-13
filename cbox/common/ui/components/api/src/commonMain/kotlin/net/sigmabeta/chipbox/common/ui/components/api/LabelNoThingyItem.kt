package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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

    val labelColor = animateColorAsState(
        targetValue = if (model.active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onBackground
        },
        label = "LabelNoThingyItem.labelColor",
    )
    // Snapped, not animated: animating FontWeight re-measures the text every frame.
    val fontWeight = if (model.active) FontWeight.Bold else FontWeight.Normal

    LabeledThingy(
        label = model.name,
        thingy = {},
        onClick = { actionSink.sendAction(action) },
        onClickLabel = onClickLabel,
        modifier = modifier,
        padding = padding,
        labelColor = labelColor,
        labelFontWeight = fontWeight,
    )
}
