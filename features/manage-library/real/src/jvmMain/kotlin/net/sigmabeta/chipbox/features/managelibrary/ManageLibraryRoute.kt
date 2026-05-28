package net.sigmabeta.chipbox.features.managelibrary

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.metroViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListEntry
import net.sigmabeta.chipbox.features.folderpicker.FolderPicker

/**
 * JVM/desktop actual — intercepts [ChipboxEvent.PickFolder] and pushes the in-app
 * [FolderPicker] screen instead of opening a system dialog. The picker is self-contained: it
 * commits the chosen path to `LibrarySource` and starts a scan itself, so we never see a
 * [ManageLibraryAction.FolderPicked] action come back through this route — that handler is
 * still on the [ManageLibraryViewModel] for the Android SAF flow.
 */
@Composable
actual fun ManageLibraryRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier,
) {
    val viewModel: ManageLibraryViewModel = metroViewModel()

    val routedOnEvent: (ChipboxEvent) -> Unit = { event ->
        when (event) {
            ChipboxEvent.PickFolder -> onEvent(ChipboxEvent.NavigateTo(FolderPicker))
            else -> onEvent(event)
        }
    }

    ChipboxListEntry(viewModel, routedOnEvent, modifier)
}
