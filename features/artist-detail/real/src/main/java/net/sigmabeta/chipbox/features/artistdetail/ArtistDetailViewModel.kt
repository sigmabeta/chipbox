package net.sigmabeta.chipbox.features.artistdetail

import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent.NavigateTo
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
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

@AssistedInject
class ArtistDetailViewModel(
    @Assisted private val artistId: Long,
    private val repository: Repository,
    private val director: Director,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<ArtistDetailState>(
    ArtistDetailState(),
    stringProvider,
    hatchet,
) {

    init {
        viewModelScope.launch {
            repository
                .getArtist(artistId, withTracks = true, withGames = true)
                .collect(::onArtistData)
        }

        viewModelScope.launch {
            director.metadataState().collect { track ->
                updateState { it.copy(playingTrackId = track?.id) }
            }
        }
    }

    override fun handleAction(action: SageAction) {
        when (action) {
            ArtistDetailAction.PlayAllClicked -> startSession(startingPosition = 0)
            ArtistDetailAction.ShuffleAllClicked -> startSession(startingPosition = 0, shuffled = true)
            is ArtistDetailAction.TrackClicked -> startSession(startingPosition = action.position)
            is ArtistDetailAction.GameClicked -> emit(NavigateTo(GameDetail(action.id)))
            else -> Unit
        }
    }

    private fun startSession(startingPosition: Int, shuffled: Boolean = false) {
        director.start(
            Session(
                type = SessionType.ARTIST,
                contentId = artistId,
                startingPosition = startingPosition,
                shuffled = shuffled,
            )
        )
    }

    private fun onArtistData(data: Data<net.sigmabeta.chipbox.models.Artist?>) {
        when (data) {
            Data.Loading -> updateState {
                it.copy(
                    artist = LCE.Loading(LOAD_OP),
                    tracks = LCE.Loading(LOAD_OP),
                    games = LCE.Loading(LOAD_OP),
                    notFound = false,
                )
            }

            Data.Empty -> updateState {
                it.copy(
                    artist = LCE.Uninitialized,
                    tracks = LCE.Uninitialized,
                    games = LCE.Uninitialized,
                    notFound = true,
                )
            }

            is Data.Succeeded -> {
                val artist = data.data ?: return
                updateState {
                    it.copy(
                        artist = LCE.Content(artist),
                        tracks = LCE.Content(artist.tracks.orEmpty()),
                        games = LCE.Content(artist.games.orEmpty()),
                        notFound = false,
                    )
                }
            }

            is Data.Failed -> updateState {
                val err = IllegalStateException(data.message)
                it.copy(
                    artist = LCE.Error(LOAD_OP, err),
                    tracks = LCE.Error(LOAD_OP, err),
                    games = LCE.Error(LOAD_OP, err),
                    notFound = false,
                )
            }
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(@Assisted artistId: Long): ArtistDetailViewModel
    }

    private companion object {
        const val LOAD_OP = "artist_detail.load"
    }
}
