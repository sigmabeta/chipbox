package net.sigmabeta.chipbox.features.home.modules

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.fake.FakeRepository
import net.sigmabeta.chipbox.scanner.fake.CountingScanner
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RngTakeTheWheelHomeModuleTest {

    @Test
    fun `hidden when the library has no tracks`() = runTest {
        val module = moduleWith(flowOf(Data.Empty))
        assertEquals(LCE.Uninitialized, module.state().first())
    }

    @Test
    fun `hidden while the probe is still loading`() = runTest {
        val module = moduleWith(flowOf(Data.Loading))
        assertEquals(LCE.Uninitialized, module.state().first())
    }

    @Test
    fun `shown with all three cards once the library has at least one track`() = runTest {
        val module = moduleWith(flowOf(Data.Succeeded(listOf(trackOf(1L)))))
        val lce = module.state().first()
        assertTrue(lce is LCE.Content)
        assertEquals(3, lce.data.items.size)
    }

    @Test
    fun `hidden while a scan is in flight`() = runTest {
        val scanner = CountingScanner(UnconfinedTestDispatcher(testScheduler))
        scanner.pushState(ScannerState.Scanning())
        val module = RngTakeTheWheelHomeModule(
            repoWithTracks(flowOf(Data.Succeeded(listOf(trackOf(1L))))),
            scanner,
            stubStringProvider(),
        )
        assertEquals(LCE.Uninitialized, module.state().first())
    }

    private fun moduleWith(flow: Flow<Data<List<Track>>>) =
        RngTakeTheWheelHomeModule(repoWithTracks(flow), CountingScanner(UnconfinedTestDispatcher()), stubStringProvider())

    private fun repoWithTracks(flow: Flow<Data<List<Track>>>): Repository =
        object : Repository by FakeRepository(emptyMap()) {
            override fun getAllTracks(withGame: Boolean, withArtists: Boolean, limit: Int?, offset: Int) = flow
        }

    private fun trackOf(id: Long) = Track(
        id = id,
        path = "/p$id",
        source = "test",
        title = "t$id",
        trackLengthMs = 1_000L,
        trackNumber = 0,
        fadeLengthMs = 0L,
        game = null,
        artists = null,
        platform = Platform.OTHER,
    )

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = string.toString()
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = string.toString()
    }
}
