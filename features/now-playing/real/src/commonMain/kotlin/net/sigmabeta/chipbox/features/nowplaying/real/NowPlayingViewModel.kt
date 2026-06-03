package net.sigmabeta.chipbox.features.nowplaying.real

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.player.common.RepeatMode
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.PlayerErrorEvent
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.common.ui.freeform.api.ChipboxFreeformViewModel
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

/** How many recent errors the now-playing error log keeps visible at once. */
private const val MAX_VISIBLE_ERRORS = 3

/** How long after the most recent error the whole error log clears itself. */
private const val ERROR_AUTO_CLEAR_MS = 10_000L

/** Max length of the game/title affixes prefixed to an error message before they're ellipsized. */
private const val ERROR_AFFIX_MAX_LENGTH = 10

/** Cap [this] at [ERROR_AFFIX_MAX_LENGTH] characters, appending an ellipsis when truncated. */
private fun String.ellipsize(): String =
    if (length > ERROR_AFFIX_MAX_LENGTH) take(ERROR_AFFIX_MAX_LENGTH) + "…" else this

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class NowPlayingViewModel @Inject constructor(
    private val director: Director,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxFreeformViewModel<NowPlayingState, NowPlayingModel>(
    NowPlayingState(),
    stringProvider,
    hatchet,
) {
    /** Monotonic id source for error rows, so each is stably keyed and individually dismissable. */
    private var nextErrorId = 0L

    /** The pending "clear the whole log" job; restarted on each new error so the 10s window slides. */
    private var errorClearJob: Job? = null

    init {
        viewModelScope.launch {
            director.metadataState().collect { track ->
                updateState { it.copy(track = track) }
            }
        }
        viewModelScope.launch {
            director.errorEvents().collect { event ->
                addError(event)
            }
        }
        viewModelScope.launch {
            director.playbackState().collect { playback ->
                // No live session to show — leave the screen rather than render a dead player.
                // IDLE is the pre-session seed the Director emits before anything plays: the
                // common cause is Android killing the app's process and later recreating this
                // screen against a brand-new, sessionless Director. STOPPED is the terminal state
                // the player lands in once playback finishes or is stopped. ENDING still has audio
                // draining, so it's deliberately excluded.
                if (playback.state == PlayerState.IDLE || playback.state == PlayerState.STOPPED) {
                    emit(ChipboxEvent.NavigateBack)
                }
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
                val current = state.value.session?.repeatMode ?: RepeatMode.OFF
                director.setRepeatMode(current.next())
            }

            NowPlayingAction.BackClicked -> emit(ChipboxEvent.NavigateBack)

            NowPlayingAction.PlayerSettingsClicked -> emit(
                // Placeholder until a real Player Settings destination screen exists.
                ChipboxEvent.ShowSnackbar("Player settings coming soon.")
            )

            is NowPlayingAction.SeekRequested -> director.seek(action.positionMs)

            is NowPlayingAction.DismissErrorClicked -> dismissError(action.id)
        }
    }

    /** Append a new error to the log (capped at [MAX_VISIBLE_ERRORS], newest last) and (re)start
     *  the sliding auto-clear window so the section disappears [ERROR_AUTO_CLEAR_MS] after the
     *  most recent error. Uses the [PlayerErrorEvent.track] the director resolved at emit time
     *  rather than the on-screen track — those can disagree when the speaker hasn't yet emitted
     *  a `TrackChange` for the failing track. */
    private fun addError(event: PlayerErrorEvent) {
        val track = event.track
        val gameName = track?.game?.title?.takeIf { it.isNotBlank() }?.ellipsize()
        val title = track?.title?.takeIf { it.isNotBlank() }?.ellipsize()
        val prefix = listOfNotNull(gameName, title)
            .joinToString(" - ")
            .let { if (it.isNotEmpty()) "$it: " else "" }
        val item = NowPlayingError(
            id = nextErrorId++,
            message = "$prefix${event.message}",
        )
        updateState { it.copy(errors = (it.errors + item).takeLast(MAX_VISIBLE_ERRORS)) }

        errorClearJob?.cancel()
        errorClearJob = viewModelScope.launch {
            delay(ERROR_AUTO_CLEAR_MS)
            updateState { it.copy(errors = emptyList()) }
        }
    }

    private fun dismissError(id: Long) {
        updateState { state -> state.copy(errors = state.errors.filterNot { it.id == id }) }
    }

    private fun togglePlayPause() {
        val playerState = state.value.playback?.state ?: PlayerState.IDLE
        if (playerState.isPlaying()) director.pause() else director.play()
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
}
