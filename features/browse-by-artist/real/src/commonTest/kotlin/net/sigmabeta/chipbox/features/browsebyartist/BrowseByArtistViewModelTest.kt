package net.sigmabeta.chipbox.features.browsebyartist

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
import net.sigmabeta.chipbox.features.artistdetail.ArtistDetail
import net.sigmabeta.chipbox.models.Artist
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

/**
 * [BrowseByArtistViewModel] subscribes to `repository.getAllArtists()` in `init` and folds each
 * Data emission into LCE on `state.artists`. The click action emits `NavigateTo(ArtistDetail)`.
 *
 * Tests drive the repository's flow via a [MutableSharedFlow] swapped in for `getAllArtists`
 * (via delegation through [FakeRepository]). The Dispatchers.setMain rig is the same one used
 * across the rest of the ChipboxListViewModel suites.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowseByArtistViewModelTest {

    @BeforeTest fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Data Empty maps to LCE Content with an empty list`() = runTest {
        val source = sharedFlowOf<Data<List<Artist>>>().also { it.tryEmit(Data.Empty) }
        val vm = BrowseByArtistViewModel(repoWithArtists(source), stubStringProvider(), BluntHatchet())
        val state = vm.state.first { it.artists is LCE.Content }
        assertEquals(LCE.Content(emptyList<Artist>()), state.artists)
    }

    @Test
    fun `Data Succeeded maps to LCE Content with the list`() = runTest {
        val artists = listOf(artistOf(1, "Yuzo Koshiro"), artistOf(2, "Yasunori Mitsuda"))
        val source = sharedFlowOf<Data<List<Artist>>>().also { it.tryEmit(Data.Succeeded(artists)) }
        val vm = BrowseByArtistViewModel(repoWithArtists(source), stubStringProvider(), BluntHatchet())
        val state = vm.state.first { it.artists is LCE.Content && (it.artists as LCE.Content).data.isNotEmpty() }
        assertEquals(LCE.Content(artists), state.artists)
    }

    @Test
    fun `Data Loading maps to LCE Loading with the documented operation tag`() = runTest {
        // The operation tag is `browse_by_artist.load` per the private companion const. Pinning
        // it ensures the LCE-driven UI's loading states stay distinguishable by operation.
        val source = sharedFlowOf<Data<List<Artist>>>().also { it.tryEmit(Data.Loading) }
        val vm = BrowseByArtistViewModel(repoWithArtists(source), stubStringProvider(), BluntHatchet())
        val state = vm.state.first { it.artists is LCE.Loading }
        val loading = state.artists as LCE.Loading
        assertEquals("browse_by_artist.load", loading.operationName)
    }

    @Test
    fun `Data Failed maps to LCE Error wrapping the message`() = runTest {
        val source = sharedFlowOf<Data<List<Artist>>>().also { it.tryEmit(Data.Failed("network down")) }
        val vm = BrowseByArtistViewModel(repoWithArtists(source), stubStringProvider(), BluntHatchet())
        val state = vm.state.first { it.artists is LCE.Error }
        val error = state.artists as LCE.Error
        assertEquals("browse_by_artist.load", error.operationName)
        assertEquals("network down", error.error.message)
    }

    @Test
    fun `ArtistClicked emits NavigateTo ArtistDetail with the right id`() = runTest {
        val vm = BrowseByArtistViewModel(FakeRepository(emptyMap()), stubStringProvider(), BluntHatchet())
        val event = collectAndDispatch(vm, BrowseByArtistAction.ArtistClicked(id = 42L))
        assertTrue(event is ChipboxEvent.NavigateTo)
        assertEquals(ArtistDetail(42L), event.destination)
    }

    // ---- helpers ----

    private fun <T> sharedFlowOf(): MutableSharedFlow<T> = MutableSharedFlow(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private fun repoWithArtists(flow: Flow<Data<List<Artist>>>): Repository =
        object : Repository by FakeRepository(emptyMap()) {
            override fun getAllArtists(withTracks: Boolean, withGames: Boolean) = flow
        }

    private fun artistOf(id: Long, name: String): Artist =
        Artist(id = id, name = name, photoUrl = null, tracks = null, games = null)

    private suspend fun CoroutineScope.collectAndDispatch(
        vm: BrowseByArtistViewModel,
        action: BrowseByArtistAction,
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
