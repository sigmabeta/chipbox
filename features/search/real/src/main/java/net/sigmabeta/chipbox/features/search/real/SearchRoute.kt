package net.sigmabeta.chipbox.features.search.real

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.ui.chrome.LocalChromeController
import net.sigmabeta.chipbox.ui.chrome.ScreenChrome
import net.sigmabeta.chipbox.ui.freeform.ChipboxFreeformEntry

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
    ChipboxFreeformEntry(viewModel, onEvent, modifier) { model, actionSink, _, m ->
        SearchContent(model, actionSink, m)
    }
}
