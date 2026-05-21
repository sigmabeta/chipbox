package net.sigmabeta.chipbox.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.sigmabeta.chipbox.ui.components.subs.LabeledThingy
import net.sigmabeta.chipbox.strings.ChipboxStringId
import net.sigmabeta.chipbox.strings.text
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
    LabeledThingy(
        label = model.name,
        thingy = {},
        onClick = { actionSink.sendAction(action) },
        onClickLabel = onClickLabel,
        modifier = modifier,
        padding = padding,
    )
}
