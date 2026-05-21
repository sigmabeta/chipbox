package net.sigmabeta.chipbox.features.browsebyplatform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent.NavigateTo
import net.sigmabeta.chipbox.features.gamesforplatform.GamesForPlatform
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.ui.list.api.ChipboxListViewModel
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class BrowseByPlatformViewModel @Inject constructor(
    private val repository: Repository,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<BrowseByPlatformState>(
    BrowseByPlatformState(),
    stringProvider,
    hatchet,
) {

    init {
        viewModelScope.launch {
            repository.getAvailablePlatforms().collect { data ->
                val lce: LCE<List<Platform>> = when (data) {
                    Data.Loading -> LCE.Loading(LOAD_OP)
                    Data.Empty -> LCE.Content(emptyList())
                    is Data.Succeeded -> LCE.Content(data.data)
                    is Data.Failed -> LCE.Error(LOAD_OP, IllegalStateException(data.message))
                }
                updateState { it.copy(platforms = lce) }
            }
        }
    }

    override fun handleAction(action: SageAction) {
        when (action) {
            is BrowseByPlatformAction.PlatformClicked ->
                emit(NavigateTo(GamesForPlatform(action.platform)))

            else -> Unit
        }
    }

    private companion object {
        const val LOAD_OP = "browse_by_platform.load"
    }
}
