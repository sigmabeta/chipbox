package net.sigmabeta.chipbox.features.nowplaying.real

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.director.ChipboxPlaybackState
import net.sigmabeta.chipbox.player.director.PlayerErrorEvent
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.player.director.fake.FakeDirector
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [NowPlayingViewModel] mirrors four Director flows into one state, dispatches user actions back
 * through the Director, and runs its own auto-clearing 3-error rolling log on top of the error
 * stream. The tests below pin each of those paths.
 *
 * Shared [TestCoroutineScheduler] between `Dispatchers.Main` and the runTest body so
 * `advanceTimeBy(ERROR_AUTO_CLEAR_MS)` actually advances the auto-clear job that lives on
 * viewModelScope (Main-confined).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = UnconfinedTestDispatcher(scheduler)

    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    // ---- init-time wire-up ----

    @Test
    fun `metadata flow lands in state track`() = runTest(dispatcher) {
        val director = FakeDirector()
        val vm = newViewModel(director)
        director.emitMetadata(trackOf(7L, "Aria"))
        val state = vm.state.first { it.track?.id == 7L }
        assertEquals("Aria", state.track?.title)
    }

    @Test
    fun `playback flow lands in state playback`() = runTest(dispatcher) {
        val director = FakeDirector()
        val vm = newViewModel(director)
        director.emitPlayback(playbackOf(PlayerState.PLAYING, position = 5_000L))
        val state = vm.state.first { it.playback?.state == PlayerState.PLAYING }
        assertEquals(5_000L, state.playback?.position)
    }

    @Test
    fun `playback IDLE emits NavigateBack`() = runTest(dispatcher) {
        // The screen is dead-on-arrival without a live session — director emits IDLE either at
        // startup (no session) or on stop. Bouncing back to the previous screen avoids rendering
        // a half-built player.
        val director = FakeDirector()
        val vm = newViewModel(director)
        val event = async(start = CoroutineStart.UNDISPATCHED) { vm.events.first() }
        director.emitPlayback(PlayerState.IDLE)
        assertTrue(event.await() is ChipboxEvent.NavigateBack)
    }

    // ---- transport actions ----

    @Test
    fun `PlayPauseClicked while PLAYING calls director pause`() = runTest(dispatcher) {
        val director = FakeDirector()
        val vm = newViewModel(director)
        director.emitPlayback(PlayerState.PLAYING)
        vm.state.first { it.playback?.state == PlayerState.PLAYING }
        vm.sendAction(NowPlayingAction.PlayPauseClicked)
        assertEquals(1, director.pauseCalls)
        assertEquals(0, director.playCalls)
    }

    @Test
    fun `PlayPauseClicked while PAUSED calls director play`() = runTest(dispatcher) {
        val director = FakeDirector()
        val vm = newViewModel(director)
        director.emitPlayback(PlayerState.PAUSED)
        vm.state.first { it.playback?.state == PlayerState.PAUSED }
        vm.sendAction(NowPlayingAction.PlayPauseClicked)
        assertEquals(1, director.playCalls)
        assertEquals(0, director.pauseCalls)
    }

    @Test
    fun `PlayPauseClicked while BUFFERING is treated as playing and calls pause`() = runTest(dispatcher) {
        // BUFFERING is "we'd be playing if we had bytes" — the UI shows pause, so a tap should
        // pause. Locks in the isPlaying() classification.
        val director = FakeDirector()
        val vm = newViewModel(director)
        director.emitPlayback(PlayerState.BUFFERING)
        vm.state.first { it.playback?.state == PlayerState.BUFFERING }
        vm.sendAction(NowPlayingAction.PlayPauseClicked)
        assertEquals(1, director.pauseCalls)
    }

    @Test
    fun `SkipForwardClicked SkipBackClicked SeekRequested forward through to the director`() = runTest(dispatcher) {
        val director = FakeDirector()
        val vm = newViewModel(director)
        vm.sendAction(NowPlayingAction.SkipForwardClicked)
        vm.sendAction(NowPlayingAction.SkipBackClicked)
        vm.sendAction(NowPlayingAction.SeekRequested(positionMs = 12_345L))
        assertEquals(1, director.skipForwardCalls)
        assertEquals(1, director.skipBackCalls)
        assertEquals(listOf(12_345L), director.seekCalls)
    }

    @Test
    fun `ShuffleClicked toggles director setShuffled based on the current session shuffle flag`() = runTest(dispatcher) {
        // The VM looks at session.shuffled and asks the director to flip it. Without a session
        // (the seed null), the toggle defaults to true.
        val director = FakeDirector()
        val vm = newViewModel(director)
        vm.sendAction(NowPlayingAction.ShuffleClicked)
        assertEquals(listOf(true), director.setShuffledCalls)
    }

    @Test
    fun `RepeatClicked cycles repeatMode in the OFF ALL ONE sequence`() = runTest(dispatcher) {
        val vm = newViewModel(FakeDirector())
        assertEquals(RepeatMode.OFF, vm.state.first().repeatMode)
        vm.sendAction(NowPlayingAction.RepeatClicked)
        assertEquals(RepeatMode.ALL, vm.state.value.repeatMode)
        vm.sendAction(NowPlayingAction.RepeatClicked)
        assertEquals(RepeatMode.ONE, vm.state.value.repeatMode)
        vm.sendAction(NowPlayingAction.RepeatClicked)
        assertEquals(RepeatMode.OFF, vm.state.value.repeatMode)
    }

    // ---- navigation / placeholder actions ----

    @Test
    fun `BackClicked emits NavigateBack`() = runTest(dispatcher) {
        val vm = newViewModel(FakeDirector())
        val event = collectAndDispatch(vm, NowPlayingAction.BackClicked)
        assertTrue(event is ChipboxEvent.NavigateBack)
    }

    @Test
    fun `PlayerSettingsClicked surfaces the coming-soon snackbar`() = runTest(dispatcher) {
        // Placeholder until the player settings screen lands. Guarding the literal so the test
        // fails loud when the route is wired and the author updates the assertion.
        val vm = newViewModel(FakeDirector())
        val event = collectAndDispatch(vm, NowPlayingAction.PlayerSettingsClicked)
        assertTrue(event is ChipboxEvent.ShowSnackbar)
        assertEquals("Player settings coming soon.", event.message)
    }

    // ---- error log ----

    @Test
    fun `errorEvents append a NowPlayingError with the track-prefixed message`() = runTest(dispatcher) {
        val director = FakeDirector()
        val vm = newViewModel(director)
        emitError(director, message = "decoder blew up", track = trackOf(1L, "Schala", gameTitle = "Chrono Trigger"))
        val state = vm.state.first { it.errors.isNotEmpty() }
        assertEquals(1, state.errors.size)
        // Game + title both ellipsized to 10 chars: "Chrono Tri…" + " - " + "Schala" + ": " + message
        assertEquals("Chrono Tri… - Schala: decoder blew up", state.errors.single().message)
    }

    @Test
    fun `errorEvents without a track skip the prefix entirely`() = runTest(dispatcher) {
        val director = FakeDirector()
        val vm = newViewModel(director)
        emitError(director, message = "session level error", track = null)
        val state = vm.state.first { it.errors.isNotEmpty() }
        assertEquals("session level error", state.errors.single().message)
    }

    @Test
    fun `error log caps at MAX_VISIBLE_ERRORS keeping only the newest 3`() = runTest(dispatcher) {
        val director = FakeDirector()
        val vm = newViewModel(director)
        repeat(5) { idx -> emitError(director, message = "err$idx", track = null) }
        val state = vm.state.first { it.errors.size == 3 }
        assertEquals(listOf("err2", "err3", "err4"), state.errors.map { it.message })
    }

    @Test
    fun `each error gets a monotonic id so individual rows are stably keyed`() = runTest(dispatcher) {
        val director = FakeDirector()
        val vm = newViewModel(director)
        emitError(director, message = "a", track = null)
        emitError(director, message = "b", track = null)
        val ids = vm.state.first { it.errors.size == 2 }.errors.map { it.id }
        assertEquals(2, ids.distinct().size)
        assertTrue(ids[1] > ids[0])
    }

    @Test
    fun `DismissErrorClicked removes only the targeted id`() = runTest(dispatcher) {
        val director = FakeDirector()
        val vm = newViewModel(director)
        emitError(director, message = "a", track = null)
        emitError(director, message = "b", track = null)
        val errs = vm.state.first { it.errors.size == 2 }.errors
        vm.sendAction(NowPlayingAction.DismissErrorClicked(id = errs.first().id))
        val after = vm.state.first { it.errors.size == 1 }
        assertEquals("b", after.errors.single().message)
    }

    @Test
    fun `the error log auto-clears the whole section after the 10s window`() = runTest(dispatcher) {
        // The VM (re)starts a sliding 10s timer on each new error and wipes the list when it
        // fires. Verify the wipe by advancing past the window.
        val director = FakeDirector()
        val vm = newViewModel(director)
        emitError(director, message = "boom", track = null)
        vm.state.first { it.errors.isNotEmpty() }
        advanceTimeBy(ERROR_AUTO_CLEAR_MS + 1)
        assertTrue(vm.state.first { it.errors.isEmpty() }.errors.isEmpty())
    }

    @Test
    fun `a fresh error within the window restarts the auto-clear timer`() = runTest(dispatcher) {
        // A burst of errors should NOT auto-clear at the original 10s mark — the timer slides
        // with each new error. Verify by advancing 9s, firing again, then advancing 5s more
        // (well past the original 10s but only 5s into the new window).
        val director = FakeDirector()
        val vm = newViewModel(director)
        emitError(director, message = "first", track = null)
        vm.state.first { it.errors.isNotEmpty() }
        advanceTimeBy(9_000L)
        emitError(director, message = "second", track = null)
        advanceTimeBy(5_000L)
        assertEquals(2, vm.state.value.errors.size, "Second error reset the timer; log is still up")
    }

    // ---- helpers ----

    private fun newViewModel(director: FakeDirector) = NowPlayingViewModel(
        director = director,
        stringProvider = stubStringProvider(),
        hatchet = BluntHatchet(),
    )

    private suspend fun emitError(director: FakeDirector, message: String, track: Track?) {
        director.emitErrorSink(PlayerErrorEvent(message = message, track = track))
    }

    private fun trackOf(
        id: Long,
        title: String,
        gameTitle: String? = null,
        artistName: String? = null,
    ): Track = Track(
        id = id,
        path = "/library/$title.psf",
        source = "test",
        title = title,
        trackLengthMs = 60_000L,
        trackNumber = 0,
        fadeLengthMs = 0L,
        game = gameTitle?.let { Game(id = 1, title = it, photoUrl = null, artists = null, tracks = null) },
        artists = artistName?.let { listOf(Artist(id = 1, name = it, photoUrl = null, tracks = null, games = null)) },
        platform = Platform.OTHER,
    )

    private fun playbackOf(state: PlayerState, position: Long = 0L): ChipboxPlaybackState = ChipboxPlaybackState(
        state = state,
        position = position,
        generatorProducedMs = 0L,
        playbackSpeed = 1.0f,
        skipForwardAllowed = false,
        errorMessage = null,
    )

    private suspend fun CoroutineScope.collectAndDispatch(
        vm: NowPlayingViewModel,
        action: NowPlayingAction,
    ): ChipboxEvent = async(start = CoroutineStart.UNDISPATCHED) { vm.events.first() }
        .also { vm.sendAction(action) }
        .await()

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = string.toString()
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = string.toString()
    }

    private companion object {
        const val ERROR_AUTO_CLEAR_MS = 10_000L
    }
}
