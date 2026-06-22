package net.sigmabeta.chipbox.features.playlists

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.features.playlistdetail.PlaylistDetail
import net.sigmabeta.chipbox.playlists.fake.FakePlaylistsRepository
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
class PlaylistsViewModelTest {

    @BeforeTest fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `seeded playlists surface as LCE Content`() = runTest {
        val repo = FakePlaylistsRepository().apply {
            seed("Boss Themes", listOf(1, 2, 3))
            seed("Late Night", listOf(4))
        }
        val vm = browseVm(repo)

        val state = vm.state.first { (it.playlists as? LCE.Content)?.data?.isNotEmpty() == true }
        val names = (state.playlists as LCE.Content).data.map { it.name }
        // Newest playlist first.
        assertEquals(listOf("Late Night", "Boss Themes"), names)
    }

    @Test
    fun `browse mode - PlaylistClicked navigates to that playlist's detail`() = runTest {
        val vm = browseVm(FakePlaylistsRepository())

        val event = collectAndDispatch(vm, PlaylistsAction.PlaylistClicked(id = 7L))

        assertTrue(event is ChipboxEvent.NavigateTo)
        assertEquals(PlaylistDetail(7L), event.destination)
    }

    @Test
    fun `browse mode - NewPlaylistClicked creates a playlist and opens its detail`() = runTest {
        val repo = FakePlaylistsRepository()
        val vm = browseVm(repo)

        val event = collectAndDispatch(vm, PlaylistsAction.NewPlaylistClicked)

        assertTrue(event is ChipboxEvent.NavigateTo)
        val destination = event.destination
        assertTrue(destination is PlaylistDetail)
        val created = repo.playlists().first()
        assertEquals(1, created.size)
        assertEquals(created.single().id, destination.id)
    }

    @Test
    fun `New Playlist appends an integer when the default name is taken`() = runTest {
        val repo = FakePlaylistsRepository()
        // The stub StringProvider returns the id's name, so the default base is "PLAYLISTS_DEFAULT_NAME".
        repo.seed("PLAYLISTS_DEFAULT_NAME")
        val vm = browseVm(repo)

        collectAndDispatch(vm, PlaylistsAction.NewPlaylistClicked)

        val names = repo.playlists().first().map { it.name }
        assertTrue(names.contains("PLAYLISTS_DEFAULT_NAME 2"), "expected a numbered default name, got $names")
    }

    @Test
    fun `picker mode - PlaylistClicked adds the pending tracks and pops back`() = runTest {
        val repo = FakePlaylistsRepository()
        val target = repo.seed("Existing", emptyList())
        val vm = pickerVm(repo, pendingTrackIds = listOf(10L, 11L))

        val event = collectAndDispatch(vm, PlaylistsAction.PlaylistClicked(id = target))

        assertTrue(event is ChipboxEvent.NavigateBack)
        assertEquals(listOf(10L, 11L), repo.trackIds(target).first())
    }

    @Test
    fun `picker mode - NewPlaylistClicked creates a playlist seeded with the pending tracks`() = runTest {
        val repo = FakePlaylistsRepository()
        val vm = pickerVm(repo, pendingTrackIds = listOf(10L, 11L))

        val event = collectAndDispatch(vm, PlaylistsAction.NewPlaylistClicked)

        assertTrue(event is ChipboxEvent.NavigateTo)
        val destination = event.destination
        assertTrue(destination is PlaylistDetail)
        assertEquals(listOf(10L, 11L), repo.trackIds(destination.id).first())
    }

    @Test
    fun `picker mode flips the state into picker form`() = runTest {
        val vm = pickerVm(FakePlaylistsRepository(), pendingTrackIds = listOf(1L))
        assertTrue(vm.state.value.isPicker)
    }

    @Test
    fun `New Playlist uses the suggested name when one is supplied`() = runTest {
        val repo = FakePlaylistsRepository()
        val vm = pickerVm(repo, pendingTrackIds = listOf(1L), suggestedName = "From game Chrono Trigger")

        collectAndDispatch(vm, PlaylistsAction.NewPlaylistClicked)

        assertEquals(listOf("From game Chrono Trigger"), repo.playlists().first().map { it.name })
    }

    // ---- helpers ----

    private fun browseVm(repo: FakePlaylistsRepository) =
        PlaylistsViewModel(emptyList(), null, repo, stubStringProvider(), BluntHatchet())

    private fun pickerVm(
        repo: FakePlaylistsRepository,
        pendingTrackIds: List<Long>,
        suggestedName: String? = null,
    ) = PlaylistsViewModel(pendingTrackIds, suggestedName, repo, stubStringProvider(), BluntHatchet())

    private suspend fun CoroutineScope.collectAndDispatch(
        vm: PlaylistsViewModel,
        action: PlaylistsAction,
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
