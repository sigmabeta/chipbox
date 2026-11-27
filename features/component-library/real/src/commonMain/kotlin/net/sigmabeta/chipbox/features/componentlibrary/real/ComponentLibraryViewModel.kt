package net.sigmabeta.chipbox.features.componentlibrary.real

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
import net.sigmabeta.sage.ui.componentlibrary.ComponentLibraryAction
import net.sigmabeta.sage.ui.componentlibrary.reduce

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class ComponentLibraryViewModel @Inject constructor(
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<ComponentLibraryState>(
    ComponentLibraryState(),
    stringProvider,
    hatchet,
) {
    override fun handleAction(action: SageAction) {
        when (action) {
            is ComponentLibraryAction -> updateState { it.copy(config = it.config.reduce(action)) }
            else -> Unit
        }
    }
}
