package net.sigmabeta.chipbox.ui.vm

import androidx.compose.runtime.Composable

/**
 * Multiplatform Chipbox view-model accessor. Resolves a [ChipboxViewModel] subtype `T` against
 * the [LocalViewModelProvider] in scope. Mirror of Hilt's `hiltViewModel<T>()` shape — call as
 * `val vm: HelloViewModel = chipboxViewModel()`.
 *
 * `inline` + `reified` so [T] survives erasure. Not `@ReadOnlyComposable` — the Android impl
 * delegates to `viewModel(modelClass, factory)`, which produces composition state.
 */
@Composable
inline fun <reified T : ChipboxViewModel> chipboxViewModel(): T =
    LocalViewModelProvider.current.get(T::class)
