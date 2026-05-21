package net.sigmabeta.chipbox.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.metroViewModel
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.ui.list.ChipboxListEntry

/**
 * JVM/desktop actual — uses `javax.swing.JFileChooser` in DIRECTORIES_ONLY mode. Deferred
 * via `SwingUtilities.invokeLater` so the dialog's nested EDT pump
 * (`WaitDispatchSupport.enter()`) runs on a fresh event-queue task instead of nesting
 * inside the currently-dispatched coroutine continuation. Calling `showOpenDialog`
 * directly from a suspend collector (the events flow lambda) corrupts the dispatched
 * continuation's intercepted state and surfaces as
 * `CompletedContinuation cannot be cast to DispatchedContinuation` when the original
 * continuation resumes.
 *
 * `LocalFileContentSource.addLibraryLocation` (the JVM `LibrarySource` impl) expects a
 * filesystem path, not a `file://` URI, so the `uri` field on the action is
 * platform-bag-of-bytes (consistent with the Android side sending a SAF URI string).
 */
@Composable
actual fun SettingsRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier,
) {
    val viewModel: SettingsViewModel = metroViewModel()

    val routedOnEvent: (ChipboxEvent) -> Unit = { event ->
        when (event) {
            ChipboxEvent.PickFolder -> pickLibraryFolderAsync { path ->
                viewModel.sendAction(SettingsAction.FolderPicked(path))
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
