package net.sigmabeta.chipbox.ui.list

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.ui.components.Content
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

    val state by viewModel.uiStateActual.collectAsStateWithLifecycle()
    val showDebug by viewModel.showDebug.collectAsStateWithLifecycle()

    val titleBarController = LocalTitleBarController.current
    LaunchedEffect(state.title) {
        titleBarController.set(state.title)
    }

    val widthClass = rememberWidthClass()
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
            modifier = modifier,
            itemContent = itemContent,
        )
    } else {
        ListScreen(
            state = state,
            actionSink = viewModel,
            showDebug = showDebug,
            sideMargin = sideMargin,
            modifier = modifier,
            itemContent = itemContent,
        )
    }
}

@Composable
private fun rememberWidthClass(): WidthClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp < WIDTH_BREAKPOINT_MEDIUM -> WidthClass.COMPACT
        widthDp < WIDTH_BREAKPOINT_EXPANDED -> WidthClass.MEDIUM
        else -> WidthClass.EXPANDED
    }
}

private val SIDE_MARGIN_DEFAULT = 16.dp
private const val WIDTH_BREAKPOINT_MEDIUM = 600
private const val WIDTH_BREAKPOINT_EXPANDED = 840
