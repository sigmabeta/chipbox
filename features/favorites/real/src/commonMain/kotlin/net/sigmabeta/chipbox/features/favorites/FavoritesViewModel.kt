package net.sigmabeta.chipbox.features.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent.NavigateTo
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListViewModel
import net.sigmabeta.chipbox.features.artistdetail.ArtistDetail
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.SessionRequest
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class FavoritesViewModel @Inject constructor(
    private val director: Director,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<FavoritesState>(
    FavoritesState(),
    stringProvider,
    hatchet,
) {

    init {
        // Highlight the active track if one of the favorites is currently playing. The favorite
        // collections themselves are wired to a data source in a later step.
        viewModelScope.launch {
            director.metadataState().collect { track ->
                updateState { it.copy(playingTrackId = track?.id) }
            }
        }
    }

    override fun handleAction(action: SageAction) {
        when (action) {
            is FavoritesAction.TrackClicked -> startSession(startingPosition = action.position)
            is FavoritesAction.GameClicked -> emit(NavigateTo(GameDetail(action.id)))
            is FavoritesAction.ArtistClicked -> emit(NavigateTo(ArtistDetail(action.id)))
            else -> Unit
        }
    }

    private fun startSession(startingPosition: Int, shuffled: Boolean = false) {
        val session = Session(
            type = SessionType.FAVORITES,
            contentId = 0L,
            startingPosition = startingPosition,
            shuffled = shuffled,
        )
        director.request(SessionRequest.Start(session))
    }
}
