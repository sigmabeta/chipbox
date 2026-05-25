package net.sigmabeta.chipbox.features.browsealltracks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListViewModel
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class BrowseAllTracksViewModel @Inject constructor(
    private val repository: Repository,
    private val director: Director,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<BrowseAllTracksState>(
    BrowseAllTracksState(),
    stringProvider,
    hatchet,
) {

    init {
        viewModelScope.launch {
            repository.getAllTracks(withGame = true).collect { data ->
                val lce: LCE<List<Track>> = when (data) {
                    Data.Loading -> LCE.Loading(LOAD_OP)
                    Data.Empty -> LCE.Content(emptyList())
                    is Data.Succeeded -> LCE.Content(data.data)
                    is Data.Failed -> LCE.Error(LOAD_OP, IllegalStateException(data.message))
                }
                updateState { it.copy(tracks = lce) }
            }
        }

        viewModelScope.launch {
            director.metadataState().collect { track ->
                updateState { it.copy(playingTrackId = track?.id) }
            }
        }
    }

    override fun handleAction(action: SageAction) {
        when (action) {
            is BrowseAllTracksAction.TrackClicked -> startSession(startingPosition = action.position)
            BrowseAllTracksAction.ShuffleAllClicked -> startSession(startingPosition = 0, shuffled = true)
            else -> Unit
        }
    }

    private fun startSession(startingPosition: Int, shuffled: Boolean = false) {
        director.start(
            Session(
                type = SessionType.ALL_TRACKS,
                contentId = 0L,
                startingPosition = startingPosition,
                shuffled = shuffled,
            )
        )
    }

    private companion object {
        const val LOAD_OP = "browse_all_tracks.load"
    }
}
