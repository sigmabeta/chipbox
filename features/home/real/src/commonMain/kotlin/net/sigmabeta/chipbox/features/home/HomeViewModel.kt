package net.sigmabeta.chipbox.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.appcomm.ChipboxEvent.NavigateTo
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListViewModel
import net.sigmabeta.chipbox.contentsource.LibrarySource
import net.sigmabeta.chipbox.features.artistdetail.ArtistDetail
import net.sigmabeta.chipbox.features.folderpicker.FolderPicker
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.features.home.module.HomeModule
import net.sigmabeta.chipbox.features.nowplaying.NowPlaying
import net.sigmabeta.chipbox.features.rescanstatus.RescanStatus
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.player.director.SessionRequest
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class HomeViewModel @Inject constructor(
    modules: Set<HomeModule>,
    private val repository: Repository,
    private val director: Director,
    private val librarySource: LibrarySource,
    private val scanner: Scanner,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<HomeState>(
    HomeState(
        sections = modules
            .sortedBy { it.priority }
            .map { HomeSectionState(it.id, it.priority, LCE.Uninitialized, it.showHeader) }
            .toImmutableList(),
    ),
    stringProvider,
    hatchet,
) {

    init {
        // Each module patches only its own slot, so modules render independently as their
        // data arrives — no `combine` / `flatMapMerge` orchestration needed.
        modules.forEach { module ->
            viewModelScope.launch {
                module.state().collect { lce ->
                    updateState { state ->
                        state.copy(
                            sections = state.sections.map { section ->
                                if (section.id == module.id) section.copy(lce = lce) else section
                            }.toImmutableList(),
                        )
                    }
                }
            }
        }

        // Folder count only feeds the empty state's copy ("add your first folder" vs "your
        // folders are empty"); the modules themselves decide whether there's content to show.
        viewModelScope.launch {
            librarySource.locations.collect { locations ->
                updateState { it.copy(libraryFolderCount = locations.size) }
            }
        }

        // Drive the first-run empty state off the library actually being empty rather than the
        // content modules going quiet. A single-track probe keeps this cheap and reactive; it
        // re-emits as the library fills or is cleared.
        viewModelScope.launch {
            repository.getAllTracks(limit = 1).collect { tracks ->
                val hasTracks = when (tracks) {
                    is Data.Succeeded -> true

                    Data.Empty -> false

                    // Leave the prior verdict in place while loading or on a transient failure, so
                    // the empty state neither flashes on startup nor flickers on a failed refresh.
                    Data.Loading, is Data.Failed -> return@collect
                }
                updateState { it.copy(hasTracks = hasTracks) }
            }
        }
    }

    override fun handleAction(action: SageAction) {
        when (action) {
            is HomeAction.GameClicked -> emit(NavigateTo(GameDetail(action.id)))

            is HomeAction.ArtistClicked -> emit(NavigateTo(ArtistDetail(action.id)))

            is HomeAction.SongClicked -> playSong(action.id)

            HomeAction.RandomSongClicked -> playRandomSong()

            HomeAction.RandomGameClicked -> navigateToRandomGame()

            HomeAction.RandomArtistClicked -> navigateToRandomArtist()

            HomeAction.AddFolderClicked -> emit(NavigateTo(FolderPicker))

            HomeAction.RescanLibraryClicked -> {
                scanner.startScan()
                emit(NavigateTo(RescanStatus))
            }

            HomeAction.NowPlayingCardClicked -> emit(NavigateTo(NowPlaying))

            HomeAction.NowPlayingPlayPauseClicked -> togglePlayPause()

            // The VM doesn't decide what "card visible" means for the rest of the app — it
            // just publishes the request. Someone upstream listens and chooses whether to
            // honour it (in practice, the chrome controller in ChipboxAppUi).
            HomeAction.NowPlayingCardAppeared ->
                emit(ChipboxEvent.RequestMiniPlayerVisibility(visible = false))

            HomeAction.NowPlayingCardDisappeared ->
                emit(ChipboxEvent.RequestMiniPlayerVisibility(visible = true))

            else -> Unit
        }
    }

    private fun togglePlayPause() = viewModelScope.launch {
        // Read playback state once via .first() since the director's StateFlow is shared and
        // replays its latest value to a fresh collector immediately. Matches the toggle logic
        // in PlayerStatusViewModel (PLAYING/BUFFERING/ENDING → pause, otherwise → play).
        val state = director.playbackState().firstOrNull()?.state ?: return@launch
        when (state) {
            PlayerState.PLAYING, PlayerState.BUFFERING, PlayerState.ENDING -> director.request(SessionRequest.Pause)
            PlayerState.PAUSED, PlayerState.IDLE, PlayerState.STOPPED, PlayerState.ERROR -> director.request(SessionRequest.Play)
        }
    }

    // The repository handles the random pick itself (Room: `ORDER BY RANDOM() LIMIT 1`,
    // RemoteRepository: GET /api/random/<thing>). The earlier `getAllX().randomOrNull()`
    // would round-trip the entire catalog on every click — fine against a local DB, a
    // multi-MB transfer over HTTP against the server-backed JS target.
    private fun playRandomSong() = viewModelScope.launch {
        val pick = repository.getRandomTrack() ?: return@launch
        playSong(pick.id)
    }

    private fun playSong(trackId: Long) {
        director.request(SessionRequest.Start(Session(type = SessionType.SINGLE_TRACK, contentId = trackId)))
    }

    private fun navigateToRandomGame() = viewModelScope.launch {
        val pick = repository.getRandomGame() ?: return@launch
        emit(NavigateTo(GameDetail(pick.id)))
    }

    private fun navigateToRandomArtist() = viewModelScope.launch {
        val pick = repository.getRandomArtist() ?: return@launch
        emit(NavigateTo(ArtistDetail(pick.id)))
    }
}

/**
 * Drains the receiver flow up to the first non-Loading emission and returns its payload —
 * `null` on `Data.Empty`/`Data.Failed`. Repository flows are cold, so the lookup is fresh
 * each call: the RNG click-action contract is "re-randomize on every tap".
 */
private suspend fun <T> Flow<Data<T>>.firstSucceeded(): T? {
    val terminal = firstOrNull { it !is Data.Loading } ?: return null
    return (terminal as? Data.Succeeded<T>)?.data
}
