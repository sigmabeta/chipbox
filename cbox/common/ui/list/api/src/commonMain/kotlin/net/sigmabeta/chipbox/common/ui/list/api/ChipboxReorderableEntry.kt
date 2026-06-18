package net.sigmabeta.chipbox.common.ui.list.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.common.ui.chrome.api.LocalTitleBarController
import net.sigmabeta.chipbox.common.ui.components.api.Content
import net.sigmabeta.chipbox.common.ui.components.api.DraggableListItem
import net.sigmabeta.chipbox.common.ui.components.api.DraggableListModel
import net.sigmabeta.sage.ui.list.ReorderableScreen

/**
 * Reorderable sibling of [ChipboxListEntry] for screens whose rows can be dragged to reorder
 * (single column only — no width-driven grid). It shares the same event/lifecycle/title-bar
 * plumbing, but renders [ReorderableScreen] with an `itemContent` that appends a drag handle to
 * any [DraggableListModel] via [DraggableListItem] and leaves every other row as-is.
 *
 * Reorder itself needs no wiring here: [ReorderableScreen] emits
 * [net.sigmabeta.sage.appcomm.SageAction.Reorder] to [viewModel] (the [ActionSink]) on drop, so
 * the ViewModel's `handleAction` is the only place that turns a drag into a domain action.
 */
@Composable
fun ChipboxReorderableEntry(
    viewModel: ChipboxListViewModel<*>,
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(viewModel) {
        viewModel.events.collect(onEvent)
    }

    ScreenLifecycleEffect(viewModel)

    val state by viewModel.uiStateActual.collectAsState()
    val showDebug by viewModel.showDebug.collectAsState()

    val titleBarController = LocalTitleBarController.current
    LaunchedEffect(state.title) {
        titleBarController.set(state.title)
    }

    ReorderableScreen(
        state = state,
        actionSink = viewModel,
        showDebug = showDebug,
        sideMargin = SIDE_MARGIN_DEFAULT,
        modifier = modifier,
        itemContent = { model, sink, debug, _, dragHandle, mod, pad ->
            when (model) {
                is DraggableListModel ->
                    DraggableListItem(dragHandle = dragHandle, modifier = mod) {
                        model.content.Content(sink, debug, Modifier, pad)
                    }

                else -> model.Content(sink, debug, mod, pad)
            }
        },
    )
}

private val SIDE_MARGIN_DEFAULT = 16.dp
