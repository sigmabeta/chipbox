package net.sigmabeta.chipbox.features.playlistdetail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxReorderableEntry

@Composable
fun PlaylistDetailRoute(
    playlistId: Long,
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = assistedMetroViewModel<PlaylistDetailViewModel, PlaylistDetailViewModel.Factory> {
        create(playlistId)
    }
    // Single-column reorderable list for both modes; edit mode marks its track rows draggable.
    ChipboxReorderableEntry(viewModel, onEvent, modifier)
}
