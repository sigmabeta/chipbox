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
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.features.library.LibraryViewModel
import net.sigmabeta.chipbox.ui.chrome.LocalTitleBarController
import net.sigmabeta.chipbox.ui.chrome.TitleBarController
import net.sigmabeta.chipbox.ui.list.ChipboxListEntry

/**
 * Desktop counterpart to the Android `LibraryRoute`. Same shape as [SettingsScreen] —
 * pulls [LibraryViewModel] from the Metro graph and renders it through the commonMain
 * [ChipboxListEntry]. `LibraryState.toListItems()` is the single source of truth for the
 * rows on both targets.
 *
 * `LibraryViewModel` only emits [ChipboxEvent.NavigateTo] (one per Browse* sub-destination).
 * Those sub-feature VMs / screens aren't ported to JVM yet, so each navigation request
 * lands as a "coming soon" snackbar — same placeholder pattern Settings uses for
 * `PlaybackStatusClicked`.
 */
data object LibraryScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: LibraryViewModel = metroViewModel()
        val titleBarController = remember { TitleBarController() }
        val snackbarHostState = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope()

        val onEvent: (ChipboxEvent) -> Unit = { event ->
            when (event) {
                ChipboxEvent.NavigateBack -> navigator.pop()
                is ChipboxEvent.NavigateTo -> coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = "${destinationLabel(event.destination)}: not implemented yet.",
                    )
                }
                is ChipboxEvent.ShowSnackbar -> coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        withDismissAction = event.withDismissAction,
                    )
                }
                is ChipboxEvent.OpenUrl,
                is ChipboxEvent.CopyToClipboard,
                ChipboxEvent.PickFolder -> Unit
            }
        }

        CompositionLocalProvider(LocalTitleBarController provides titleBarController) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(titleBarController.state.title ?: "Library") },
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

private fun destinationLabel(destination: Any): String =
    destination::class.simpleName ?: "Destination"
