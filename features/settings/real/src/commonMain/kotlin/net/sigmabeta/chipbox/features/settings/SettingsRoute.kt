package net.sigmabeta.chipbox.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.sigmabeta.chipbox.appcomm.ChipboxEvent

/**
 * `expect`/`actual` split because the folder picker for `ChipboxEvent.PickFolder` is
 * platform-specific:
 *
 *  - Android (`androidMain`) and JVM/desktop (`jvmMain`) both push the in-app
 *    `:features:folder-picker` screen. The Android actual first gates it behind All Files Access
 *    (`MANAGE_EXTERNAL_STORAGE`), since the picker browses and the scanner reads raw paths.
 *  - The jsMain stub forwards events unchanged.
 *
 * Each actual routes every other `ChipboxEvent` to the host's outer sink unchanged, and
 * intercepts `PickFolder` locally so the shared chrome doesn't need a per-platform launcher
 * callback in its sink signature.
 */
@Composable
expect fun SettingsRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
)
