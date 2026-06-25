package net.sigmabeta.chipbox.features.browsebyartist

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
import net.sigmabeta.chipbox.features.artistdetail.ArtistDetail
import net.sigmabeta.chipbox.models.Artist
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

/**
 * [BrowseByArtistViewModel] opens a paging window over `repository.getAllArtists(limit, offset)` on
 * `init` and folds each Data emission into LCE on `state.artists`. The click action emits
 * `NavigateTo(ArtistDetail)`.
 *
 * Tests drive the repository's flow via a [MutableSharedFlow] swapped in for `getAllArtists`
 * (via delegation through [FakeRepository]), plus a list-backed `repoOver` that honors limit/offset
 * to exercise the paging window. The Dispatchers.setMain rig is the same one used across the rest of
 * the ChipboxListViewModel suites.
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

    @Test
    fun `paging walks the catalog a page at a time and stops at the end`() = runTest {
        val vm = BrowseByArtistViewModel(repoOver(manyArtists(250)), stubStringProvider(), BluntHatchet())

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
        val all = manyArtists(250)
        val gate = CompletableDeferred<Unit>()
        var pagedFetches = 0
        val repo = object : Repository by FakeRepository(emptyMap()) {
            override fun getAllArtists(withTracks: Boolean, withGames: Boolean, limit: Int?, offset: Int) = flow {
                emit(Data.Loading)
                if (offset > 0) {
                    pagedFetches++
                    gate.await() // hold the in-flight page open
                }
                val slice = all.drop(offset).let { if (limit != null) it.take(limit) else it }
                emit(if (slice.isEmpty()) Data.Empty else Data.Succeeded(slice))
            }
        }
        val vm = BrowseByArtistViewModel(repo, stubStringProvider(), BluntHatchet())
        assertEquals(100, vm.contentSize()) // initial page (offset 0, ungated)

        vm.sendAction(SageAction.LoadMoreRequested) // starts page @100, suspends on the gate
        vm.sendAction(SageAction.LoadMoreRequested) // coalesced away by the in-flight guard
        gate.complete(Unit)

        assertEquals(200, vm.contentSize())
        assertEquals(1, pagedFetches) // only one page was actually requested
    }

    @Test
    fun `entering at an offset pages backwards to the start, then forward to the end`() = runTest {
        val vm = BrowseByArtistViewModel(repoOver(manyArtists(250)), stubStringProvider(), BluntHatchet())
        assertEquals(100, vm.contentSize())

        // Open the window at the second page.
        vm.sendAction(SageAction.InitWithPageNumber(id = 0L, pageNumber = 1L))
        assertEquals(100, vm.contentSize())
        assertEquals(100, vm.state.value.windowStart)
        assertTrue(vm.state.value.hasMoreBefore)
        assertEquals(101L, firstArtistId(vm)) // artists 101..200

        // Prepend the earlier page.
        vm.sendAction(SageAction.LoadPreviousRequested)
        assertEquals(200, vm.contentSize())
        assertEquals(0, vm.state.value.windowStart)
        assertEquals(false, vm.state.value.hasMoreBefore)
        assertEquals(1L, firstArtistId(vm)) // window now starts at the top

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

    private fun repoWithArtists(flow: Flow<Data<List<Artist>>>): Repository =
        object : Repository by FakeRepository(emptyMap()) {
            override fun getAllArtists(withTracks: Boolean, withGames: Boolean, limit: Int?, offset: Int) = flow
        }

    // Backs the catalog with a real list and honors limit/offset, so the VM's paging window can be
    // exercised the way the database repository would serve it.
    private fun repoOver(all: List<Artist>): Repository =
        object : Repository by FakeRepository(emptyMap()) {
            override fun getAllArtists(withTracks: Boolean, withGames: Boolean, limit: Int?, offset: Int): Flow<Data<List<Artist>>> {
                val slice = all.drop(offset).let { if (limit != null) it.take(limit) else it }
                return flowOf(if (slice.isEmpty()) Data.Empty else Data.Succeeded(slice))
            }
        }

    private fun BrowseByArtistViewModel.contentSize() =
        (state.value.artists as? LCE.Content)?.data?.size ?: 0

    private fun firstArtistId(vm: BrowseByArtistViewModel) =
        (vm.state.value.artists as LCE.Content).data.first().id

    private fun manyArtists(count: Int): List<Artist> =
        (1..count).map { artistOf(it.toLong(), "Artist $it") }

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
