package net.sigmabeta.chipbox.features.search.real

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.features.artistdetail.ArtistDetail
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.ui.freeform.ChipboxFreeformViewModel
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

// Mirrors VGLS's search timings.
internal const val MIN_QUERY_LENGTH = 3
private const val DEBOUNCE_MS = 300L
private const val HISTORY_RECORD_DELAY_MS = 3_000L

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: Repository,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxFreeformViewModel<SearchState, SearchModel>(
    SearchState(),
    stringProvider,
    hatchet,
) {
    // Cancelled/restarted on every settled query, so only the query the user actually
    // lingers on gets recorded (VGLS's startHistoryTimer behaviour).
    private var historyTimer: Job? = null

    init {
        observeSearchHistory()
        observeQueryForSubmit()
        observeGameResults()
        observeSongResults()
        observeArtistResults()
    }

    override fun handleAction(action: SageAction) {
        when (action) {
            // Clearing the box returns to the history view (drop the submitted query).
            is SearchAction.QueryChanged -> updateState {
                it.copy(
                    query = action.query,
                    submittedQuery = if (action.query.isBlank()) "" else it.submittedQuery,
                )
            }
            // Refill the box; the debounce observer submits it from here.
            is SearchAction.HistoryClicked -> updateState { it.copy(query = action.query) }
            is SearchAction.HistoryRemoved -> viewModelScope.launch {
                repository.removeSearchHistory(action.id)
            }
            is SearchAction.GameClicked -> emit(
                ChipboxEvent.NavigateTo(GameDetail(action.gameId))
            )
            is SearchAction.SongClicked -> action.gameId?.let {
                emit(ChipboxEvent.NavigateTo(GameDetail(it)))
            }
            is SearchAction.ArtistClicked -> emit(
                ChipboxEvent.NavigateTo(ArtistDetail(action.artistId))
            )
            SearchAction.BackClicked -> emit(ChipboxEvent.NavigateBack)
        }
    }

    private fun observeSearchHistory() {
        viewModelScope.launch {
            repository.getSearchHistory().collect { data ->
                when (data) {
                    is Data.Succeeded -> updateState { it.copy(history = data.data) }
                    Data.Empty -> updateState { it.copy(history = emptyList()) }
                    // Keep the last list on transient loading / failure.
                    Data.Loading, is Data.Failed -> Unit
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeGameResults() {
        state
            .map { it.submittedQuery }
            .distinctUntilChanged()
            .flatMapLatest { query ->
                if (query.isBlank()) flowOf(Data.Empty) else repository.searchGames(query)
            }
            .onEach { data ->
                updateState {
                    when (data) {
                        Data.Loading -> it.copy(gamesLoading = true)
                        Data.Empty -> it.copy(gameResults = emptyList(), gamesLoading = false)
                        is Data.Succeeded -> it.copy(
                            gameResults = data.data,
                            gamesLoading = false,
                        )
                        is Data.Failed -> it.copy(
                            gameResults = emptyList(),
                            gamesLoading = false,
                        )
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSongResults() {
        state
            .map { it.submittedQuery }
            .distinctUntilChanged()
            .flatMapLatest { query ->
                if (query.isBlank()) flowOf(Data.Empty) else repository.searchSongs(query)
            }
            .onEach { data ->
                updateState {
                    when (data) {
                        Data.Loading -> it.copy(songsLoading = true)
                        Data.Empty -> it.copy(songResults = emptyList(), songsLoading = false)
                        is Data.Succeeded -> it.copy(
                            songResults = data.data,
                            songsLoading = false,
                        )
                        is Data.Failed -> it.copy(
                            songResults = emptyList(),
                            songsLoading = false,
                        )
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeArtistResults() {
        state
            .map { it.submittedQuery }
            .distinctUntilChanged()
            .flatMapLatest { query ->
                if (query.isBlank()) flowOf(Data.Empty) else repository.searchArtists(query)
            }
            .onEach { data ->
                updateState {
                    when (data) {
                        Data.Loading -> it.copy(artistsLoading = true)
                        Data.Empty -> it.copy(artistResults = emptyList(), artistsLoading = false)
                        is Data.Succeeded -> it.copy(
                            artistResults = data.data,
                            artistsLoading = false,
                        )
                        is Data.Failed -> it.copy(
                            artistResults = emptyList(),
                            artistsLoading = false,
                        )
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    @OptIn(FlowPreview::class)
    private fun observeQueryForSubmit() {
        state
            .map { it.query.trim() }
            .distinctUntilChanged()
            .debounce(DEBOUNCE_MS)
            .onEach { query ->
                historyTimer?.cancel()
                if (query.length >= MIN_QUERY_LENGTH) {
                    // Run the search now (observeResults keys off submittedQuery).
                    updateState { it.copy(submittedQuery = query) }
                    // Only the history write waits the full linger window, so a query
                    // typed through on the way to another one isn't recorded.
                    historyTimer = viewModelScope.launch {
                        delay(HISTORY_RECORD_DELAY_MS)
                        repository.addSearchHistory(query)
                    }
                }
            }
            .launchIn(viewModelScope)
    }
}
