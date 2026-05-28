package net.sigmabeta.chipbox.features.folderpicker

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
    // `user.home` is set on every JVM (linux/macOS `$HOME`, Windows `USERPROFILE`); falling back
    // to "/" keeps the picker functional on the off chance the property is unset.
    val defaultPath = remember { System.getProperty("user.home") ?: "/" }
    val viewModel = assistedMetroViewModel<FolderPickerViewModel, FolderPickerViewModel.Factory> {
        create(defaultPath)
    }
    ChipboxListEntry(viewModel, onEvent, modifier)
}
