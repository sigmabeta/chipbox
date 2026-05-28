package net.sigmabeta.chipbox.features.folderpicker

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.sigmabeta.chipbox.appcomm.ChipboxEvent

// JS is enforcement-only — no DI graph, no filesystem, no real route. The stub keeps the
// commonMain `expect` satisfied so cross-platform compilation stays green.
@Suppress("UNUSED_PARAMETER")
@Composable
actual fun FolderPickerRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier,
) = Unit
