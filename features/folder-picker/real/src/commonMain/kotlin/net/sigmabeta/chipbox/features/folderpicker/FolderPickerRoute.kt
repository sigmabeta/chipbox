package net.sigmabeta.chipbox.features.folderpicker

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.sigmabeta.chipbox.appcomm.ChipboxEvent

/**
 * `expect`/`actual` because the picker's initial directory differs per OS: Android lands the
 * user in `Environment.getExternalStorageDirectory()`, JVM in `System.getProperty("user.home")`.
 * JS is enforcement-only and has no real implementation. The actuals all forward [onEvent]
 * untouched — this screen has no platform-specific events to intercept (cf. ManageLibrary).
 */
@Composable
expect fun FolderPickerRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
)
