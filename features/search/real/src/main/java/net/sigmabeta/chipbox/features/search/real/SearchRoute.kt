package net.sigmabeta.chipbox.features.search.real

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.ui.chrome.LocalChromeController
import net.sigmabeta.chipbox.ui.chrome.ScreenChrome

@Composable
fun SearchRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hide the app top bar — the in-screen SearchBar replaces it. Keep the nav bar and
    // player status (defaults) so Search stays a normal top-level tab.
    val chromeController = LocalChromeController.current
    LaunchedEffect(Unit) {
        chromeController.set(ScreenChrome(showTopBar = false))
    }

    val viewModel: SearchViewModel = hiltViewModel()
    LaunchedEffect(viewModel) {
        viewModel.events.collect(onEvent)
    }

    val actual by viewModel.uiStateActual.collectAsStateWithLifecycle()
    val raw by viewModel.state.collectAsStateWithLifecycle()
    val showDebug by viewModel.showDebug.collectAsStateWithLifecycle()

    SearchContent(
        listItems = actual.listItems,
        query = raw.query,
        showDebug = showDebug,
        actionSink = viewModel,
        modifier = modifier,
    )
}
