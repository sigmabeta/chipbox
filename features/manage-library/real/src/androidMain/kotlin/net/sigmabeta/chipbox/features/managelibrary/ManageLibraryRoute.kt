package net.sigmabeta.chipbox.features.managelibrary

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.metroViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListEntry

@Composable
actual fun ManageLibraryRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier,
) {
    val viewModel: ManageLibraryViewModel = metroViewModel()

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            viewModel.sendAction(ManageLibraryAction.FolderPicked(uri.toString()))
        }
    }

    // PickFolder is screen-local (needs the SAF launcher remembered in this composable),
    // so intercept it here and forward everything else to the host's event sink.
    val routedOnEvent: (ChipboxEvent) -> Unit = { event ->
        when (event) {
            ChipboxEvent.PickFolder -> folderPicker.launch(null)
            else -> onEvent(event)
        }
    }

    ChipboxListEntry(viewModel, routedOnEvent, modifier)
}
