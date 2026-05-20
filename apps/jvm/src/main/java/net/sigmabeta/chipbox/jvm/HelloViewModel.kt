package net.sigmabeta.chipbox.jvm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.sigmabeta.chipbox.ui.vm.ChipboxViewModel
import net.sigmabeta.sage.logging.Hatchet
import javax.inject.Inject

/**
 * Demo view-model for the Compose Multiplatform desktop bootstrap. Constructor-injected by
 * the JVM Dagger graph; consumed by `HelloChipbox` via `chipboxViewModel<HelloViewModel>()`.
 * Exists to prove the multiplatform `ViewModelProvider` chain works end-to-end — a real
 * port (`appui`, feature modules) gets its own VM the same way and replaces this.
 *
 * Surfaces a tiny [StateFlow] so the consuming composable can `collectAsState` it; the
 * payload is the runtime class name of the injected Hatchet so it's visible whether the
 * graph wired in a real instance (BasicHatchet) vs a no-op shim. Deliberately *not*
 * injecting Repository — that would force Room to open the SQLite file in `gui` mode where
 * it isn't needed.
 */
class HelloViewModel @Inject constructor(
    hatchet: Hatchet,
) : ChipboxViewModel() {
    private val messageMutable = MutableStateFlow(
        "ViewModel wired up — hatchet=${hatchet::class.simpleName}",
    )
    val message: StateFlow<String> = messageMutable.asStateFlow()
}
