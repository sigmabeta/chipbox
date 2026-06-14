package net.sigmabeta.chipbox.features.home

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.features.artistdetail.ArtistDetail
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.features.home.module.HomeModule
import net.sigmabeta.chipbox.features.home.module.HomeModuleSection
import net.sigmabeta.chipbox.features.nowplaying.NowPlaying
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.player.director.SessionRequest
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
class HomeViewModelTest {

    @BeforeTest fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `each module emission patches only its own section slot`() = runTest {
        val emptySection = HomeModuleSection("Top", persistentListOf())
        val modules = setOf(
            FakeHomeModule("a", priority = 0, flow = flowOf(LCE.Content(emptySection))),
            FakeHomeModule("b", priority = 1, flow = flowOf(LCE.Loading("b.load"))),
        )
        val vm = newVm(modules = modules)
        val state = vm.state.first { it.sections.first().lce is LCE.Content }
        assertEquals(LCE.Content(emptySection), state.sections.first { it.id == "a" }.lce)
        assertEquals(LCE.Loading("b.load"), state.sections.first { it.id == "b" }.lce)
    }

    @Test
    fun `GameClicked emits NavigateTo GameDetail`() = runTest {
        val vm = newVm()
        val event = collectAndDispatch(vm, HomeAction.GameClicked(id = 42L))
        assertTrue(event is ChipboxEvent.NavigateTo)
        assertEquals(GameDetail(42L), event.destination)
    }

    @Test
    fun `RandomGameClicked picks one of the loaded games and navigates`() = runTest {
        val games = listOf(gameOf(7L), gameOf(8L))
        val vm = newVm(repository = repoWithRandomGames(games))
        val event = collectAndDispatch(vm, HomeAction.RandomGameClicked)
        assertTrue(event is ChipboxEvent.NavigateTo)
        val destination = event.destination
        assertTrue(destination is GameDetail)
        assertTrue(destination.id in setOf(7L, 8L))
    }

    @Test
    fun `RandomArtistClicked picks one of the loaded artists and navigates`() = runTest {
        val artists = listOf(artistOf(3L), artistOf(4L))
        val vm = newVm(repository = repoWithRandomArtists(artists))
        val event = collectAndDispatch(vm, HomeAction.RandomArtistClicked)
        assertTrue(event is ChipboxEvent.NavigateTo)
        val destination = event.destination
        assertTrue(destination is ArtistDetail)
        assertTrue(destination.id in setOf(3L, 4L))
    }

    @Test
    fun `RandomSongClicked starts a SINGLE_TRACK session for one of the loaded tracks`() = runTest {
        val tracks = listOf(trackOf(11L), trackOf(12L))
        val director = RecordingDirector()
        val vm = newVm(
            repository = repoWithRandomTracks(tracks),
            director = director,
        )
        vm.sendAction(HomeAction.RandomSongClicked)
        // sendAction under UnconfinedTestDispatcher runs the launch synchronously; one start call expected.
        assertEquals(1, director.startSessionCalls.size)
        val session = director.startSessionCalls.single()
        assertEquals(SessionType.SINGLE_TRACK, session.type)
        assertTrue(session.contentId in setOf(11L, 12L))
    }

    @Test
    fun `NowPlayingCardClicked emits NavigateTo NowPlaying`() = runTest {
        val vm = newVm()
        val event = collectAndDispatch(vm, HomeAction.NowPlayingCardClicked)
        assertTrue(event is ChipboxEvent.NavigateTo)
        assertEquals(NowPlaying, event.destination)
    }

    @Test
    fun `NowPlayingPlayPauseClicked pauses when playing`() = runTest {
        val director = RecordingDirector()
        director.emitPlayback(PlayerState.PLAYING)
        val vm = newVm(director = director)
        vm.sendAction(HomeAction.NowPlayingPlayPauseClicked)
        assertEquals(1, director.pauseCalls)
        assertEquals(0, director.playCalls)
    }

    @Test
    fun `NowPlayingPlayPauseClicked plays when paused`() = runTest {
        val director = RecordingDirector()
        director.emitPlayback(PlayerState.PAUSED)
        val vm = newVm(director = director)
        vm.sendAction(HomeAction.NowPlayingPlayPauseClicked)
        assertEquals(1, director.playCalls)
        assertEquals(0, director.pauseCalls)
    }

    @Test
    fun `NowPlayingCardAppeared emits RequestMiniPlayerVisibility(false)`() = runTest {
        val vm = newVm()
        val event = collectAndDispatch(vm, HomeAction.NowPlayingCardAppeared)
        assertTrue(event is ChipboxEvent.RequestMiniPlayerVisibility)
        assertEquals(false, event.visible)
    }

    @Test
    fun `NowPlayingCardDisappeared emits RequestMiniPlayerVisibility(true)`() = runTest {
        val vm = newVm()
        val event = collectAndDispatch(vm, HomeAction.NowPlayingCardDisappeared)
        assertTrue(event is ChipboxEvent.RequestMiniPlayerVisibility)
        assertEquals(true, event.visible)
    }

    @Test
    fun `Random clicks no-op when the repository returns Empty`() = runTest {
        val director = RecordingDirector()
        val vm = newVm(
            repository = repoWithEverythingEmpty(),
            director = director,
        )
        vm.sendAction(HomeAction.RandomSongClicked)
        vm.sendAction(HomeAction.RandomGameClicked)
        vm.sendAction(HomeAction.RandomArtistClicked)
        assertEquals(0, director.startSessionCalls.size)
    }

    // ---- helpers ----

    private class RecordingDirector : FakeDirector() {
        val startSessionCalls: List<Session>
            get() = requests.filterIsInstance<SessionRequest.Start>().map { it.session }
    }

    private fun newVm(
        modules: Set<HomeModule> = emptySet(),
        repository: Repository = repoWithEverythingEmpty(),
        director: FakeDirector = FakeDirector(),
    ) = HomeViewModel(
        modules = modules,
        repository = repository,
        director = director,
        stringProvider = stubStringProvider(),
        hatchet = BluntHatchet(),
    )

    private class FakeHomeModule(
        override val id: String,
        override val priority: Int,
        private val flow: Flow<LCE<HomeModuleSection>>,
    ) : HomeModule {
        override fun state(): Flow<LCE<HomeModuleSection>> = flow
    }

    private suspend fun CoroutineScope.collectAndDispatch(
        vm: HomeViewModel,
        action: HomeAction,
    ): ChipboxEvent = async(start = CoroutineStart.UNDISPATCHED) { vm.events.first() }
        .also { vm.sendAction(action) }
        .await()

    private fun gameOf(id: Long) = Game(id = id, title = "g$id", photoUrl = null, artists = null, tracks = null)

    private fun artistOf(id: Long) = Artist(id = id, name = "a$id", photoUrl = null, tracks = null, games = null)

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

    private fun repoWithGames(flow: Flow<Data<List<Game>>>): Repository =
        object : Repository by FakeRepository(emptyMap()) {
            override fun getAllGames(withTracks: Boolean, withArtists: Boolean) = flow
        }

    private fun repoWithArtists(flow: Flow<Data<List<Artist>>>): Repository =
        object : Repository by FakeRepository(emptyMap()) {
            override fun getAllArtists(withTracks: Boolean, withGames: Boolean) = flow
        }

    private fun repoWithTracks(flow: Flow<Data<List<Track>>>): Repository =
        object : Repository by FakeRepository(emptyMap()) {
            override fun getAllTracks(withGame: Boolean, withArtists: Boolean) = flow
        }

    private fun repoWithRandomGames(games: List<Game>): Repository =
        object : Repository by FakeRepository(emptyMap()) {
            override suspend fun getRandomGame(): Game? = games.randomOrNull()
        }

    private fun repoWithRandomArtists(artists: List<Artist>): Repository =
        object : Repository by FakeRepository(emptyMap()) {
            override suspend fun getRandomArtist(): Artist? = artists.randomOrNull()
        }

    private fun repoWithRandomTracks(tracks: List<Track>): Repository =
        object : Repository by FakeRepository(emptyMap()) {
            override suspend fun getRandomTrack(): Track? = tracks.randomOrNull()
        }

    private fun repoWithEverythingEmpty(): Repository = FakeRepository(emptyMap())

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = string.toString()
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = string.toString()
    }

    // Suppress unused for symmetry with BrowseByGameViewModelTest's helper kit; will be wanted
    // once HomeViewModel grows error/loading-path tests.
    @Suppress("unused")
    private fun <T> sharedFlowOf(): MutableSharedFlow<T> = MutableSharedFlow(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
}
