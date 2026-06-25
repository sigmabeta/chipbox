package net.sigmabeta.chipbox.features.browsebygame

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.fake.FakeRepository
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseByGameViewModelTest {

    @BeforeTest fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Data Empty maps to LCE Content with an empty list`() = runTest {
        val source = sharedFlowOf<Data<List<Game>>>().also { it.tryEmit(Data.Empty) }
        val vm = BrowseByGameViewModel(repoWithGames(source), stubStringProvider(), BluntHatchet())
        val state = vm.state.first { it.games is LCE.Content }
        assertEquals(LCE.Content(emptyList<Game>()), state.games)
    }

    @Test
    fun `Data Succeeded maps to LCE Content with the list`() = runTest {
        val games = listOf(gameOf(1, "Chrono Trigger"), gameOf(2, "Final Fantasy VI"))
        val source = sharedFlowOf<Data<List<Game>>>().also { it.tryEmit(Data.Succeeded(games)) }
        val vm = BrowseByGameViewModel(repoWithGames(source), stubStringProvider(), BluntHatchet())
        val state = vm.state.first { (it.games as? LCE.Content)?.data?.isNotEmpty() == true }
        assertEquals(LCE.Content(games), state.games)
    }

    @Test
    fun `Data Loading maps to LCE Loading with the documented operation tag`() = runTest {
        val source = sharedFlowOf<Data<List<Game>>>().also { it.tryEmit(Data.Loading) }
        val vm = BrowseByGameViewModel(repoWithGames(source), stubStringProvider(), BluntHatchet())
        val state = vm.state.first { it.games is LCE.Loading }
        assertEquals("browse_by_game.load", (state.games as LCE.Loading).operationName)
    }

    @Test
    fun `Data Failed maps to LCE Error wrapping the message`() = runTest {
        val source = sharedFlowOf<Data<List<Game>>>().also { it.tryEmit(Data.Failed("disk error")) }
        val vm = BrowseByGameViewModel(repoWithGames(source), stubStringProvider(), BluntHatchet())
        val state = vm.state.first { it.games is LCE.Error }
        val error = state.games as LCE.Error
        assertEquals("browse_by_game.load", error.operationName)
        assertEquals("disk error", error.error.message)
    }

    @Test
    fun `GameClicked emits NavigateTo GameDetail with the right id`() = runTest {
        val vm = BrowseByGameViewModel(FakeRepository(emptyMap()), stubStringProvider(), BluntHatchet())
        val event = collectAndDispatch(vm, BrowseByGameAction.GameClicked(id = 7L))
        assertTrue(event is ChipboxEvent.NavigateTo)
        assertEquals(GameDetail(7L), event.destination)
    }

    @Test
    fun `paging walks the catalog a page at a time and stops at the end`() = runTest {
        val vm = BrowseByGameViewModel(repoOver(manyGames(250)), stubStringProvider(), BluntHatchet())

        // First page loads on init.
        assertEquals(100, vm.contentSize())
        assertTrue(vm.state.value.hasMoreAfter)
        assertEquals(false, vm.state.value.hasMoreBefore)

        vm.sendAction(SageAction.LoadMoreRequested)
        assertEquals(200, vm.contentSize())
        assertTrue(vm.state.value.hasMoreAfter)

        vm.sendAction(SageAction.LoadMoreRequested)
        assertEquals(250, vm.contentSize())
        assertEquals(false, vm.state.value.hasMoreAfter) // last page was partial

        // Nothing left below -> request is a no-op.
        vm.sendAction(SageAction.LoadMoreRequested)
        assertEquals(250, vm.contentSize())
    }

    @Test
    fun `load-more ignores a second request while a page is still loading`() = runTest {
        val all = manyGames(250)
        val gate = CompletableDeferred<Unit>()
        var pagedFetches = 0
        val repo = object : Repository by FakeRepository(emptyMap()) {
            override fun getAllGames(withTracks: Boolean, withArtists: Boolean, limit: Int?, offset: Int) = flow {
                emit(Data.Loading)
                if (offset > 0) {
                    pagedFetches++
                    gate.await() // hold the in-flight page open
                }
                val slice = all.drop(offset).let { if (limit != null) it.take(limit) else it }
                emit(if (slice.isEmpty()) Data.Empty else Data.Succeeded(slice))
            }
        }
        val vm = BrowseByGameViewModel(repo, stubStringProvider(), BluntHatchet())
        assertEquals(100, vm.contentSize()) // initial page (offset 0, ungated)

        vm.sendAction(SageAction.LoadMoreRequested) // starts page @100, suspends on the gate
        vm.sendAction(SageAction.LoadMoreRequested) // coalesced away by the in-flight guard
        gate.complete(Unit)

        assertEquals(200, vm.contentSize())
        assertEquals(1, pagedFetches) // only one page was actually requested
    }

    @Test
    fun `entering at an offset pages backwards to the start, then forward to the end`() = runTest {
        val vm = BrowseByGameViewModel(repoOver(manyGames(250)), stubStringProvider(), BluntHatchet())
        assertEquals(100, vm.contentSize())

        // Open the window at the second page.
        vm.sendAction(SageAction.InitWithPageNumber(id = 0L, pageNumber = 1L))
        assertEquals(100, vm.contentSize())
        assertEquals(100, vm.state.value.windowStart)
        assertTrue(vm.state.value.hasMoreBefore)
        assertEquals(101L, firstGameId(vm)) // games 101..200

        // Prepend the earlier page.
        vm.sendAction(SageAction.LoadPreviousRequested)
        assertEquals(200, vm.contentSize())
        assertEquals(0, vm.state.value.windowStart)
        assertEquals(false, vm.state.value.hasMoreBefore)
        assertEquals(1L, firstGameId(vm)) // window now starts at the top

        // And still pages forward off the same window.
        vm.sendAction(SageAction.LoadMoreRequested)
        assertEquals(250, vm.contentSize())
        assertEquals(false, vm.state.value.hasMoreAfter)
    }

    // ---- helpers ----

    private fun <T> sharedFlowOf(): MutableSharedFlow<T> = MutableSharedFlow(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private fun repoWithGames(flow: Flow<Data<List<Game>>>): Repository =
        object : Repository by FakeRepository(emptyMap()) {
            override fun getAllGames(withTracks: Boolean, withArtists: Boolean, limit: Int?, offset: Int) = flow
        }

    // Backs the catalog with a real list and honors limit/offset, so the VM's paging window can be
    // exercised the way the database repository would serve it.
    private fun repoOver(all: List<Game>): Repository =
        object : Repository by FakeRepository(emptyMap()) {
            override fun getAllGames(withTracks: Boolean, withArtists: Boolean, limit: Int?, offset: Int): Flow<Data<List<Game>>> {
                val slice = all.drop(offset).let { if (limit != null) it.take(limit) else it }
                return flowOf(if (slice.isEmpty()) Data.Empty else Data.Succeeded(slice))
            }
        }

    private fun BrowseByGameViewModel.contentSize() =
        (state.value.games as? LCE.Content)?.data?.size ?: 0

    private fun firstGameId(vm: BrowseByGameViewModel) =
        (vm.state.value.games as LCE.Content).data.first().id

    private fun manyGames(count: Int): List<Game> =
        (1..count).map { gameOf(it.toLong(), "Game $it") }

    private fun gameOf(id: Long, title: String): Game =
        Game(id = id, title = title, photoUrl = null, artists = null, tracks = null)

    private suspend fun CoroutineScope.collectAndDispatch(
        vm: BrowseByGameViewModel,
        action: BrowseByGameAction,
    ): ChipboxEvent = async(start = CoroutineStart.UNDISPATCHED) { vm.events.first() }
        .also { vm.sendAction(action) }
        .await()

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = string.toString()
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = string.toString()
    }
}
