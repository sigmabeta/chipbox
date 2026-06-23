package net.sigmabeta.chipbox.common.appui.api

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen

/**
 * Voyager's per-Screen `ViewModelStoreOwner` plumbing is Android-only — `Navigator.kt`'s
 * `DefaultNavigatorScreenLifecycleProvider` returns
 * `listOf(AndroidScreenLifecycleOwner.get(screen))` on androidMain but `emptyList()` on
 * desktop / iOS / wasm (see `voyager-navigator/.../LifecycleProvider.nonAndroid.kt`). Without
 * a per-screen owner, every Screen in a Voyager `Navigator` shares whichever ancestor
 * provided `LocalViewModelStoreOwner` (the Compose `Window` on JVM), so
 * `metroViewModel<VM>()` returns the same cached instance on every push — pushing
 * `GamesForPlatform(DREAMCAST)`, popping, then pushing `GamesForPlatform(GENESIS)` shows
 * Dreamcast content because the VM is reused.
 *
 * The Android actual is a passthrough — Voyager already provides the owner there, and crucially
 * retains it for as long as [screen] stays in the back stack (only disposing when the screen is
 * popped). The JVM/JS actual mirrors that lifetime by hanging the `ViewModelStore` off a Voyager
 * `ScreenModel` keyed to [screen]: Voyager keeps `ScreenModel`s alive across the whole back stack
 * and calls `onDispose()` only when the screen leaves it. A plain composition-scoped
 * `remember { ViewModelStore() }` does NOT do this — Voyager unmounts a screen's composition the
 * moment it stops being the top of the stack (e.g. when a detail screen is pushed over Home), so
 * the store would be cleared and the VM re-created on the way back, losing Home's loaded modules
 * and Search's active query. [screen] is therefore required to scope the store correctly.
 */
@Composable
internal expect fun WithPerScreenViewModelStore(screen: Screen, content: @Composable () -> Unit)
