package net.sigmabeta.chipbox.playerstatus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.sage.images.SourceInfo

@HiltViewModel
class PlayerStatusViewModel @Inject constructor(
    private val director: Director,
) : ViewModel() {

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

    fun onPlayPauseClicked() {
        if (state.value.isPlaying) {
            director.pause()
        } else {
            director.play()
        }
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

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
