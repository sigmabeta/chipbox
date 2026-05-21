package net.sigmabeta.chipbox.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.sigmabeta.chipbox.appcomm.ChipboxEvent

/**
 * `expect`/`actual` split because the folder picker for `ChipboxEvent.PickFolder` is
 * fundamentally platform-specific:
 *
 *  - Android (`androidMain`) uses SAF via
 *    `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree)`.
 *  - JVM/desktop (`jvmMain`) uses `javax.swing.JFileChooser` in DIRECTORIES_ONLY mode,
 *    deferred via `SwingUtilities.invokeLater` to keep AWT's nested EDT pump out of the
 *    currently-dispatched coroutine continuation.
 *
 * Both actuals route every other `ChipboxEvent` to the host's outer sink unchanged, and
 * intercept `PickFolder` locally so the shared chrome doesn't need a per-platform launcher
 * callback in its sink signature.
 */
@Composable
expect fun SettingsRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
)
