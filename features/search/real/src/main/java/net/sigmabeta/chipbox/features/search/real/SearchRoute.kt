package net.sigmabeta.chipbox.features.search.real

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import dev.zacsweers.metrox.viewmodel.metroViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.ui.chrome.api.LocalChromeController
import net.sigmabeta.chipbox.ui.chrome.api.ScreenChrome

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

    val viewModel: SearchViewModel = metroViewModel()
    LaunchedEffect(viewModel) {
        viewModel.events.collect(onEvent)
    }

    val actual by viewModel.uiStateActual.collectAsState()
    val raw by viewModel.state.collectAsState()
    val showDebug by viewModel.showDebug.collectAsState()

    SearchContent(
        listItems = actual.listItems,
        query = raw.query,
        showDebug = showDebug,
        actionSink = viewModel,
        modifier = modifier,
    )
}
