package net.sigmabeta.chipbox.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.components.SectionListModel


@Composable
@Suppress("MagicNumber")
fun SectionListItem(
    model: SectionListModel,
    actionSink: ActionSink,
    showDebug: Boolean,
    modifier: Modifier,
    padding: PaddingValues,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        model.sectionItems.forEach {
            it.Content(
                sink = actionSink,
                debug = showDebug,
                mod = Modifier,
                pad = padding
            )
        }
    }
}
