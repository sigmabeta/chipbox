package net.sigmabeta.chipbox.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.metroViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListEntry
import net.sigmabeta.chipbox.features.folderpicker.FolderPicker

/**
 * JVM/desktop actual — intercepts [ChipboxEvent.PickFolder] and pushes the in-app
 * [FolderPicker] screen instead of opening a system dialog. The picker self-handles the
 * add + scan + RescanStatus navigation, so a [SettingsAction.FolderPicked] never comes back
 * through this route (that handler stays on [SettingsViewModel] for the Android SAF flow).
 */
@Composable
actual fun SettingsRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier,
) {
    val viewModel: SettingsViewModel = metroViewModel()

    val routedOnEvent: (ChipboxEvent) -> Unit = { event ->
        when (event) {
            ChipboxEvent.PickFolder -> onEvent(ChipboxEvent.NavigateTo(FolderPicker))
            else -> onEvent(event)
        }
    }

    ChipboxListEntry(viewModel, routedOnEvent, modifier)
}
