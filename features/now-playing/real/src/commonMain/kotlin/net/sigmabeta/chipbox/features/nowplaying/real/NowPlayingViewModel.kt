package net.sigmabeta.chipbox.features.nowplaying.real

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.favorites.FavoritesRepository
import net.sigmabeta.chipbox.features.artistdetail.ArtistDetail
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.features.gamesforplatform.GamesForPlatform
import net.sigmabeta.chipbox.features.playlists.Playlists
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.RepeatMode
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.PlayerErrorEvent
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.player.director.SessionRequest
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.common.ui.freeform.api.ChipboxFreeformViewModel
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

/** How many recent errors the now-playing error log keeps visible at once. */
private const val MAX_VISIBLE_ERRORS = 3

/** How long after the most recent error the whole error log clears itself. */
private const val ERROR_AUTO_CLEAR_MS = 10_000L

/** How long the context menu stays open without interaction before auto-returning to NONE. */
private const val CONTEXT_MENU_TIMEOUT_MS = 5_000L

/** Max length of the game/title affixes prefixed to an error message before they're ellipsized. */
private const val ERROR_AFFIX_MAX_LENGTH = 10

/** Cap [this] at [ERROR_AFFIX_MAX_LENGTH] characters, appending an ellipsis when truncated. */
private fun String.ellipsize(): String =
    if (length > ERROR_AFFIX_MAX_LENGTH) take(ERROR_AFFIX_MAX_LENGTH) + "…" else this

@OptIn(ExperimentalCoroutinesApi::class)
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class NowPlayingViewModel @Inject constructor(
    private val director: Director,
    private val repository: Repository,
    private val favorites: FavoritesRepository,
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

    /**
     * The pending "auto-dismiss the context menu" job. (Re)started whenever the menu opens or the
     * user interacts with it, so [CONTEXT_MENU_TIMEOUT_MS] of inactivity returns to NONE; cancelled
     * when the menu closes or the screen leaves the foreground.
     */
    private var contextMenuTimerJob: Job? = null

    /** Resolved track metadata keyed by id; the queue re-emits on reorder but membership doesn't
     *  change, so a drag never refetches. */
    private val trackCache = mutableMapOf<Long, Track>()

    /** Latest queue order (ids) — maps a tapped track id to the position [SessionRequest.PlayPosition]
     *  expects, robust to any row that failed to resolve. */
    private var setlistIds: List<Long> = emptyList()

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
        viewModelScope.launch {
            director.metadataState().flatMapLatest { track ->
                if (track == null) flowOf(false) else favorites.isTrackFavorite(track.id)
            }.collect { favorite ->
                updateState { it.copy(trackFavorite = favorite) }
            }
        }
        viewModelScope.launch {
            director.setlistState().collect { ids ->
                setlistIds = ids
                val resolved = ids.mapNotNull { id ->
                    trackCache[id] ?: repository.getTrack(id, withGame = true)?.also { trackCache[id] = it }
                }
                updateState { it.copy(setlistTracks = resolved) }
            }
        }
    }

    override fun handleAction(action: SageAction) {
        when (action) {
            NowPlayingAction.PlayPauseClicked -> togglePlayPause()

            NowPlayingAction.SkipForwardClicked -> director.request(SessionRequest.SkipForward)

            NowPlayingAction.SkipBackClicked -> director.request(SessionRequest.SkipBack)

            // Fired from the CONTROLS menu row; toggling counts as an interaction, so bump the timer.
            NowPlayingAction.ShuffleClicked -> {
                director.request(SessionRequest.SetShuffled(state.value.session?.shuffled != true))
                bumpContextMenuTimer()
            }

            NowPlayingAction.RepeatClicked -> {
                val current = state.value.session?.repeatMode ?: RepeatMode.OFF
                director.request(SessionRequest.SetRepeatMode(current.next()))
                bumpContextMenuTimer()
            }

            // Toggling counts as an interaction, so keep the menu open by bumping the timer.
            NowPlayingAction.AddToFavoritesClicked -> {
                toggleTrackFavorite()
                bumpContextMenuTimer()
            }

            // Close the menu and hand the playing track to the playlist picker.
            NowPlayingAction.AddToPlaylistClicked -> {
                val trackId = state.value.track?.id
                closeContextMenu()
                if (trackId != null) emit(ChipboxEvent.NavigateTo(Playlists(listOf(trackId))))
            }

            // Hand the whole current setlist (queue order) to the playlist picker.
            NowPlayingAction.AddSetlistToPlaylistClicked ->
                if (setlistIds.isNotEmpty()) emit(ChipboxEvent.NavigateTo(Playlists(setlistIds)))

            NowPlayingAction.BackClicked -> emit(ChipboxEvent.NavigateBack)

            NowPlayingAction.PlayerSettingsClicked -> emit(
                // Placeholder until a real Player Settings destination screen exists.
                ChipboxEvent.ShowSnackbar("Player settings coming soon.")
            )

            is NowPlayingAction.SeekRequested -> director.request(SessionRequest.Seek(action.positionMs))

            is NowPlayingAction.DismissErrorClicked -> dismissError(action.id)

            NowPlayingAction.TrackInfoClicked -> showContextMenu(ContextMenuMode.LINKS)

            NowPlayingAction.MenuClicked -> showContextMenu(ContextMenuMode.CONTROLS)

            // Toggle the reorderable setlist in/out of the InfoContainer slot.
            NowPlayingAction.SetlistClicked -> toggleSetlist()

            is NowPlayingAction.SetlistTrackClicked -> {
                val position = setlistIds.indexOf(action.trackId)
                if (position >= 0) director.request(SessionRequest.PlayPosition(position))
            }

            is NowPlayingAction.SetlistTrackRemoved -> {
                val position = setlistIds.indexOf(action.trackId)
                // The active track isn't removable (the UI hides the affordance); guard anyway.
                if (position >= 0 && action.trackId != state.value.track?.id) {
                    director.request(SessionRequest.RemoveTrack(position))
                }
            }

            // Drop from the inline reorderable list; the director owns the canonical order.
            is SageAction.Reorder ->
                director.request(SessionRequest.Reorder(action.fromIndex, action.toIndex))

            NowPlayingAction.ContextMenuBackClicked -> closeContextMenu()

            NowPlayingAction.ContextMenuGameClicked -> {
                val gameId = state.value.track?.gameId
                closeContextMenu()
                if (gameId != null) emit(ChipboxEvent.NavigateTo(GameDetail(gameId)))
            }

            NowPlayingAction.ContextMenuPlatformClicked -> {
                val platform = state.value.track?.platform
                closeContextMenu()
                if (platform != null) emit(ChipboxEvent.NavigateTo(GamesForPlatform(platform)))
            }

            NowPlayingAction.ContextMenuArtistsClicked -> {
                val artists = state.value.track?.artists.orEmpty()
                when {
                    // A single artist links straight through; several open the ARTISTS picker.
                    artists.size == 1 -> {
                        closeContextMenu()
                        emit(ChipboxEvent.NavigateTo(ArtistDetail(artists.first().id)))
                    }

                    artists.size > 1 -> showContextMenu(ContextMenuMode.ARTISTS)

                    else -> Unit
                }
            }

            is NowPlayingAction.ContextMenuArtistClicked -> {
                closeContextMenu()
                emit(ChipboxEvent.NavigateTo(ArtistDetail(action.artistId)))
            }

            // Lifecycle, delivered from the Route. Foregrounding restarts the auto-dismiss window so
            // a menu left open across a background→foreground gets a fresh 5s; backgrounding cancels
            // the pending dismiss so it doesn't fire while the user can't see the screen.
            SageAction.Resume -> if (state.value.contextMenuMode != ContextMenuMode.NONE) bumpContextMenuTimer()

            SageAction.Pause -> contextMenuTimerJob?.cancel()
        }
    }

    /** Switch the context menu to [mode] and (re)start the inactivity auto-dismiss window. Closes
     *  the setlist, which shares the InfoContainer slot. */
    private fun showContextMenu(mode: ContextMenuMode) {
        updateState { it.copy(contextMenuMode = mode, setlistVisible = false) }
        bumpContextMenuTimer()
    }

    /** Toggle the reorderable setlist in place of the whole InfoContainer. No auto-dismiss —
     *  reordering takes time — and any open context menu is closed (they share the slot). */
    private fun toggleSetlist() {
        contextMenuTimerJob?.cancel()
        updateState { it.copy(setlistVisible = !it.setlistVisible, contextMenuMode = ContextMenuMode.NONE) }
    }

    /** Close the context menu (back to track info) and stop the pending auto-dismiss. */
    private fun closeContextMenu() {
        contextMenuTimerJob?.cancel()
        updateState { it.copy(contextMenuMode = ContextMenuMode.NONE) }
    }

    /** Restart the inactivity window so the menu auto-returns to NONE after [CONTEXT_MENU_TIMEOUT_MS]. */
    private fun bumpContextMenuTimer() {
        contextMenuTimerJob?.cancel()
        contextMenuTimerJob = viewModelScope.launch {
            delay(CONTEXT_MENU_TIMEOUT_MS)
            updateState { it.copy(contextMenuMode = ContextMenuMode.NONE) }
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

    private fun toggleTrackFavorite() {
        val trackId = state.value.track?.id ?: return
        val makeFavorite = !state.value.trackFavorite
        viewModelScope.launch { favorites.setTrackFavorite(trackId, makeFavorite) }
    }

    private fun togglePlayPause() {
        val playerState = state.value.playback?.state ?: PlayerState.IDLE
        if (playerState.isPlaying()) director.request(SessionRequest.Pause) else director.request(SessionRequest.Play)
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
