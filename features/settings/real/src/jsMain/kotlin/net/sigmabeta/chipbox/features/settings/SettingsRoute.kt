package net.sigmabeta.chipbox.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.metroViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListEntry

// JS is an enforcement-only purity target with no folder picker (and no runtime DI graph), so the
// actual just wires the view model + list entry and forwards every event unchanged.
@Composable
actual fun SettingsRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier,
) {
    val viewModel: SettingsViewModel = metroViewModel()
    ChipboxListEntry(viewModel, onEvent, modifier)
}
