package net.sigmabeta.chipbox.features.playlists

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListEntry

@Composable
fun PlaylistsRoute(
    pendingTrackIds: List<Long>,
    suggestedName: String?,
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = assistedMetroViewModel<PlaylistsViewModel, PlaylistsViewModel.Factory> {
        create(pendingTrackIds, suggestedName)
    }
    ChipboxListEntry(viewModel, onEvent, modifier)
}
