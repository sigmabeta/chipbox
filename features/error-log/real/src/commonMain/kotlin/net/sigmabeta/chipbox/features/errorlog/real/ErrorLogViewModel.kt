package net.sigmabeta.chipbox.features.errorlog.real

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListViewModel
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class ErrorLogViewModel @Inject constructor(
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<ErrorLogState>(
    ErrorLogState(),
    stringProvider,
    hatchet,
) {
    init {
        // recentErrors is a plain in-memory snapshot (Hatchet's last-16 ring buffer), not a flow —
        // read it once when the screen opens. Newest first so the latest error sits at the top.
        updateState { it.copy(errors = hatchet.recentErrors.reversed()) }
    }

    override fun handleAction(action: SageAction) = Unit
}
