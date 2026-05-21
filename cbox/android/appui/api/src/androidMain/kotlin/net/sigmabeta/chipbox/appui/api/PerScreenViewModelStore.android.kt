package net.sigmabeta.chipbox.appui.api

import androidx.compose.runtime.Composable

/**
 * Android passthrough — Voyager's `AndroidScreenLifecycleOwner` (per `Navigator.kt` →
 * `DefaultNavigatorScreenLifecycleProvider`) already provides per-Screen
 * `LocalViewModelStoreOwner` here. Wrapping again would override that and lose Voyager's
 * saved-state restoration plumbing.
 */
@Composable
internal actual fun WithPerScreenViewModelStore(content: @Composable () -> Unit) {
    content()
}
