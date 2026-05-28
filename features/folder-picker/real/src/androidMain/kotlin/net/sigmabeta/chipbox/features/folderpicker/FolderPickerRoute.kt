package net.sigmabeta.chipbox.features.folderpicker

import android.os.Environment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListEntry

@Composable
actual fun FolderPickerRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier,
) {
    // External storage is `/storage/emulated/0` on most devices — the same root the SAF picker
    // lands users in by default.
    val defaultPath = remember { Environment.getExternalStorageDirectory().absolutePath }
    val viewModel = assistedMetroViewModel<FolderPickerViewModel, FolderPickerViewModel.Factory> {
        create(defaultPath)
    }
    ChipboxListEntry(viewModel, onEvent, modifier)
}
