package net.sigmabeta.chipbox.features.browsebyartist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent.NavigateTo
import net.sigmabeta.chipbox.features.artistdetail.ArtistDetail
import net.sigmabeta.chipbox.models.Artist
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
class BrowseByArtistViewModel @Inject constructor(
    private val repository: Repository,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<BrowseByArtistState>(
    BrowseByArtistState(),
    stringProvider,
    hatchet,
) {

    init {
        viewModelScope.launch {
            repository.getAllArtists().collect { data ->
                val lce: LCE<List<Artist>> = when (data) {
                    Data.Loading -> LCE.Loading(LOAD_OP)
                    Data.Empty -> LCE.Content(emptyList())
                    is Data.Succeeded -> LCE.Content(data.data)
                    is Data.Failed -> LCE.Error(LOAD_OP, IllegalStateException(data.message))
                }
                updateState { it.copy(artists = lce) }
            }
        }
    }

    override fun handleAction(action: SageAction) {
        when (action) {
            is BrowseByArtistAction.ArtistClicked -> emit(NavigateTo(ArtistDetail(action.id)))
            else -> Unit
        }
    }

    private companion object {
        const val LOAD_OP = "browse_by_artist.load"
    }
}
