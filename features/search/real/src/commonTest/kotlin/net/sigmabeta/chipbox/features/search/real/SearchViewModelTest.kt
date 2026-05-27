package net.sigmabeta.chipbox.features.search.real

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.features.artistdetail.ArtistDetail
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.SearchHistory
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.director.fake.FakeDirector
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.fake.FakeRepository
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [SearchViewModel] gates queries behind a 300ms debounce and gates history-recording behind a
 * 3-second linger window, both running on viewModelScope. Tests share one
 * [TestCoroutineScheduler] between Main and the runTest body so `advanceTimeBy` actually moves
 * the VM's timers — without this, the debounce never fires and submittedQuery stays empty.
 *
 * For each test we layer on top of [FakeRepository] only the methods the test cares about (via
 * Kotlin interface delegation), so adding a new Repository method tomorrow doesn't break every
 * test today.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = UnconfinedTestDispatcher(scheduler)

    @BeforeTest fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- history flow ----

    @Test
    fun `getSearchHistory Succeeded lands in state history as LCE Content`() = runTest(dispatcher) {
        val historyFlow = sharedFlowOf<Data<List<SearchHistory>>>().also {
            it.tryEmit(Data.Succeeded(listOf(SearchHistory(id = 1, query = "ff7"))))
        }
        val vm = newViewModel(repository = repoOverridingHistory(historyFlow))

        val state = vm.state.first { it.history is LCE.Content }
        val content = state.history as LCE.Content
        assertEquals("ff7", content.data.single().query)
    }

    @Test
    fun `getSearchHistory Loading shows up as LCE Loading with the history operation tag`() = runTest(dispatcher) {
        val historyFlow = sharedFlowOf<Data<List<SearchHistory>>>().also { it.tryEmit(Data.Loading) }
        val vm = newViewModel(repository = repoOverridingHistory(historyFlow))

        val state = vm.state.first { it.history is LCE.Loading }
        assertEquals("search.history", (state.history as LCE.Loading).operationName)
    }

    // ---- query debounce → submission ----

    @Test
    fun `QueryChanged below MIN_QUERY_LENGTH never submits even after debounce settles`() = runTest(dispatcher) {
        // The threshold exists so 1-2 character queries don't spam the repo; verify settling
        // through the debounce window does NOT bump submittedQuery.
        val vm = newViewModel()
        vm.sendAction(SearchAction.QueryChanged("ff"))
        advanceTimeBy(DEBOUNCE_MS * 2)
        assertEquals("ff", vm.state.value.query)
        assertEquals("", vm.state.value.submittedQuery)
    }

    @Test
    fun `QueryChanged at MIN_QUERY_LENGTH submits after the debounce window`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.sendAction(SearchAction.QueryChanged("ff7"))
        advanceTimeBy(DEBOUNCE_MS + 1)
        assertEquals("ff7", vm.state.first { it.submittedQuery == "ff7" }.submittedQuery)
    }

    @Test
    fun `rapid QueryChanged emissions only submit the last value (debounce semantics)`() = runTest(dispatcher) {
        // The classic "drop intermediate keystrokes" guarantee — only the value the user
        // settles on for DEBOUNCE_MS reaches the repo.
        val vm = newViewModel()
        vm.sendAction(SearchAction.QueryChanged("fina"))
        advanceTimeBy(100)
        vm.sendAction(SearchAction.QueryChanged("final"))
        advanceTimeBy(100)
        vm.sendAction(SearchAction.QueryChanged("final f"))
        advanceTimeBy(DEBOUNCE_MS + 1)
        assertEquals("final f", vm.state.value.submittedQuery)
    }

    @Test
    fun `QueryChanged with blank clears the submittedQuery (back to history view)`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.sendAction(SearchAction.QueryChanged("ff7"))
        advanceTimeBy(DEBOUNCE_MS + 1)
        vm.state.first { it.submittedQuery == "ff7" }

        vm.sendAction(SearchAction.QueryChanged(""))
        // Submitted clears immediately on blank (no debounce — see the reducer).
        assertEquals("", vm.state.value.submittedQuery)
        assertEquals("", vm.state.value.query)
    }

    // ---- result wiring ----

    @Test
    fun `submittedQuery threads through to searchGames searchSongs searchArtists results`() = runTest(dispatcher) {
        // After the debounce fires, three searches run in parallel. Each result observer maps
        // Data.Succeeded → LCE.Content; the VM lights up all three result slots together.
        val gameHits = listOf(gameOf(1, "Chrono Trigger"))
        val songHits = listOf(trackOf(1, "Schala"))
        val artistHits = listOf(artistOf(1, "Mitsuda"))
        val repo = object : Repository by FakeRepository(emptyMap()) {
            override fun searchGames(query: String) = flowOnce<Data<List<Game>>>(Data.Succeeded(gameHits))
            override fun searchSongs(query: String) = flowOnce<Data<List<Track>>>(Data.Succeeded(songHits))
            override fun searchArtists(query: String) = flowOnce<Data<List<Artist>>>(Data.Succeeded(artistHits))
        }
        val vm = newViewModel(repository = repo)

        vm.sendAction(SearchAction.QueryChanged("mits"))
        advanceTimeBy(DEBOUNCE_MS + 1)

        val state = vm.state.first {
            it.gameResults is LCE.Content && it.songResults is LCE.Content && it.artistResults is LCE.Content
        }
        assertEquals(gameHits, (state.gameResults as LCE.Content).data)
        assertEquals(songHits, (state.songResults as LCE.Content).data)
        assertEquals(artistHits, (state.artistResults as LCE.Content).data)
    }

    @Test
    fun `Data Empty results map to LCE Content with an empty list (not Uninitialized)`() = runTest(dispatcher) {
        // Empty is "we asked and got nothing back" — distinct from "haven't asked yet". The
        // mapping must hand the screen a Content(emptyList) so the empty-state row renders.
        val repo = object : Repository by FakeRepository(emptyMap()) {
            override fun searchGames(query: String) = flowOnce<Data<List<Game>>>(Data.Empty)
        }
        val vm = newViewModel(repository = repo)
        vm.sendAction(SearchAction.QueryChanged("nope"))
        advanceTimeBy(DEBOUNCE_MS + 1)
        val state = vm.state.first { it.gameResults is LCE.Content }
        assertTrue((state.gameResults as LCE.Content).data.isEmpty())
    }

    @Test
    fun `Data Failed result becomes LCE Error tagged with the matching operation name`() = runTest(dispatcher) {
        val repo = object : Repository by FakeRepository(emptyMap()) {
            override fun searchGames(query: String) = flowOnce<Data<List<Game>>>(Data.Failed("oops"))
        }
        val vm = newViewModel(repository = repo)
        vm.sendAction(SearchAction.QueryChanged("xyz"))
        advanceTimeBy(DEBOUNCE_MS + 1)
        val state = vm.state.first { it.gameResults is LCE.Error }
        val err = state.gameResults as LCE.Error
        assertEquals("search.games", err.operationName)
        assertEquals("oops", err.error.message)
    }

    // ---- history recording (3-second linger) ----

    @Test
    fun `addSearchHistory runs only after the linger window AND when results exist`() = runTest(dispatcher) {
        val historyAdds = mutableListOf<String>()
        val gameHits = listOf(gameOf(1, "Chrono Trigger"))
        val repo = object : Repository by FakeRepository(emptyMap()) {
            override fun searchGames(query: String) = flowOnce<Data<List<Game>>>(Data.Succeeded(gameHits))
            override suspend fun addSearchHistory(query: String) {
                historyAdds += query
            }
        }
        val vm = newViewModel(repository = repo)

        vm.sendAction(SearchAction.QueryChanged("chrono"))
        advanceTimeBy(DEBOUNCE_MS + 1)
        vm.state.first { it.gameResults is LCE.Content }

        // Before the linger window: no recording yet.
        advanceTimeBy(HISTORY_RECORD_DELAY_MS - DEBOUNCE_MS - 100)
        assertTrue(historyAdds.isEmpty(), "history shouldn't record before the linger window settles")

        // After the linger window: recording fires once.
        advanceTimeBy(500)
        assertEquals(listOf("chrono"), historyAdds)
    }

    @Test
    fun `addSearchHistory is skipped when the query found nothing (no-results guard)`() = runTest(dispatcher) {
        // VGLS rule: don't pollute history with dead-end queries. Locks the
        // `hasAnyResults` check.
        val historyAdds = mutableListOf<String>()
        val repo = object : Repository by FakeRepository(emptyMap()) {
            override suspend fun addSearchHistory(query: String) {
                historyAdds += query
            }
        }
        val vm = newViewModel(repository = repo)

        vm.sendAction(SearchAction.QueryChanged("nothing-matches"))
        advanceTimeBy(DEBOUNCE_MS + HISTORY_RECORD_DELAY_MS + 100)
        assertTrue(historyAdds.isEmpty(), "history shouldn't record when no category has results")
    }

    @Test
    fun `a new query within the linger window cancels the pending history-record job`() = runTest(dispatcher) {
        // The history timer is restarted on every settled query; locked-in via the
        // historyTimer.cancel() before the new launch.
        val historyAdds = mutableListOf<String>()
        val gameHits = listOf(gameOf(1, "X"))
        val repo = object : Repository by FakeRepository(emptyMap()) {
            override fun searchGames(query: String) = flowOnce<Data<List<Game>>>(Data.Succeeded(gameHits))
            override suspend fun addSearchHistory(query: String) {
                historyAdds += query
            }
        }
        val vm = newViewModel(repository = repo)

        vm.sendAction(SearchAction.QueryChanged("aaa"))
        advanceTimeBy(DEBOUNCE_MS + 1)
        // Halfway through the 3s window, type a new query — the original "aaa" record should
        // be cancelled and a new "bbb" timer started.
        advanceTimeBy(1_500)
        vm.sendAction(SearchAction.QueryChanged("bbb"))
        advanceTimeBy(DEBOUNCE_MS + HISTORY_RECORD_DELAY_MS + 100)
        assertEquals(listOf("bbb"), historyAdds, "Only the latest query reaches addSearchHistory")
    }

    // ---- action handlers ----

    @Test
    fun `HistoryClicked refills the query so the debounce submits it`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.sendAction(SearchAction.HistoryClicked(query = "older search"))
        assertEquals("older search", vm.state.value.query)
        // And the debounce window submits it like any other query.
        advanceTimeBy(DEBOUNCE_MS + 1)
        assertEquals("older search", vm.state.first { it.submittedQuery == "older search" }.submittedQuery)
    }

    @Test
    fun `HistoryRemoved forwards the id to the repository removeSearchHistory call`() = runTest(dispatcher) {
        val removed = mutableListOf<Long>()
        val repo = object : Repository by FakeRepository(emptyMap()) {
            override suspend fun removeSearchHistory(id: Long) {
                removed += id
            }
        }
        val vm = newViewModel(repository = repo)
        vm.sendAction(SearchAction.HistoryRemoved(id = 99L))
        assertEquals(listOf(99L), removed)
    }

    @Test
    fun `GameClicked emits NavigateTo GameDetail with the right id`() = runTest(dispatcher) {
        val vm = newViewModel()
        val event = collectAndDispatch(vm, SearchAction.GameClicked(gameId = 5L))
        assertTrue(event is ChipboxEvent.NavigateTo)
        assertEquals(GameDetail(5L), event.destination)
    }

    @Test
    fun `ArtistClicked emits NavigateTo ArtistDetail with the right id`() = runTest(dispatcher) {
        val vm = newViewModel()
        val event = collectAndDispatch(vm, SearchAction.ArtistClicked(artistId = 6L))
        assertTrue(event is ChipboxEvent.NavigateTo)
        assertEquals(ArtistDetail(6L), event.destination)
    }

    @Test
    fun `BackClicked emits NavigateBack`() = runTest(dispatcher) {
        val vm = newViewModel()
        val event = collectAndDispatch(vm, SearchAction.BackClicked)
        assertTrue(event is ChipboxEvent.NavigateBack)
    }

    @Test
    fun `SongClicked starts a director session with the song-results setlist`() = runTest(dispatcher) {
        // SongResults are treated as an ad-hoc queue — the director.start(setlist, ...) overload
        // (not the Session-based one). The starting position must be the index of the tapped
        // song in the current results.
        val director = RecordingDirector()
        val songs = listOf(trackOf(10, "A"), trackOf(20, "B"), trackOf(30, "C"))
        val repo = object : Repository by FakeRepository(emptyMap()) {
            override fun searchSongs(query: String) = flowOnce<Data<List<Track>>>(Data.Succeeded(songs))
        }
        val vm = newViewModel(repository = repo, director = director)

        vm.sendAction(SearchAction.QueryChanged("abc"))
        advanceTimeBy(DEBOUNCE_MS + 1)
        vm.state.first { it.songResults is LCE.Content }

        vm.sendAction(SearchAction.SongClicked(trackId = 20L))
        val call = director.startCalls.single()
        assertEquals(listOf(10L, 20L, 30L), call.setlist)
        assertEquals(1, call.startingPosition)
        assertEquals("abc", call.sourceName)
    }

    @Test
    fun `SongClicked on a track that is not in the results is a no-op`() = runTest(dispatcher) {
        // Guards `if (startingPosition >= 0) director.start(...)` — a stale tap (id no longer in
        // current results) must NOT crash and must NOT start a session.
        val director = RecordingDirector()
        val repo = object : Repository by FakeRepository(emptyMap()) {
            override fun searchSongs(query: String) =
                flowOnce<Data<List<Track>>>(Data.Succeeded(listOf(trackOf(1, "T"))))
        }
        val vm = newViewModel(repository = repo, director = director)
        vm.sendAction(SearchAction.QueryChanged("zzz"))
        advanceTimeBy(DEBOUNCE_MS + 1)
        vm.state.first { it.songResults is LCE.Content }
        vm.sendAction(SearchAction.SongClicked(trackId = 999L))
        assertTrue(director.startCalls.isEmpty())
    }

    // ---- helpers ----

    private fun newViewModel(
        repository: Repository = FakeRepository(emptyMap()),
        director: RecordingDirector = RecordingDirector(),
    ) = SearchViewModel(
        repository = repository,
        director = director,
        stringProvider = stubStringProvider(),
        hatchet = BluntHatchet(),
    )

    private fun <T> sharedFlowOf(): MutableSharedFlow<T> = MutableSharedFlow(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private fun <T> flowOnce(value: T): Flow<T> = sharedFlowOf<T>().also { it.tryEmit(value) }

    private fun repoOverridingHistory(flow: Flow<Data<List<SearchHistory>>>): Repository =
        object : Repository by FakeRepository(emptyMap()) {
            override fun getSearchHistory() = flow
        }

    /** [FakeDirector] but with a [start(setlist, ...)] overload that records its arguments so the
     *  song-results path can be asserted. */
    private class RecordingDirector : FakeDirector() {
        data class StartCall(val setlist: List<Long>, val startingPosition: Int, val sourceName: String?)
        val startCalls = mutableListOf<StartCall>()
        override fun start(
            setlist: List<Long>,
            startingPosition: Int,
            sourceName: String?,
            shuffled: Boolean,
        ) {
            startCalls += StartCall(setlist, startingPosition, sourceName)
        }
    }

    private suspend fun CoroutineScope.collectAndDispatch(
        vm: SearchViewModel,
        action: SearchAction,
    ): ChipboxEvent = async(start = CoroutineStart.UNDISPATCHED) { vm.events.first() }
        .also { vm.sendAction(action) }
        .await()

    private fun gameOf(id: Long, title: String): Game =
        Game(id = id, title = title, photoUrl = null, artists = null, tracks = null)

    private fun trackOf(id: Long, title: String): Track = Track(
        id = id,
        path = "/library/$title.psf",
        source = "test",
        title = title,
        trackLengthMs = 60_000L,
        trackNumber = 0,
        fadeLengthMs = 0L,
        game = null,
        artists = null,
        platform = Platform.OTHER,
    )

    private fun artistOf(id: Long, name: String): Artist =
        Artist(id = id, name = name, photoUrl = null, tracks = null, games = null)

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = string.toString()
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = string.toString()
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
        const val HISTORY_RECORD_DELAY_MS = 3_000L
    }
}
