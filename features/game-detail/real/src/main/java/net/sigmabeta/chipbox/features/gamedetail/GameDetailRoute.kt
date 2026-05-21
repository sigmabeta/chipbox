package net.sigmabeta.chipbox.features.gamedetail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.ui.list.api.ChipboxListEntry

@Composable
fun GameDetailRoute(
    gameId: Long,
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = assistedMetroViewModel<GameDetailViewModel, GameDetailViewModel.Factory> {
        create(gameId)
    }
    ChipboxListEntry(viewModel, onEvent, modifier)
}
