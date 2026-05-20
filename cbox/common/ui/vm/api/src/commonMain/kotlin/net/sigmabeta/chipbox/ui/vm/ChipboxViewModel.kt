package net.sigmabeta.chipbox.ui.vm

import androidx.lifecycle.ViewModel

/**
 * Base class for Chipbox UI view-models. Extends `androidx.lifecycle.ViewModel` from the
 * multiplatform lifecycle artifact (`androidx.lifecycle:lifecycle-viewmodel` 2.8+), which
 * publishes commonMain `ViewModel` + `viewModelScope` for both the Android target (backed by
 * Activity/Fragment/NavBackStackEntry stores) and the JVM target (where Voyager — or the
 * caller — supplies the `ViewModelStore` scope).
 *
 * Originally a deliberately-empty marker (see slice 5 of Milestone 6); promoted to extend
 * lifecycle.ViewModel here because the first real Android port — `ChipboxListViewModel` —
 * needs `viewModelScope`. JVM-side `onCleared`/store wiring isn't exercised yet (the JVM
 * provider hands out Dagger singletons; nothing calls `store.clear()`), but lands cleanly
 * the day a feature screen needs it.
 */
open class ChipboxViewModel : ViewModel()
