package net.sigmabeta.chipbox.features.componentlibrary.real

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.metroViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListEntry

@Composable
fun ComponentLibraryRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ComponentLibraryViewModel = metroViewModel()
    ChipboxListEntry(viewModel, onEvent, modifier)
}
