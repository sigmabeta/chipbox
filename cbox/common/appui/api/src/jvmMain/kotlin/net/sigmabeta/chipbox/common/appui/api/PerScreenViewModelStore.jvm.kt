package net.sigmabeta.chipbox.common.appui.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen

/**
 * JVM/desktop `WithPerScreenViewModelStore` — Voyager's non-Android
 * `DefaultNavigatorScreenLifecycleProvider` returns `emptyList()`, so without this every
 * Screen would share the Compose `Window`'s `ViewModelStoreOwner` and `metroViewModel<VM>()`
 * would return the same instance across pushes.
 *
 * The store is hung off a Voyager [ScreenModel] (via [rememberScreenModel], keyed by the
 * [screen]) rather than a plain `remember`. Voyager retains `ScreenModel`s for the whole time
 * the screen is in the back stack and calls [ScreenModel.onDispose] only when the screen is
 * actually popped — so the `ViewModelStore` (and the VMs in it) survive navigating away from a
 * screen and back, matching Android's `AndroidScreenLifecycleOwner` semantics. A composition
 * `remember` would instead drop the store the moment the screen stopped being the top of the
 * stack (Voyager unmounts non-top screens), re-creating the VM on the way back and losing its
 * state — Home would reload its modules and Search would clear its query.
 */
@Composable
internal actual fun WithPerScreenViewModelStore(screen: Screen, content: @Composable () -> Unit) {
    val holder = screen.rememberScreenModel { ViewModelStoreHolder() }
    val owner = remember(holder) {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = holder.store
        }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        content()
    }
}

/**
 * Owns a [ViewModelStore] for a single Screen. [onDispose] (called by Voyager when the screen
 * leaves the back stack) clears the store so each VM gets `onCleared()` before it's GC'd.
 */
private class ViewModelStoreHolder : ScreenModel {
    val store = ViewModelStore()

    override fun onDispose() {
        store.clear()
    }
}
