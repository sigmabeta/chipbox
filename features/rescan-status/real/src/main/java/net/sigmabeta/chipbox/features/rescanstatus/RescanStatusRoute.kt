package net.sigmabeta.chipbox.features.rescanstatus

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.metroViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListEntry

@Composable
actual fun RescanStatusRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier,
) {
    val viewModel: RescanStatusViewModel = metroViewModel()
    ChipboxListEntry(viewModel, onEvent, modifier)
}
