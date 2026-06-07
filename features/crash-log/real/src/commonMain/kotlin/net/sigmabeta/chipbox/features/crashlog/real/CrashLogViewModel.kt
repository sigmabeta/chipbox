package net.sigmabeta.chipbox.features.crashlog.real

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListViewModel
import net.sigmabeta.chipbox.crash.CrashReportStore
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class CrashLogViewModel @Inject constructor(
    stringProvider: StringProvider,
    hatchet: Hatchet,
    crashReportStore: CrashReportStore,
) : ChipboxListViewModel<CrashLogState>(
    CrashLogState(),
    stringProvider,
    hatchet,
) {
    init {
        // The persisted reports are a fixed snapshot read once when the screen opens (at most 20
        // small files — RealCrashReporter prunes to that). The store already returns them newest
        // first.
        updateState { it.copy(crashes = crashReportStore.list()) }
    }

    override fun handleAction(action: SageAction) = Unit
}
