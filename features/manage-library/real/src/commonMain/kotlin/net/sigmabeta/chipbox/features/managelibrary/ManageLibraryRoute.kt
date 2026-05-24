package net.sigmabeta.chipbox.features.managelibrary

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.sigmabeta.chipbox.appcomm.ChipboxEvent

/**
 * `expect`/`actual` for the same reason [net.sigmabeta.chipbox.features.settings.SettingsRoute] is:
 * the folder picker behind `ChipboxEvent.PickFolder` (raised by the "Add folder to library" CTA)
 * is platform-specific — SAF `OpenDocumentTree` on Android, `JFileChooser` on the JVM. Each actual
 * intercepts `PickFolder` locally and forwards every other event to the host's outer sink.
 */
@Composable
expect fun ManageLibraryRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
)
