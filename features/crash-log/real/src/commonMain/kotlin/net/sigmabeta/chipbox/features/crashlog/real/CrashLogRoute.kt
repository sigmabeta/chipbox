package net.sigmabeta.chipbox.features.crashlog.real

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.metroViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListEntry

@Composable
fun CrashLogRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CrashLogViewModel = metroViewModel()
    ChipboxListEntry(viewModel, onEvent, modifier)
}
