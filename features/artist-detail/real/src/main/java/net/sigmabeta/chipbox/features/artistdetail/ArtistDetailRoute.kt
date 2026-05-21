package net.sigmabeta.chipbox.features.artistdetail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.metroViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.ui.list.ChipboxListEntry

@Composable
fun ArtistDetailRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ArtistDetailViewModel = metroViewModel()
    ChipboxListEntry(viewModel, onEvent, modifier)
}
