package net.sigmabeta.chipbox.features.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.metroViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.ui.list.api.ChipboxListEntry

@Composable
fun LibraryRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LibraryViewModel = metroViewModel()
    ChipboxListEntry(viewModel, onEvent, modifier)
}
