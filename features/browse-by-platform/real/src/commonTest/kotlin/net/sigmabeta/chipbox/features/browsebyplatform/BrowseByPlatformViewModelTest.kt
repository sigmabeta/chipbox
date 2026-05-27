package net.sigmabeta.chipbox.features.browsebyplatform

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
import net.sigmabeta.chipbox.features.gamesforplatform.GamesForPlatform
import net.sigmabeta.chipbox.models.Platform
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
class BrowseByPlatformViewModelTest {

    @BeforeTest fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Data Empty maps to LCE Content with an empty list`() = runTest {
        val source = sharedFlowOf<Data<List<Platform>>>().also { it.tryEmit(Data.Empty) }
        val vm = BrowseByPlatformViewModel(repoWithPlatforms(source), stubStringProvider(), BluntHatchet())
        val state = vm.state.first { it.platforms is LCE.Content }
        assertEquals(LCE.Content(emptyList<Platform>()), state.platforms)
    }

    @Test
    fun `Data Succeeded maps to LCE Content with the list`() = runTest {
        val platforms = listOf(Platform.SNES, Platform.NES, Platform.PSX)
        val source = sharedFlowOf<Data<List<Platform>>>().also { it.tryEmit(Data.Succeeded(platforms)) }
        val vm = BrowseByPlatformViewModel(repoWithPlatforms(source), stubStringProvider(), BluntHatchet())
        val state = vm.state.first { (it.platforms as? LCE.Content)?.data?.isNotEmpty() == true }
        assertEquals(LCE.Content(platforms), state.platforms)
    }

    @Test
    fun `Data Loading maps to LCE Loading with the documented operation tag`() = runTest {
        val source = sharedFlowOf<Data<List<Platform>>>().also { it.tryEmit(Data.Loading) }
        val vm = BrowseByPlatformViewModel(repoWithPlatforms(source), stubStringProvider(), BluntHatchet())
        val state = vm.state.first { it.platforms is LCE.Loading }
        assertEquals("browse_by_platform.load", (state.platforms as LCE.Loading).operationName)
    }

    @Test
    fun `Data Failed maps to LCE Error wrapping the message`() = runTest {
        val source = sharedFlowOf<Data<List<Platform>>>().also { it.tryEmit(Data.Failed("oops")) }
        val vm = BrowseByPlatformViewModel(repoWithPlatforms(source), stubStringProvider(), BluntHatchet())
        val state = vm.state.first { it.platforms is LCE.Error }
        val error = state.platforms as LCE.Error
        assertEquals("browse_by_platform.load", error.operationName)
        assertEquals("oops", error.error.message)
    }

    @Test
    fun `PlatformClicked emits NavigateTo GamesForPlatform with the right platform`() = runTest {
        val vm = BrowseByPlatformViewModel(FakeRepository(emptyMap()), stubStringProvider(), BluntHatchet())
        val event = collectAndDispatch(vm, BrowseByPlatformAction.PlatformClicked(Platform.NES))
        assertTrue(event is ChipboxEvent.NavigateTo)
        assertEquals(GamesForPlatform(Platform.NES), event.destination)
    }

    // ---- helpers ----

    private fun <T> sharedFlowOf(): MutableSharedFlow<T> = MutableSharedFlow(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private fun repoWithPlatforms(flow: Flow<Data<List<Platform>>>): Repository =
        object : Repository by FakeRepository(emptyMap()) {
            override fun getAvailablePlatforms() = flow
        }

    private suspend fun CoroutineScope.collectAndDispatch(
        vm: BrowseByPlatformViewModel,
        action: BrowseByPlatformAction,
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
