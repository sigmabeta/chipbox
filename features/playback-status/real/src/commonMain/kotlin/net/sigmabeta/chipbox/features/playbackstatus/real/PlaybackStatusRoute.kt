package net.sigmabeta.chipbox.features.playbackstatus.real

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.metroViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListEntry

@Composable
fun PlaybackStatusRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PlaybackStatusViewModel = metroViewModel()
    ChipboxListEntry(viewModel, onEvent, modifier)
}
