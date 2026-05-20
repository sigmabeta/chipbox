package net.sigmabeta.chipbox.ui.vm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.reflect.KClass

/**
 * Platform-side DI-container shim that resolves a [ChipboxViewModel] subtype to an instance.
 * Android impls wrap Hilt's per-`NavBackStackEntry` factory; the JVM impl dispatches to its
 * plain-Dagger `JvmChipboxComponent`. [get] is `@Composable` because the Android impl needs
 * `LocalViewModelStoreOwner` / `LocalContext` to find the right scope and factory — the JVM
 * impl can ignore those.
 *
 * The provider's lifetime + scoping is its own concern — `chipboxViewModel()` just asks for
 * a `T` and trusts whatever lifecycle policy the platform's provider follows.
 */
interface ViewModelProvider {
    @Composable
    fun <T : ChipboxViewModel> get(type: KClass<T>): T
}

/**
 * CompositionLocal carrying the active [ViewModelProvider]. App entry points (the Android
 * `setContent { ... }` Activity or the JVM `application { Window { ... } }`) wrap their root
 * composable in a `CompositionLocalProvider(LocalViewModelProvider provides ...)` so feature
 * composables can call `chipboxViewModel()` without knowing which platform they're on.
 *
 * `static` because the provider is set once per app run and never reassigned — Compose can
 * skip the regular state-tracking machinery the non-static variant carries.
 */
val LocalViewModelProvider = staticCompositionLocalOf<ViewModelProvider> {
    error(
        "No ViewModelProvider in scope. Wrap your root composable in " +
            "CompositionLocalProvider(LocalViewModelProvider provides <your impl>).",
    )
}
