package net.sigmabeta.chipbox.features.managelibrary

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.metroViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListEntry
import net.sigmabeta.chipbox.features.folderpicker.FolderPicker

/**
 * Android actual — intercepts [ChipboxEvent.PickFolder] and pushes the in-app [FolderPicker]
 * screen (the same flow the JVM target uses). Android dropped the SAF `OpenDocumentTree` picker
 * when it moved to raw-path libraries + All Files Access; the bespoke picker enforces the storage
 * permission itself and commits the chosen path to `LibrarySource`, so no
 * [ManageLibraryAction.FolderPicked] comes back through this route.
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
