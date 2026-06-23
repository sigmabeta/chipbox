package net.sigmabeta.chipbox.features.browsealltracks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.SessionRequest
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListViewModel
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.list.PaginationType
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider
import kotlin.math.max

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class BrowseAllTracksViewModel @Inject constructor(
    private val repository: Repository,
    private val director: Director,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<BrowseAllTracksState>(
    BrowseAllTracksState(),
    stringProvider,
    hatchet,
) {

    // Only one page request runs at a time: append and prepend both mutate the window list, and the
    // active check coalesces the burst of scroll signals the list emits while a page is in flight.
    private var pageJob: Job? = null

    private val pageSize: Int
        get() = (state.value.paginationType as? PaginationType.Paginating)?.pageSize
            ?: PaginationType.DEFAULT_PAGE_SIZE

    init {
        loadInitial(startOffset = 0)

        viewModelScope.launch {
            director.metadataState().collect { track ->
                updateState { it.copy(playingTrackId = track?.id) }
            }
        }
    }

    override fun handleAction(action: SageAction) {
        when (action) {
            is BrowseAllTracksAction.TrackClicked -> startSession(startingPosition = action.position)
            BrowseAllTracksAction.ShuffleAllClicked -> startSession(startingPosition = 0, shuffled = true)
            SageAction.LoadMoreRequested -> loadNextPage()
            SageAction.LoadPreviousRequested -> loadPreviousPage()
            // Entry-at-offset seam: open the window around a target page. No route sends this yet;
            // it's the hook for a future "resume where I left off" / jump-to entry, and the tests
            // use it to exercise prepend.
            is SageAction.InitWithPageNumber -> loadInitial((action.pageNumber * pageSize).toInt())
            else -> Unit
        }
    }

    /** (Re)open the window at [startOffset], discarding anything previously loaded. */
    private fun loadInitial(startOffset: Int) {
        pageJob?.cancel()
        updateState {
            it.copy(
                tracks = LCE.Loading(LOAD_OP),
                windowStart = startOffset,
                hasMoreBefore = startOffset > 0,
                hasMoreAfter = true,
                loadingPrevious = false,
                loadingMore = false,
            )
        }
        pageJob = viewModelScope.launch {
            val data = fetchPage(offset = startOffset, limit = pageSize)
            updateState { reduceInitial(it, data) }
        }
    }

    /** Append the next page below the current window. */
    private fun loadNextPage() {
        if (pageJob?.isActive == true) return
        val current = state.value
        val loaded = (current.tracks as? LCE.Content)?.data ?: return
        if (!current.hasMoreAfter) return
        val offset = current.windowStart + loaded.size
        updateState { it.copy(loadingMore = true) }
        pageJob = viewModelScope.launch {
            val data = fetchPage(offset = offset, limit = pageSize)
            updateState { reduceAppend(it, data) }
        }
    }

    /** Prepend the previous page above the current window (only meaningful when [windowStart] > 0). */
    private fun loadPreviousPage() {
        if (pageJob?.isActive == true) return
        val current = state.value
        if (current.tracks !is LCE.Content || !current.hasMoreBefore) return
        val newStart = max(0, current.windowStart - pageSize)
        val count = current.windowStart - newStart
        if (count <= 0) return
        updateState { it.copy(loadingPrevious = true) }
        pageJob = viewModelScope.launch {
            val data = fetchPage(offset = newStart, limit = count)
            updateState { reducePrepend(it, data, newStart) }
        }
    }

    private suspend fun fetchPage(offset: Int, limit: Int): Data<List<Track>> = repository
        .getAllTracks(withGame = true, limit = limit, offset = offset)
        .first { it !is Data.Loading }

    private fun reduceInitial(state: BrowseAllTracksState, data: Data<List<Track>>) = when (data) {
        is Data.Succeeded -> state.copy(
            tracks = LCE.Content(data.data),
            loadingPrevious = false,
            loadingMore = false,
            hasMoreAfter = data.data.size >= pageSize,
        )
        Data.Empty -> state.copy(
            tracks = LCE.Content(emptyList()),
            loadingPrevious = false,
            loadingMore = false,
            hasMoreAfter = false,
        )
        is Data.Failed -> state.copy(tracks = LCE.Error(LOAD_OP, IllegalStateException(data.message)))
        Data.Loading -> state
    }

    private fun reduceAppend(state: BrowseAllTracksState, data: Data<List<Track>>) = when (data) {
        is Data.Succeeded -> {
            val current = (state.tracks as? LCE.Content)?.data.orEmpty()
            state.copy(
                tracks = LCE.Content(current + data.data),
                loadingMore = false,
                hasMoreAfter = data.data.size >= pageSize,
            )
        }
        Data.Empty -> state.copy(loadingMore = false, hasMoreAfter = false)
        // Keep what's already shown; just drop the footer spinner.
        is Data.Failed -> state.copy(loadingMore = false)
        Data.Loading -> state
    }

    private fun reducePrepend(state: BrowseAllTracksState, data: Data<List<Track>>, newStart: Int) = when (data) {
        is Data.Succeeded -> {
            val current = (state.tracks as? LCE.Content)?.data.orEmpty()
            state.copy(
                tracks = LCE.Content(data.data + current),
                windowStart = newStart,
                loadingPrevious = false,
                hasMoreBefore = newStart > 0,
            )
        }
        Data.Empty -> state.copy(loadingPrevious = false, hasMoreBefore = false)
        is Data.Failed -> state.copy(loadingPrevious = false)
        Data.Loading -> state
    }

    private fun startSession(startingPosition: Int, shuffled: Boolean = false) {
        val session = Session(
            type = SessionType.ALL_TRACKS,
            contentId = 0L,
            startingPosition = startingPosition,
            shuffled = shuffled,
        )
        director.request(SessionRequest.Start(session))
    }

    private companion object {
        const val LOAD_OP = "browse_all_tracks.load"
    }
}
