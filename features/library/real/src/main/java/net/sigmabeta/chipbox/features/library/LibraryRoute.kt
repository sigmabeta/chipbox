package net.sigmabeta.chipbox.features.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxNavEvent
import net.sigmabeta.chipbox.ui.list.ChipboxListEntry

@Composable
fun LibraryRoute(
    onNavEvent: (ChipboxNavEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LibraryViewModel = hiltViewModel()
    ChipboxListEntry(viewModel, onNavEvent, modifier)
}
