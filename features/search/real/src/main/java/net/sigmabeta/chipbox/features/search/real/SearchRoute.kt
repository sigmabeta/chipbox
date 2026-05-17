package net.sigmabeta.chipbox.features.search.real

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    // Query is screen-local UI state — the ViewModel never reads it (search is UI-only),
    // so it lives here rather than being hoisted into the nav graph. rememberSaveable
    // survives configuration changes / process death.
    var query by rememberSaveable { mutableStateOf("") }
    // Hide the app top bar — the in-screen SearchBar replaces it. Keep the nav bar and
    // player status (defaults) so Search stays a normal top-level tab.
    val chromeController = LocalChromeController.current
    LaunchedEffect(Unit) {
        chromeController.set(ScreenChrome(showTopBar = false))
    }

    val viewModel: SearchViewModel = hiltViewModel()
    ChipboxFreeformEntry(viewModel, onEvent, modifier) { model, actionSink, _, m ->
        SearchContent(model, query, { query = it }, actionSink, m)
    }
}
