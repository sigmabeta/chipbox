package net.sigmabeta.chipbox.features.componentlibrary.real

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListViewModel
import net.sigmabeta.chipbox.features.componentlibrary.ComponentLibraryMode
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

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
            is ComponentLibraryAction.OpenMode ->
                emit(ChipboxEvent.NavigateTo(ComponentLibraryMode(action.mode)))

            else -> Unit
        }
    }
}
