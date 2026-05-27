package net.sigmabeta.chipbox.features.gamesforplatform

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
import net.sigmabeta.chipbox.models.Platform
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GamesForPlatformViewModelTest {

    @BeforeTest fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init seeds state platform from the assisted constructor arg`() = runTest {
        // The assisted-injected platform is mirrored onto state.platform synchronously in init —
        // the title bar reads from it, so a delay would briefly show a blank title.
        val vm = newViewModel(platform = Platform.SNES)
        assertEquals(Platform.SNES, vm.state.first().platform)
    }

    @Test
    fun `Data Succeeded surfaces the game list in state`() = runTest {
        val games = listOf(gameOf(1, "Chrono Trigger"), gameOf(2, "Final Fantasy VI"))
        val source = sharedFlowOf<Data<List<Game>>>().also { it.tryEmit(Data.Succeeded(games)) }
        val vm = newViewModel(platform = Platform.SNES, repository = repoWithGames(source))

        val state = vm.state.first { (it.games as? LCE.Content)?.data?.isNotEmpty() == true }
        assertEquals(LCE.Content(games), state.games)
    }

    @Test
    fun `Data Empty maps to LCE Content with an empty list`() = runTest {
        val source = sharedFlowOf<Data<List<Game>>>().also { it.tryEmit(Data.Empty) }
        val vm = newViewModel(platform = Platform.SNES, repository = repoWithGames(source))
        val state = vm.state.first { it.games is LCE.Content }
        assertEquals(LCE.Content(emptyList<Game>()), state.games)
    }

    @Test
    fun `Data Loading maps to LCE Loading with the documented op tag`() = runTest {
        val source = sharedFlowOf<Data<List<Game>>>().also { it.tryEmit(Data.Loading) }
        val vm = newViewModel(platform = Platform.SNES, repository = repoWithGames(source))
        val state = vm.state.first { it.games is LCE.Loading }
        assertEquals("games_for_platform.load", (state.games as LCE.Loading).operationName)
    }

    @Test
    fun `Data Failed maps to LCE Error wrapping the message`() = runTest {
        val source = sharedFlowOf<Data<List<Game>>>().also { it.tryEmit(Data.Failed("io")) }
        val vm = newViewModel(platform = Platform.SNES, repository = repoWithGames(source))
        val state = vm.state.first { it.games is LCE.Error }
        val err = state.games as LCE.Error
        assertEquals("games_for_platform.load", err.operationName)
        assertEquals("io", err.error.message)
    }

    @Test
    fun `GameClicked emits NavigateTo GameDetail with the right id`() = runTest {
        val vm = newViewModel(platform = Platform.SNES)
        val event = collectAndDispatch(vm, GamesForPlatformAction.GameClicked(id = 7L))
        assertTrue(event is ChipboxEvent.NavigateTo)
        assertEquals(GameDetail(7L), event.destination)
    }

    @Test
    fun `PlayAllClicked routes to the start session path without throwing`() = runTest {
        val vm = newViewModel(platform = Platform.SNES)
        vm.sendAction(GamesForPlatformAction.PlayAllClicked)
    }

    @Test
    fun `ShuffleAllClicked routes to the start session path without throwing`() = runTest {
        val vm = newViewModel(platform = Platform.SNES)
        vm.sendAction(GamesForPlatformAction.ShuffleAllClicked)
    }

    // ---- helpers ----

    private fun newViewModel(
        platform: Platform,
        repository: Repository = FakeRepository(emptyMap()),
        director: FakeDirector = FakeDirector(),
    ) = GamesForPlatformViewModel(
        platform = platform,
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

    private fun repoWithGames(flow: Flow<Data<List<Game>>>): Repository =
        object : Repository by FakeRepository(emptyMap()) {
            override fun getGamesForPlatform(platform: Platform) = flow
        }

    private suspend fun CoroutineScope.collectAndDispatch(
        vm: GamesForPlatformViewModel,
        action: GamesForPlatformAction,
    ): ChipboxEvent = async(start = CoroutineStart.UNDISPATCHED) { vm.events.first() }
        .also { vm.sendAction(action) }
        .await()

    private fun gameOf(id: Long, title: String): Game =
        Game(id = id, title = title, photoUrl = null, artists = null, tracks = null)

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = string.toString()
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = string.toString()
    }
}
