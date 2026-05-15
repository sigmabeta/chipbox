package net.sigmabeta.chipbox.features.gamesforplatform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.ui.list.ChipboxListEntry

@Composable
fun GamesForPlatformRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: GamesForPlatformViewModel = hiltViewModel()
    ChipboxListEntry(viewModel, onEvent, modifier)
}
