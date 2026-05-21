package net.sigmabeta.chipbox.features.gamesforplatform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.ui.list.ChipboxListEntry

@Composable
fun GamesForPlatformRoute(
    platform: Platform,
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        assistedMetroViewModel<GamesForPlatformViewModel, GamesForPlatformViewModel.Factory> {
            create(platform)
        }
    ChipboxListEntry(viewModel, onEvent, modifier)
}
