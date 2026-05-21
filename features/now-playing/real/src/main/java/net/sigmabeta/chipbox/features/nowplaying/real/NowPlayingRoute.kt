package net.sigmabeta.chipbox.features.nowplaying.real

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.metroViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.ui.chrome.api.LocalChromeController
import net.sigmabeta.chipbox.ui.chrome.api.ScreenChrome
import net.sigmabeta.chipbox.ui.freeform.api.ChipboxFreeformEntry

@Composable
fun NowPlayingRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chromeController = LocalChromeController.current
    LaunchedEffect(Unit) {
        chromeController.set(
            ScreenChrome(
                showTopBar = false,
                showPlayerStatus = false
            )
        )
    }

    val viewModel: NowPlayingViewModel = metroViewModel()
    ChipboxFreeformEntry(viewModel, onEvent, modifier) { model, actionSink, _, m ->
        NowPlayingContent(model, actionSink, m)
    }
}
