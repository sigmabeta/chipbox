package net.sigmabeta.chipbox.vm

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.HiltViewModelFactory
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import net.sigmabeta.chipbox.ui.vm.ChipboxViewModel
import net.sigmabeta.chipbox.ui.vm.ViewModelProvider
import kotlin.reflect.KClass

/**
 * Android-side [ViewModelProvider] — the actual that makes `chipboxViewModel<T>()` work on
 * Android. Mirrors what `hiltViewModel<T>()` does internally: pulls the active
 * `ViewModelStoreOwner` out of composition, wraps its default factory in
 * `HiltViewModelFactory` (so `@HiltViewModel`-annotated classes are creatable), and asks
 * `androidx.lifecycle.viewmodel.compose.viewModel` to either return the cached instance for
 * this owner or create a new one.
 *
 * Generic — any `@HiltViewModel` class extending [ChipboxViewModel] resolves with zero extra
 * wiring per VM. The JVM-side `JvmViewModelProvider` has a `when` over known types because
 * it dispatches to component accessors; Android trusts Hilt's KSP-generated factory
 * registry.
 *
 * Scope follows Hilt's normal rules: inside a `NavHost { composable { ... } }`, the owner is
 * a [NavBackStackEntry] and the VM dies with the screen; outside one (e.g. the activity-root
 * composable), the owner is the [androidx.activity.ComponentActivity] and the VM is
 * activity-scoped.
 */
class AndroidHiltViewModelProvider : ViewModelProvider {
    @Composable
    override fun <T : ChipboxViewModel> get(type: KClass<T>): T {
        val owner = checkNotNull(LocalViewModelStoreOwner.current) {
            "No LocalViewModelStoreOwner in composition — wrap your content in a NavHost or " +
                "an Activity-rooted setContent { }."
        }
        val context = LocalContext.current
        val factory = when (owner) {
            is NavBackStackEntry -> HiltViewModelFactory(context, owner)
            is HasDefaultViewModelProviderFactory -> HiltViewModelFactory(context, owner.defaultViewModelProviderFactory)
            else -> error(
                "ViewModelStoreOwner ${owner::class.simpleName} provides no default factory " +
                    "Hilt can wrap; expected a NavBackStackEntry or a HasDefaultViewModelProviderFactory.",
            )
        }
        return viewModel(modelClass = type.java, viewModelStoreOwner = owner, factory = factory)
    }
}
