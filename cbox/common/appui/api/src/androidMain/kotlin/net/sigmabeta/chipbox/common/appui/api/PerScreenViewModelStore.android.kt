package net.sigmabeta.chipbox.common.appui.api

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen

/**
 * Android passthrough — Voyager's `AndroidScreenLifecycleOwner` (per `Navigator.kt` →
 * `DefaultNavigatorScreenLifecycleProvider`) already provides per-Screen
 * `LocalViewModelStoreOwner` here and retains it across the back stack. Wrapping again would
 * override that and lose Voyager's saved-state restoration plumbing. [screen] is unused here —
 * only the JVM/JS actuals need it (to scope their own store to the screen's stack lifetime).
 */
@Composable
internal actual fun WithPerScreenViewModelStore(screen: Screen, content: @Composable () -> Unit) {
    content()
}
