package net.sigmabeta.chipbox.common.playerstatus.api

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.logging.Hatchet

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class PlayerStatusViewModel @Inject constructor(
    private val director: Director,
    private val hatchet: Hatchet,
) : ViewModel(),
    ActionSink {

    val state: StateFlow<PlayerStatusState> = combine(
        director.metadataState(),
        director.playbackState(),
    ) { track, playback ->
        if (track == null) {
            PlayerStatusState.Empty
        } else {
            val playerState = playback.state
            PlayerStatusState(
                visible = playerState != PlayerState.IDLE && playerState != PlayerState.STOPPED,
                isPlaying = playerState.isPlaying(),
                isBuffering = playerState == PlayerState.BUFFERING,
                isError = playerState == PlayerState.ERROR,
                title = track.title,
                artistsCaption = track.artists?.joinToString(", ") { it.name }.orEmpty(),
                artwork = SourceInfo(info = track.game?.photoUrl),
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = PlayerStatusState.Empty,
    )

    /**
     * Funnels every mini-player interaction through one logged entry point — the same shape as
     * [net.sigmabeta.chipbox.common.ui.list.api.ChipboxListViewModel.sendAction], which this VM
     * can't inherit from (it owns a [PlayerStatusState], not a `ListState`).
     */
    override fun sendAction(action: SageAction) {
        hatchet.v("${this::class.simpleName} action: $action")
        when (action) {
            PlayerStatusAction.PlayPauseClicked -> togglePlayPause()

            // Navigating to Now Playing is the host's job (it pushes onto the active tab's
            // navigator via the onClick callback in ChipboxNavHost); here we only log it.
            PlayerStatusAction.CardClicked -> Unit

            else -> Unit
        }
    }

    private fun togglePlayPause() {
        if (state.value.isPlaying) {
            director.pause()
        } else {
            director.play()
        }
    }

    private fun PlayerState.isPlaying(): Boolean = when (this) {
        PlayerState.PLAYING,
        PlayerState.BUFFERING,
        PlayerState.ENDING -> true

        PlayerState.PAUSED,
        PlayerState.ERROR,
        PlayerState.IDLE,
        PlayerState.STOPPED -> false
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
