package net.sigmabeta.chipbox.jvm

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet

/**
 * Demo view-model for the Compose Multiplatform desktop bootstrap. Resolved by
 * `metroViewModel<HelloViewModel>()` in `HomeScreen` through the multibinding map
 * `JvmChipboxGraph` exposes via the inherited `ViewModelGraph`. Exists to prove the
 * multiplatform Metro VM chain works end-to-end on JVM — a real feature port replaces this.
 *
 * Surfaces a tiny [StateFlow] so the consuming composable can `collectAsState` it; the
 * payload is the runtime class name of the injected Hatchet so it's visible whether the
 * graph wired in a real instance (BasicHatchet) vs a no-op shim. Deliberately *not*
 * injecting Repository — that would force Room to open the SQLite file in `gui` mode where
 * it isn't needed.
 */
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class HelloViewModel @Inject constructor(
    hatchet: Hatchet,
) : ViewModel() {
    private val messageMutable = MutableStateFlow(
        "ViewModel wired up — hatchet=${hatchet::class.simpleName}",
    )
    val message: StateFlow<String> = messageMutable.asStateFlow()
}
