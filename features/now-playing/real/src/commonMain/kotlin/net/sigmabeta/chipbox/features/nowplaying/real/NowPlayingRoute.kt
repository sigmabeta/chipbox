package net.sigmabeta.chipbox.features.nowplaying.real

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.metroViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.common.ui.freeform.api.ChipboxFreeformEntry

@Composable
fun NowPlayingRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Ask the host to hide the top bar (the swipe-down chevron is the screen's own header)
    // and the bottom mini-player (would duplicate this screen's transport). The host's
    // per-screen scaffold resets chrome to default on the next screen change, so we don't
    // emit a `true` on disposal.
    LaunchedEffect(Unit) {
        onEvent(ChipboxEvent.RequestTopBarVisibility(visible = false))
        onEvent(ChipboxEvent.RequestMiniPlayerVisibility(visible = false))
    }

    val viewModel: NowPlayingViewModel = metroViewModel()
    ChipboxFreeformEntry(viewModel, onEvent, modifier) { model, actionSink, _, m ->
        NowPlayingContent(model, actionSink, m)
    }
}
