package net.sigmabeta.chipbox.features.gamedetail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxNavEvent
import net.sigmabeta.chipbox.ui.list.ChipboxListEntry

@Composable
fun GameDetailRoute(
    onNavEvent: (ChipboxNavEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: GameDetailViewModel = hiltViewModel()
    ChipboxListEntry(viewModel, onNavEvent, modifier)
}
