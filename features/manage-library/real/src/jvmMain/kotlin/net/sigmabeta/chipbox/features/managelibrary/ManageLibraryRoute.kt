package net.sigmabeta.chipbox.features.managelibrary

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.metroViewModel
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListEntry

/**
 * JVM/desktop actual — picks a folder with `javax.swing.JFileChooser` in DIRECTORIES_ONLY mode,
 * deferred via `SwingUtilities.invokeLater` so the dialog's nested EDT pump runs on a fresh
 * event-queue task instead of nesting inside the dispatched coroutine continuation (see the same
 * note on `SettingsRoute`'s JVM actual). `LocalFileContentSource.addLibraryLocation` expects a
 * filesystem path, so the picked `absolutePath` flows straight into `FolderPicked`.
 */
@Composable
actual fun ManageLibraryRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier,
) {
    val viewModel: ManageLibraryViewModel = metroViewModel()

    val routedOnEvent: (ChipboxEvent) -> Unit = { event ->
        when (event) {
            ChipboxEvent.PickFolder -> pickLibraryFolderAsync { path ->
                viewModel.sendAction(ManageLibraryAction.FolderPicked(path))
            }

            else -> onEvent(event)
        }
    }

    ChipboxListEntry(viewModel, routedOnEvent, modifier)
}

private fun pickLibraryFolderAsync(onResult: (String) -> Unit) {
    SwingUtilities.invokeLater {
        val chooser = JFileChooser().apply {
            dialogTitle = "Choose music library folder"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isMultiSelectionEnabled = false
        }
        val approved = chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION
        val path = if (approved) chooser.selectedFile?.absolutePath else null
        if (path != null) onResult(path)
    }
}
