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
import net.sigmabeta.chipbox.ui.list.ChipboxListViewModel
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

// Mirrors VGLS's search timings.
internal const val MIN_QUERY_LENGTH = 3
private const val DEBOUNCE_MS = 300L
private const val HISTORY_RECORD_DELAY_MS = 3_000L

private const val OP_HISTORY = "search.history"
private const val OP_GAMES = "search.games"
private const val OP_SONGS = "search.songs"
private const val OP_ARTISTS = "search.artists"

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: Repository,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<SearchState>(
    SearchState(),
    stringProvider,
    hatchet,
) {
    // Cancelled/restarted on every settled query, so only the query the user lingers on
    // is considered for recording (VGLS's startHistoryTimer behaviour).
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
                updateState { it.copy(history = data.toLce(OP_HISTORY)) }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeGameResults() {
        searchResults({ repository.searchGames(it) }, OP_GAMES)
            .onEach { lce -> updateState { it.copy(gameResults = lce) } }
            .launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSongResults() {
        searchResults({ repository.searchSongs(it) }, OP_SONGS)
            .onEach { lce -> updateState { it.copy(songResults = lce) } }
            .launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeArtistResults() {
        searchResults({ repository.searchArtists(it) }, OP_ARTISTS)
            .onEach { lce -> updateState { it.copy(artistResults = lce) } }
            .launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> searchResults(
        query: (String) -> kotlinx.coroutines.flow.Flow<Data<List<T>>>,
        operation: String,
    ) = state
        .map { it.submittedQuery }
        .distinctUntilChanged()
        .flatMapLatest { q -> if (q.isBlank()) flowOf(Data.Empty) else query(q) }
        .map { it.toLce(operation) }

    @OptIn(FlowPreview::class)
    private fun observeQueryForSubmit() {
        state
            .map { it.query.trim() }
            .distinctUntilChanged()
            .debounce(DEBOUNCE_MS)
            .onEach { query ->
                historyTimer?.cancel()
                if (query.length >= MIN_QUERY_LENGTH) {
                    // Run the search now (the result observers key off submittedQuery).
                    updateState { it.copy(submittedQuery = query) }
                    // Record to history only after the linger window, and only if the
                    // search actually found something — matches VGLS.
                    historyTimer = viewModelScope.launch {
                        delay(HISTORY_RECORD_DELAY_MS)
                        val s = state.value
                        if (s.submittedQuery == query && s.hasAnyResults()) {
                            repository.addSearchHistory(query)
                        }
                    }
                }
            }
            .launchIn(viewModelScope)
    }
}

private fun <T> Data<List<T>>.toLce(operation: String): LCE<List<T>> = when (this) {
    Data.Loading -> LCE.Loading(operation)
    Data.Empty -> LCE.Content(emptyList())
    is Data.Succeeded -> LCE.Content(data)
    is Data.Failed -> LCE.Error(operation, IllegalStateException(message))
}

private fun LCE<List<*>>.hasContent(): Boolean = this is LCE.Content && data.isNotEmpty()

private fun SearchState.hasAnyResults(): Boolean = gameResults.hasContent() || songResults.hasContent() || artistResults.hasContent()
