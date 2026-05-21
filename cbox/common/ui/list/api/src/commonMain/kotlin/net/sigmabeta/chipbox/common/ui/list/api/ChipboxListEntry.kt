package net.sigmabeta.chipbox.common.ui.list.api

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.common.ui.chrome.api.LocalTitleBarController
import net.sigmabeta.chipbox.ui.components.api.Content
import net.sigmabeta.sage.android.ui.list.GridScreen
import net.sigmabeta.sage.android.ui.list.ListScreen
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.list.ColumnType
import net.sigmabeta.sage.list.WidthClass

@Composable
fun ChipboxListEntry(
    viewModel: ChipboxListViewModel<*>,
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(viewModel) {
        viewModel.events.collect(onEvent)
    }

    val state by viewModel.uiStateActual.collectAsState()
    val showDebug by viewModel.showDebug.collectAsState()

    val titleBarController = LocalTitleBarController.current
    LaunchedEffect(state.title) {
        titleBarController.set(state.title)
    }

    BoxWithConstraints(modifier = modifier) {
        val widthClass = when {
            maxWidth.value < WIDTH_BREAKPOINT_MEDIUM -> WidthClass.COMPACT
            maxWidth.value < WIDTH_BREAKPOINT_EXPANDED -> WidthClass.MEDIUM
            else -> WidthClass.EXPANDED
        }
        val numColumns = state.columnType.numberOfColumns(widthClass)
        require(numColumns > 0) {
            "numberOfColumns is 0 for ${state.columnType} / $widthClass"
        }

        val sideMargin = SIDE_MARGIN_DEFAULT
        val itemContent: @Composable (ListModel, ActionSink, Boolean, Modifier, PaddingValues) -> Unit =
            { model, sink, debug, mod, pad -> model.Content(sink, debug, mod, pad) }

        if (numColumns > 1) {
            val (staggered, allowHorizScroller) = when (val ct = state.columnType) {
                is ColumnType.Staggered -> true to ct.allowHorizScroller
                else -> false to false
            }
            GridScreen(
                state = state,
                actionSink = viewModel,
                showDebug = showDebug,
                numberOfColumns = numColumns,
                staggered = staggered,
                allowHorizScroller = allowHorizScroller,
                sideMargin = sideMargin,
                modifier = Modifier,
                itemContent = itemContent,
            )
        } else {
            ListScreen(
                state = state,
                actionSink = viewModel,
                showDebug = showDebug,
                sideMargin = sideMargin,
                modifier = Modifier,
                itemContent = itemContent,
            )
        }
    }
}

private val SIDE_MARGIN_DEFAULT = 16.dp
private const val WIDTH_BREAKPOINT_MEDIUM = 600
private const val WIDTH_BREAKPOINT_EXPANDED = 840
