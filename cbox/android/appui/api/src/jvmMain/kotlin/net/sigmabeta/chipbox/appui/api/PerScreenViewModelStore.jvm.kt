package net.sigmabeta.chipbox.appui.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * JVM/desktop `WithPerScreenViewModelStore` — Voyager's non-Android
 * `DefaultNavigatorScreenLifecycleProvider` returns `emptyList()`, so without this every
 * Screen would share the Compose `Window`'s `ViewModelStoreOwner` and `metroViewModel<VM>()`
 * would return the same instance across pushes.
 *
 * Each invocation `remember`s a fresh [ViewModelStore]. The `remember` slot is tied to the
 * Screen's mount — Voyager pops a screen by unmounting its composition, which drops the
 * remembered store reference. The `DisposableEffect.onDispose` calls `store.clear()` so
 * each VM in the store gets `onCleared()` invoked before the store is GC'd, matching
 * Android's per-Screen lifecycle semantics.
 */
@Composable
internal actual fun WithPerScreenViewModelStore(content: @Composable () -> Unit) {
    val store = remember { ViewModelStore() }
    val owner = remember(store) {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
    }
    DisposableEffect(store) {
        onDispose { store.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        content()
    }
}
