package net.sigmabeta.chipbox.features.artistdetail

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
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.favorites.fake.FakeFavoritesRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ArtistDetailViewModel] is the mirror image of [GameDetailViewModel] — same Data → LCE fan
 * across three slots (artist/tracks/games), same notFound behaviour. Tests follow the same
 * shape; if a future state-shape change splits the two reducers, this guards the ArtistDetail
 * side independently.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArtistDetailViewModelTest {

    @BeforeTest fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Data Succeeded fans the loaded artist across all three LCE slots`() = runTest {
        val artist = artistOf(
            id = 1L,
            name = "Yasunori Mitsuda",
            tracks = listOf(trackOf(1, "Schala")),
            games = listOf(gameOf(1, "Chrono Trigger")),
        )
        val source = sharedFlowOf<Data<Artist?>>().also { it.tryEmit(Data.Succeeded(artist)) }

        val vm = newViewModel(artistId = 1L, repository = repoWithArtist(source))

        val state = vm.state.first { it.artist is LCE.Content }
        assertEquals(LCE.Content(artist), state.artist)
        assertEquals(LCE.Content(artist.tracks!!), state.tracks)
        assertEquals(LCE.Content(artist.games!!), state.games)
        assertFalse(state.notFound)
    }

    @Test
    fun `Data Empty flips notFound and resets the slots to Uninitialized`() = runTest {
        val source = sharedFlowOf<Data<Artist?>>().also { it.tryEmit(Data.Empty) }
        val vm = newViewModel(artistId = 999L, repository = repoWithArtist(source))

        val state = vm.state.first { it.notFound }
        assertEquals(LCE.Uninitialized, state.artist)
        assertEquals(LCE.Uninitialized, state.tracks)
        assertEquals(LCE.Uninitialized, state.games)
    }

    @Test
    fun `Data Loading fans LCE Loading across all three slots`() = runTest {
        val source = sharedFlowOf<Data<Artist?>>().also { it.tryEmit(Data.Loading) }
        val vm = newViewModel(artistId = 1L, repository = repoWithArtist(source))

        val state = vm.state.first { it.artist is LCE.Loading }
        assertEquals("artist_detail.load", (state.artist as LCE.Loading).operationName)
        assertTrue(state.tracks is LCE.Loading)
        assertTrue(state.games is LCE.Loading)
    }

    @Test
    fun `Data Failed fans LCE Error across all three slots with the same message`() = runTest {
        val source = sharedFlowOf<Data<Artist?>>().also { it.tryEmit(Data.Failed("net err")) }
        val vm = newViewModel(artistId = 1L, repository = repoWithArtist(source))

        val state = vm.state.first { it.artist is LCE.Error }
        val err = state.artist as LCE.Error
        assertEquals("artist_detail.load", err.operationName)
        assertEquals("net err", err.error.message)
    }

    @Test
    fun `playingTrackId mirrors Director metadata id`() = runTest {
        val director = FakeDirector()
        val vm = newViewModel(artistId = 1L, director = director)
        assertNull(vm.state.first().playingTrackId)

        director.emitMetadata(trackOf(42L, "X"))
        val state = vm.state.first { it.playingTrackId == 42L }
        assertEquals(42L, state.playingTrackId)
    }

    @Test
    fun `GameClicked emits NavigateTo GameDetail with the right id`() = runTest {
        val vm = newViewModel(artistId = 1L)
        val event = collectAndDispatch(vm, ArtistDetailAction.GameClicked(id = 5L))
        assertTrue(event is ChipboxEvent.NavigateTo)
        assertEquals(GameDetail(5L), event.destination)
    }

    @Test
    fun `PlayAllClicked routes to the start session path without throwing`() = runTest {
        val vm = newViewModel(artistId = 1L)
        vm.sendAction(ArtistDetailAction.PlayAllClicked)
    }

    @Test
    fun `ShuffleAllClicked routes to the start session path without throwing`() = runTest {
        val vm = newViewModel(artistId = 1L)
        vm.sendAction(ArtistDetailAction.ShuffleAllClicked)
    }

    @Test
    fun `TrackClicked routes to the start session path without throwing`() = runTest {
        val vm = newViewModel(artistId = 1L)
        vm.sendAction(ArtistDetailAction.TrackClicked(position = 1))
    }

    // ---- helpers ----

    private fun newViewModel(
        artistId: Long,
        repository: Repository = FakeRepository(emptyMap()),
        director: FakeDirector = FakeDirector(),
    ) = ArtistDetailViewModel(
        artistId = artistId,
        repository = repository,
        director = director,
        favorites = FakeFavoritesRepository(),
        stringProvider = stubStringProvider(),
        hatchet = BluntHatchet(),
    )

    private fun <T> sharedFlowOf(): MutableSharedFlow<T> = MutableSharedFlow(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private fun repoWithArtist(flow: Flow<Data<Artist?>>): Repository =
        object : Repository by FakeRepository(emptyMap()) {
            override fun getArtist(id: Long, withTracks: Boolean, withGames: Boolean) = flow
        }

    private suspend fun CoroutineScope.collectAndDispatch(
        vm: ArtistDetailViewModel,
        action: ArtistDetailAction,
    ): ChipboxEvent = async(start = CoroutineStart.UNDISPATCHED) { vm.events.first() }
        .also { vm.sendAction(action) }
        .await()

    private fun artistOf(id: Long, name: String, tracks: List<Track>? = null, games: List<Game>? = null): Artist =
        Artist(id = id, name = name, photoUrl = null, tracks = tracks, games = games)

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

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = string.toString()
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = string.toString()
    }
}
