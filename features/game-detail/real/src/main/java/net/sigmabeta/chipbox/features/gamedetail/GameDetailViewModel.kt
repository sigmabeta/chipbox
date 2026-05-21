package net.sigmabeta.chipbox.features.gamedetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import javax.inject.Inject
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent.NavigateTo
import net.sigmabeta.chipbox.features.artistdetail.ArtistDetail
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.ui.list.ChipboxListViewModel
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class GameDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: Repository,
    private val director: Director,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<GameDetailState>(
    GameDetailState(),
    stringProvider,
    hatchet,
) {

    private val args: GameDetail = savedStateHandle.toRoute()

    init {
        viewModelScope.launch {
            repository
                .getGame(args.id, withTracks = true, withArtists = true)
                .collect(::onGameData)
        }

        viewModelScope.launch {
            director.metadataState().collect { track ->
                updateState { it.copy(playingTrackId = track?.id) }
            }
        }
    }

    override fun handleAction(action: SageAction) {
        when (action) {
            GameDetailAction.PlayAllClicked -> startSession(startingPosition = 0)
            GameDetailAction.ShuffleAllClicked -> startSession(startingPosition = 0, shuffled = true)
            is GameDetailAction.TrackClicked -> startSession(startingPosition = action.position)
            is GameDetailAction.ArtistClicked -> emit(NavigateTo(ArtistDetail(action.id)))
            else -> Unit
        }
    }

    private fun startSession(startingPosition: Int, shuffled: Boolean = false) {
        director.start(
            Session(
                type = SessionType.GAME,
                contentId = args.id,
                startingPosition = startingPosition,
                shuffled = shuffled,
            )
        )
    }

    private fun onGameData(data: Data<net.sigmabeta.chipbox.models.Game?>) {
        when (data) {
            Data.Loading -> updateState {
                it.copy(
                    game = LCE.Loading(LOAD_OP),
                    tracks = LCE.Loading(LOAD_OP),
                    artists = LCE.Loading(LOAD_OP),
                    notFound = false,
                )
            }

            Data.Empty -> updateState {
                it.copy(
                    game = LCE.Uninitialized,
                    tracks = LCE.Uninitialized,
                    artists = LCE.Uninitialized,
                    notFound = true,
                )
            }

            is Data.Succeeded -> {
                val game = data.data ?: return
                updateState {
                    it.copy(
                        game = LCE.Content(game),
                        tracks = LCE.Content(game.tracks.orEmpty()),
                        artists = LCE.Content(game.artists.orEmpty()),
                        notFound = false,
                    )
                }
            }

            is Data.Failed -> updateState {
                val err = IllegalStateException(data.message)
                it.copy(
                    game = LCE.Error(LOAD_OP, err),
                    tracks = LCE.Error(LOAD_OP, err),
                    artists = LCE.Error(LOAD_OP, err),
                    notFound = false,
                )
            }
        }
    }

    private companion object {
        const val LOAD_OP = "game_detail.load"
    }
}
