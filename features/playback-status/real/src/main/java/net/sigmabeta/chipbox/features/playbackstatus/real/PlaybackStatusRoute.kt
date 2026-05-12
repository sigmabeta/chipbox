package net.sigmabeta.chipbox.features.playbackstatus.real

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.ui.list.ChipboxListEntry

@Composable
fun PlaybackStatusRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PlaybackStatusViewModel = hiltViewModel()
    ChipboxListEntry(viewModel, onEvent, modifier)
}
