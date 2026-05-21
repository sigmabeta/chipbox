package net.sigmabeta.chipbox.common.appui.api

import androidx.compose.runtime.Composable

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
 * The Android actual is a passthrough — Voyager already provides the owner there. The JVM
 * actual remembers a per-composition `ViewModelStore` and provides it via
 * `LocalViewModelStoreOwner`; `DisposableEffect.onDispose` clears the store when the screen
 * unmounts so VMs get `onCleared()` called and don't leak.
 */
@Composable
internal expect fun WithPerScreenViewModelStore(content: @Composable () -> Unit)
