package net.sigmabeta.chipbox.features.componentlibrary.real

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListEntry
import net.sigmabeta.chipbox.features.componentlibrary.LibraryMode

@Composable
fun ComponentLibraryModeRoute(
    mode: LibraryMode,
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        assistedMetroViewModel<ComponentLibraryModeViewModel, ComponentLibraryModeViewModel.Factory> {
            create(mode)
        }
    // The sample components carry synthetic sourceInfo strings that don't resolve to real images.
    // Forcing LocalInspectionMode = true makes CrossfadeImage take its preview path and render
    // deterministic BitmapGenerator gradients (seeded per sourceInfo) instead of failing Coil
    // loads — the same way @Preview / Paparazzi render these components.
    CompositionLocalProvider(LocalInspectionMode provides true) {
        ChipboxListEntry(viewModel, onEvent, modifier)
    }
}
