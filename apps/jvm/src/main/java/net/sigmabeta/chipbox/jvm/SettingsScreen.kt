package net.sigmabeta.chipbox.jvm

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.features.settings.SettingsAction
import net.sigmabeta.chipbox.features.settings.SettingsViewModel
import net.sigmabeta.chipbox.ui.chrome.LocalTitleBarController
import net.sigmabeta.chipbox.ui.chrome.TitleBarController
import net.sigmabeta.chipbox.ui.list.ChipboxListEntry

/**
 * Desktop counterpart to the Android `SettingsRoute`. Pulls the shared (commonMain)
 * [SettingsViewModel] from the Metro graph and renders it through the commonMain
 * [ChipboxListEntry] promoted in M9 slice 6g — same `SettingsState.toListItems()` rows
 * the Android UI shows.
 *
 * Event handling differs by platform:
 *   - Android (`SettingsRoute`) intercepts [ChipboxEvent.PickFolder] with the SAF
 *     `OpenDocumentTree` launcher and sends the resulting SAF URI back through
 *     `SettingsAction.FolderPicked`.
 *   - Desktop pops a `javax.swing.JFileChooser` in directories-only mode and sends the
 *     selected absolute path back as the same `SettingsAction.FolderPicked` payload.
 *     `LocalFileContentSource.addLibraryLocation` expects a filesystem path (not a `file://`
 *     URI) — the `uri` field on the action is platform-bag-of-bytes, not a strict URI.
 *
 * [ChipboxListEntry] reads [LocalTitleBarController] for the screen title; the bind is
 * scoped to this screen so each Voyager destination gets its own controller instance.
 */
data object SettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: SettingsViewModel = metroViewModel()
        val titleBarController = remember { TitleBarController() }
        val snackbarHostState = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope()

        val onEvent: (ChipboxEvent) -> Unit = { event ->
            when (event) {
                ChipboxEvent.NavigateBack -> navigator.pop()
                is ChipboxEvent.OpenUrl -> openUrlIfSupported(event.url)
                is ChipboxEvent.ShowSnackbar -> coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        withDismissAction = event.withDismissAction,
                    )
                }
                is ChipboxEvent.CopyToClipboard -> copyToClipboard(event.text)
                ChipboxEvent.PickFolder -> pickLibraryFolderAsync { path ->
                    viewModel.sendAction(SettingsAction.FolderPicked(path))
                }
                is ChipboxEvent.NavigateTo -> Unit
            }
        }

        CompositionLocalProvider(LocalTitleBarController provides titleBarController) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(titleBarController.state.title ?: "Settings") },
                        navigationIcon = {
                            IconButton(onClick = { navigator.pop() }) {
                                Text("<")
                            }
                        },
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { padding ->
                ChipboxListEntry(
                    viewModel = viewModel,
                    onEvent = onEvent,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

private fun openUrlIfSupported(url: String) {
    if (!Desktop.isDesktopSupported()) return
    val desktop = Desktop.getDesktop()
    if (!desktop.isSupported(Desktop.Action.BROWSE)) return
    desktop.browse(URI(url))
}

private fun copyToClipboard(text: String) {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(StringSelection(text), null)
}

/**
 * Pop a modal directory chooser via [SwingUtilities.invokeLater] so the dialog's nested EDT
 * pump (`WaitDispatchSupport.enter()`) runs on a fresh event-queue task instead of nesting
 * inside the currently-dispatched coroutine continuation. Calling `JFileChooser.showOpenDialog`
 * directly from a suspend collector (the events flow lambda) corrupts the dispatched
 * continuation's intercepted state and surfaces as
 * `CompletedContinuation cannot be cast to DispatchedContinuation` when the original
 * continuation tries to resume.
 */
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
