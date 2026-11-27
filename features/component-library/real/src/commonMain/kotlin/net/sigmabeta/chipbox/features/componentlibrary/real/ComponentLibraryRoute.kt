package net.sigmabeta.chipbox.features.componentlibrary.real

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.metroViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListEntry
import net.sigmabeta.sage.ui.componentlibrary.ComponentLibraryHost

@Composable
fun ComponentLibraryRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ComponentLibraryViewModel = metroViewModel()
    val state by viewModel.uiStateActual.collectAsState()
    // The host reads the live style from the catalog's carrier row and forces preview art.
    ComponentLibraryHost(state.listItems) {
        ChipboxListEntry(viewModel, onEvent, modifier)
    }
}
