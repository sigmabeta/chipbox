package net.sigmabeta.chipbox.features.nowplaying.real

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.ui.freeform.ChipboxFreeformViewModel
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val director: Director,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxFreeformViewModel<NowPlayingState, NowPlayingModel>(
    NowPlayingState(),
    stringProvider,
    hatchet,
) {
    init {
        viewModelScope.launch {
            director.metadataState().collect { track ->
                updateState { it.copy(track = track) }
            }
        }
        viewModelScope.launch {
            director.playbackState().collect { playback ->
                updateState { it.copy(playback = playback) }
            }
        }
        viewModelScope.launch {
            director.sessionState().collect { session ->
                updateState { it.copy(session = session) }
            }
        }
    }

    override fun handleAction(action: SageAction) {
        when (action) {
            NowPlayingAction.PlayPauseClicked -> togglePlayPause()

            NowPlayingAction.SkipForwardClicked -> director.skipForward()

            NowPlayingAction.SkipBackClicked -> director.skipBack()

            NowPlayingAction.ShuffleClicked -> {
                director.setShuffled(state.value.session?.shuffled != true)
            }

            NowPlayingAction.RepeatClicked -> {
                updateState { it.copy(repeatMode = it.repeatMode.next()) }
            }

            NowPlayingAction.BackClicked -> emit(ChipboxEvent.NavigateBack)

            NowPlayingAction.PlayerSettingsClicked -> emit(
                // Placeholder until a real Player Settings destination screen exists.
                ChipboxEvent.ShowSnackbar("Player settings coming soon.")
            )

            is NowPlayingAction.SeekRequested -> director.seek(action.positionMs)
        }
    }

    private fun togglePlayPause() {
        val playerState = state.value.playback?.state ?: PlayerState.IDLE
        if (playerState.isPlaying()) director.pause() else director.play()
    }

    private fun PlayerState.isPlaying(): Boolean = when (this) {
        PlayerState.PLAYING,
        PlayerState.PRELOADING,
        PlayerState.BUFFERING,
        PlayerState.FAST_FORWARDING,
        PlayerState.REWINDING,
        PlayerState.ENDING -> true

        PlayerState.PAUSED,
        PlayerState.ERROR,
        PlayerState.IDLE,
        PlayerState.STOPPED -> false
    }
}
