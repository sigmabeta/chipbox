package net.sigmabeta.chipbox.features.tracksforartist

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListEntry

@Composable
fun TracksForArtistRoute(
    artistId: Long,
    artistName: String,
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        assistedMetroViewModel<TracksForArtistViewModel, TracksForArtistViewModel.Factory> {
            create(artistId, artistName)
        }
    ChipboxListEntry(viewModel, onEvent, modifier)
}
