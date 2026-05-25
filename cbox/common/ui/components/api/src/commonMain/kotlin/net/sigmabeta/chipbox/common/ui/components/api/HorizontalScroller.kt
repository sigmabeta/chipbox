package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.components.HorizontalScrollerListModel

@Composable
@Suppress("MagicNumber")
fun HorizontalScroller(
    model: HorizontalScrollerListModel,
    actionSink: ActionSink,
    showDebug: Boolean,
    modifier: Modifier,
    padding: PaddingValues,
) {
    LazyRow(
        contentPadding = padding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        items(
            items = model.scrollingItems,
            key = { it.dataId },
            contentType = { it::class.simpleName },
        ) {
            it.Content(
                sink = actionSink,
                debug = showDebug,
                mod = Modifier.animateItem(),
                pad = PaddingValues()
            )
        }
    }
}
