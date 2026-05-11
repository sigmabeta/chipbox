package net.sigmabeta.chipbox.features.gamedetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.ui.list.ChipboxListViewModel
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

@HiltViewModel
class GameDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: Repository,
    private val director: Director,
    stringProvider: StringProvider,
    private val hatchet: Hatchet,
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
    }

    override fun handleAction(action: SageAction) {
        when (action) {
            GameDetailAction.PlayAllClicked -> startSession(startingPosition = 0)
            GameDetailAction.ShuffleAllClicked -> startSession(startingPosition = randomTrackIndex())
            is GameDetailAction.TrackClicked -> startSession(startingPosition = action.position)
            is GameDetailAction.ArtistClicked -> hatchet.v(
                "Artist ${action.id} clicked; ArtistDetail destination not wired yet."
            )
            else -> Unit
        }
    }

    // Director plays the setlist in fixed order from this index onwards; true
    // shuffled-order playback needs Session/Director support that doesn't exist yet.
    private fun randomTrackIndex(): Int {
        val tracks = state.value.tracks
        val size = if (tracks is LCE.Content) tracks.data.size else 0
        return if (size > 0) kotlin.random.Random.nextInt(size) else 0
    }

    private fun startSession(startingPosition: Int) {
        director.start(
            Session(
                type = SessionType.GAME,
                contentId = args.id,
                startingPosition = startingPosition,
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
                )
            }
            Data.Empty -> updateState {
                it.copy(
                    game = LCE.Uninitialized,
                    tracks = LCE.Content(emptyList()),
                    artists = LCE.Content(emptyList()),
                )
            }
            is Data.Succeeded -> {
                val game = data.data
                if (game == null) {
                    updateState {
                        it.copy(
                            game = LCE.Uninitialized,
                            tracks = LCE.Content(emptyList()),
                            artists = LCE.Content(emptyList()),
                        )
                    }
                } else {
                    updateState {
                        it.copy(
                            game = LCE.Content(game),
                            tracks = LCE.Content(game.tracks.orEmpty()),
                            artists = LCE.Content(game.artists.orEmpty()),
                        )
                    }
                }
            }
            is Data.Failed -> updateState {
                val err = IllegalStateException(data.message)
                it.copy(
                    game = LCE.Error(LOAD_OP, err),
                    tracks = LCE.Error(LOAD_OP, err),
                    artists = LCE.Error(LOAD_OP, err),
                )
            }
        }
    }

    private companion object {
        const val LOAD_OP = "game_detail.load"
    }
}
