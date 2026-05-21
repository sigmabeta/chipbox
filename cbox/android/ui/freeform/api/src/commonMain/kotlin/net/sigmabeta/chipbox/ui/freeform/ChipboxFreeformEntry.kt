package net.sigmabeta.chipbox.ui.freeform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.ui.chrome.LocalTitleBarController
import net.sigmabeta.sage.appcomm.ActionSink

@Composable
fun <Model> ChipboxFreeformEntry(
    viewModel: ChipboxFreeformViewModel<*, Model>,
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (model: Model, actionSink: ActionSink, showDebug: Boolean, modifier: Modifier) -> Unit,
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

    content(state.content, viewModel, showDebug, modifier)
}
