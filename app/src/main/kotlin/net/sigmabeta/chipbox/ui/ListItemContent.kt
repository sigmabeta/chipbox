package net.sigmabeta.chipbox.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.sigmabeta.chipbox.ui.components.Content
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.components.ListModel

@Composable
fun ListItemContent(
    model: ListModel,
    sink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    model.Content(sink = sink, debug = false, mod = modifier, pad = padding)
}
