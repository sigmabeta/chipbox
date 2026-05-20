package net.sigmabeta.chipbox.ui.vm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Multiplatform Chipbox view-model accessor. Resolves a [ChipboxViewModel] subtype `T` against
 * the [LocalViewModelProvider] in scope. Mirror of Hilt's `hiltViewModel<T>()` shape — call as
 * `val vm: HelloViewModel = chipboxViewModel()`.
 *
 * The function is `inline` + `reified` so [T] survives erasure; `@Composable` because the
 * underlying `staticCompositionLocalOf` read is, and `@ReadOnlyComposable` because it just
 * reads the local without producing any composition state of its own.
 */
@Composable
@ReadOnlyComposable
inline fun <reified T : ChipboxViewModel> chipboxViewModel(): T =
    LocalViewModelProvider.current.get(T::class)
