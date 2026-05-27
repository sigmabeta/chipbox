package net.sigmabeta.chipbox.features.gamedetail

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
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
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
 * [GameDetailViewModel] fans Data → LCE across three state slots (game/tracks/artists) in
 * lockstep, and surfaces a `notFound` flag distinctly from `LCE.Uninitialized`. Two distinct
 * "no data" cases that the screen needs to render differently:
 *
 *   - `Uninitialized` + `notFound = false` → "haven't tried loading yet" — leave the screen blank
 *   - `Uninitialized` + `notFound = true` → "asked the repo, it returned Empty" — show the
 *     not-found state. The reducer flips `notFound = true` only on `Data.Empty`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailViewModelTest {

    @BeforeTest fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Data Succeeded fans the loaded game across all three LCE slots`() = runTest {
        // The reducer fills game/tracks/artists in one atomic state update so the screen never
        // shows a half-loaded view (game card without its track list, etc.).
        val game = gameOf(1L, "Chrono Trigger", tracks = listOf(trackOf(1, "Schala")), artists = listOf(artistOf(1, "Yasunori Mitsuda")))
        val source = sharedFlowOf<Data<Game?>>().also { it.tryEmit(Data.Succeeded(game)) }

        val vm = newViewModel(gameId = 1L, repository = repoWithGame(source))

        val state = vm.state.first { it.game is LCE.Content }
        assertEquals(LCE.Content(game), state.game)
        assertEquals(LCE.Content(game.tracks!!), state.tracks)
        assertEquals(LCE.Content(game.artists!!), state.artists)
        assertFalse(state.notFound, "notFound should remain false when the game loads")
    }

    @Test
    fun `Data Empty flips notFound to true and resets the slots to Uninitialized`() = runTest {
        // The screen distinguishes "still loading" (Uninitialized, !notFound) from "asked the
        // repo and got nothing back" (Uninitialized, notFound). Lock in the reset semantics so
        // a stale game payload doesn't linger after the underlying row is deleted.
        val source = sharedFlowOf<Data<Game?>>().also { it.tryEmit(Data.Empty) }

        val vm = newViewModel(gameId = 999L, repository = repoWithGame(source))

        val state = vm.state.first { it.notFound }
        assertEquals(LCE.Uninitialized, state.game)
        assertEquals(LCE.Uninitialized, state.tracks)
        assertEquals(LCE.Uninitialized, state.artists)
    }

    @Test
    fun `Data Loading fans LCE Loading across all three slots`() = runTest {
        val source = sharedFlowOf<Data<Game?>>().also { it.tryEmit(Data.Loading) }
        val vm = newViewModel(gameId = 1L, repository = repoWithGame(source))

        val state = vm.state.first { it.game is LCE.Loading }
        assertEquals("game_detail.load", (state.game as LCE.Loading).operationName)
        assertTrue(state.tracks is LCE.Loading)
        assertTrue(state.artists is LCE.Loading)
    }

    @Test
    fun `Data Failed fans LCE Error across all three slots with the same message`() = runTest {
        val source = sharedFlowOf<Data<Game?>>().also { it.tryEmit(Data.Failed("io error")) }
        val vm = newViewModel(gameId = 1L, repository = repoWithGame(source))

        val state = vm.state.first { it.game is LCE.Error }
        val err = state.game as LCE.Error
        assertEquals("game_detail.load", err.operationName)
        assertEquals("io error", err.error.message)
        assertTrue(state.tracks is LCE.Error)
        assertTrue(state.artists is LCE.Error)
    }

    @Test
    fun `Succeeded with a null inner game leaves state untouched (no spurious update)`() = runTest {
        // Reducer's `data.data ?: return` guard: a Succeeded-but-null payload is a contract
        // violation; the VM ignores it rather than crashing or mis-flipping notFound. This is
        // distinct from Data.Empty (which IS the documented "no row" signal).
        val source = sharedFlowOf<Data<Game?>>().also { it.tryEmit(Data.Succeeded(null)) }
        val vm = newViewModel(gameId = 1L, repository = repoWithGame(source))

        val state = vm.state.first()
        assertEquals(LCE.Uninitialized, state.game)
        assertFalse(state.notFound, "notFound must stay false — null Succeeded isn't Empty")
    }

    @Test
    fun `playingTrackId mirrors Director metadata id`() = runTest {
        val director = FakeDirector()
        val vm = newViewModel(gameId = 1L, director = director)
        assertNull(vm.state.first().playingTrackId)

        director.emitMetadata(trackOf(99L, "Now Playing"))
        val state = vm.state.first { it.playingTrackId == 99L }
        assertEquals(99L, state.playingTrackId)
    }

    @Test
    fun `ArtistClicked emits NavigateTo ArtistDetail with the right id`() = runTest {
        val vm = newViewModel(gameId = 1L)
        val event = collectAndDispatch(vm, GameDetailAction.ArtistClicked(id = 7L))
        assertTrue(event is ChipboxEvent.NavigateTo)
        assertEquals(ArtistDetail(7L), event.destination)
    }

    // PlayAllClicked / ShuffleAllClicked / TrackClicked all call director.start(session). The
    // FakeDirector's start() is a no-op stub, so we can't assert the session shape here — that's
    // covered in RealDirectorTest's session-handling path. We do exercise the code paths to keep
    // the action-handler reachable.

    @Test
    fun `PlayAllClicked routes to the start session path without throwing`() = runTest {
        val vm = newViewModel(gameId = 1L)
        vm.sendAction(GameDetailAction.PlayAllClicked)
    }

    @Test
    fun `ShuffleAllClicked routes to the start session path without throwing`() = runTest {
        val vm = newViewModel(gameId = 1L)
        vm.sendAction(GameDetailAction.ShuffleAllClicked)
    }

    @Test
    fun `TrackClicked routes to the start session path without throwing`() = runTest {
        val vm = newViewModel(gameId = 1L)
        vm.sendAction(GameDetailAction.TrackClicked(position = 2))
    }

    // ---- helpers ----

    private fun newViewModel(
        gameId: Long,
        repository: Repository = FakeRepository(emptyMap()),
        director: FakeDirector = FakeDirector(),
    ) = GameDetailViewModel(
        gameId = gameId,
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

    private fun repoWithGame(flow: Flow<Data<Game?>>): Repository =
        object : Repository by FakeRepository(emptyMap()) {
            override fun getGame(id: Long, withTracks: Boolean, withArtists: Boolean) = flow
        }

    private suspend fun CoroutineScope.collectAndDispatch(
        vm: GameDetailViewModel,
        action: GameDetailAction,
    ): ChipboxEvent = async(start = CoroutineStart.UNDISPATCHED) { vm.events.first() }
        .also { vm.sendAction(action) }
        .await()

    private fun gameOf(id: Long, title: String, tracks: List<Track>? = null, artists: List<Artist>? = null): Game =
        Game(id = id, title = title, photoUrl = null, artists = artists, tracks = tracks)

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

    private fun artistOf(id: Long, name: String): Artist =
        Artist(id = id, name = name, photoUrl = null, tracks = null, games = null)

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = string.toString()
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = string.toString()
    }
}
