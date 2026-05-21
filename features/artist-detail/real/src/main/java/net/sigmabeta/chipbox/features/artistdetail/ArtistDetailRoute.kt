package net.sigmabeta.chipbox.features.artistdetail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.ui.list.api.ChipboxListEntry

@Composable
fun ArtistDetailRoute(
    artistId: Long,
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = assistedMetroViewModel<ArtistDetailViewModel, ArtistDetailViewModel.Factory> {
        create(artistId)
    }
    ChipboxListEntry(viewModel, onEvent, modifier)
}
