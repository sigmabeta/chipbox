package net.sigmabeta.chipbox.features.componentlibrary.real

import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListViewModel
import net.sigmabeta.chipbox.features.componentlibrary.LibraryMode
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

@AssistedInject
class ComponentLibraryModeViewModel(
    @Assisted private val mode: LibraryMode,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<ComponentLibraryModeState>(
    ComponentLibraryModeState(),
    stringProvider,
    hatchet,
) {
    init {
        // Seed by mode so each sub-screen has its own (stable) sample set; generated once.
        updateState {
            it.copy(
                mode = mode,
                content = generateSampleContent(mode.ordinal.toLong(), SAMPLE_COUNT),
            )
        }
    }

    // Sample rows use SageAction.Noop; the one interactive control is the dropdown, which toggles
    // its expansion the same way real screens do (at most one open at a time).
    override fun handleAction(action: SageAction) {
        when (action) {
            is ComponentLibraryAction.DropdownExpandClicked -> updateState {
                val next = if (it.expandedDropdownId == action.settingId) null else action.settingId
                it.copy(expandedDropdownId = next)
            }

            else -> Unit
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(@Assisted mode: LibraryMode): ComponentLibraryModeViewModel
    }

    private companion object {
        const val SAMPLE_COUNT = 12
    }
}
