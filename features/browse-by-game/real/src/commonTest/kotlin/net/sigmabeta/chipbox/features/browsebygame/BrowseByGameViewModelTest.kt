package net.sigmabeta.chipbox.features.browsebygame

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
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

    // ---- helpers ----

    private fun <T> sharedFlowOf(): MutableSharedFlow<T> = MutableSharedFlow(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private fun repoWithGames(flow: Flow<Data<List<Game>>>): Repository =
        object : Repository by FakeRepository(emptyMap()) {
            override fun getAllGames(withTracks: Boolean, withArtists: Boolean) = flow
        }

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
