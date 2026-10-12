package net.sigmabeta.chipbox.features.tracksforartist

import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListViewModel
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.SessionRequest
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

@AssistedInject
class TracksForArtistViewModel(
    @Assisted private val artistId: Long,
    @Assisted artistName: String,
    private val repository: Repository,
    private val director: Director,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<TracksForArtistState>(
    TracksForArtistState(artistName = artistName),
    stringProvider,
    hatchet,
) {

    init {
        loadTracks()

        viewModelScope.launch {
            director.metadataState().collect { track ->
                updateState { it.copy(playingTrackId = track?.id) }
            }
        }
    }

    override fun handleAction(action: SageAction) {
        when (action) {
            TracksForArtistAction.PlayAllClicked -> startSession(startingPosition = 0)

            TracksForArtistAction.ShuffleAllClicked ->
                startSession(startingPosition = 0, shuffled = true)

            is TracksForArtistAction.TrackClicked -> startSession(startingPosition = action.position)

            else -> Unit
        }
    }

    private fun loadTracks() {
        viewModelScope.launch {
            updateState { it.copy(tracks = LCE.Loading(LOAD_OP)) }
            val lce: LCE<List<Track>> = try {
                LCE.Content(repository.getTracksForArtist(artistId, withGame = true, withArtists = false))
            } catch (ex: Exception) {
                LCE.Error(LOAD_OP, ex)
            }
            updateState { it.copy(tracks = lce) }
        }
    }

    private fun startSession(startingPosition: Int, shuffled: Boolean = false) {
        val session = Session(
            type = SessionType.ARTIST,
            contentId = artistId,
            startingPosition = startingPosition,
            shuffled = shuffled,
        )
        director.request(SessionRequest.Start(session))
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(@Assisted artistId: Long, @Assisted artistName: String): TracksForArtistViewModel
    }

    private companion object {
        const val LOAD_OP = "tracks_for_artist.load"
    }
}
