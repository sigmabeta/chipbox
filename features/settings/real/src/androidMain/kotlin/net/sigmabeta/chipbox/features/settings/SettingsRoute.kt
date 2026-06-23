package net.sigmabeta.chipbox.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.metroViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListEntry
import net.sigmabeta.chipbox.features.folderpicker.FolderPicker

/**
 * Android actual — intercepts [ChipboxEvent.PickFolder] and pushes the in-app [FolderPicker]
 * screen (matching the JVM target). Android dropped SAF `OpenDocumentTree` when it moved to
 * raw-path libraries + All Files Access; the bespoke picker enforces the storage permission and
 * commits the chosen path itself.
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
