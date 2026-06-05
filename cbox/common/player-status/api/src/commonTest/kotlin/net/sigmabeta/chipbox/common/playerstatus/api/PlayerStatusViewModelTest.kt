package net.sigmabeta.chipbox.common.playerstatus.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.sigmabeta.chipbox.player.director.fake.FakeDirector
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.director.ChipboxPlaybackState
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the small `combine(metadataState, playbackState) → PlayerStatusState` reducer in
 * [PlayerStatusViewModel] plus the `sendAction` dispatch.
 *
 * Rig: `Dispatchers.setMain(UnconfinedTestDispatcher())` so `viewModelScope` (and the `stateIn`
 * it backs) runs on a deterministic test dispatcher. Unconfined makes every emission propagate
 * synchronously, so a fresh [FakeDirector.emitMetadata]/[FakeDirector.emitPlayback] is visible
 * on `viewModel.state.first(...)` immediately after the suspending emit returns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerStatusViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Empty until any metadata arrives`() = runTest {
        val director = FakeDirector()
        val viewModel = PlayerStatusViewModel(director, BluntHatchet())
        // FakeDirector seeds metadata=null + IDLE playback; the reducer maps null track → Empty.
        assertEquals(PlayerStatusState.Empty, viewModel.state.first())
    }

    @Test
    fun `metadata plus PLAYING state produces a visible row with title and isPlaying true`() = runTest {
        val director = FakeDirector()
        val viewModel = PlayerStatusViewModel(director, BluntHatchet())

        director.emitMetadata(trackOf("Schala"))
        director.emitPlayback(PlayerState.PLAYING)

        val state = viewModel.state.first { it.visible }
        assertTrue(state.visible, "non-null track + PLAYING should make the row visible")
        assertTrue(state.isPlaying)
        assertFalse(state.isBuffering)
        assertFalse(state.isError)
        assertEquals("Schala", state.title)
    }

    @Test
    fun `BUFFERING state surfaces as isPlaying and isBuffering both true`() = runTest {
        // Documented in the reducer: BUFFERING counts as "playing" for the play/pause toggle but
        // also as buffering for the spinner. Both true together.
        val director = FakeDirector()
        val viewModel = PlayerStatusViewModel(director, BluntHatchet())

        director.emitMetadata(trackOf("Schala"))
        director.emitPlayback(PlayerState.BUFFERING)

        val state = viewModel.state.first { it.isBuffering }
        assertTrue(state.isPlaying)
        assertTrue(state.isBuffering)
        assertFalse(state.isError)
    }

    @Test
    fun `ERROR state surfaces isError and clears the play affordance`() = runTest {
        val director = FakeDirector()
        val viewModel = PlayerStatusViewModel(director, BluntHatchet())

        director.emitMetadata(trackOf("Schala"))
        director.emitPlayback(PlayerState.ERROR)

        val state = viewModel.state.first { it.isError }
        assertTrue(state.isError)
        assertFalse(state.isPlaying)
    }

    @Test
    fun `IDLE and STOPPED both render as not visible`() = runTest {
        // The visibility predicate explicitly excludes both: nothing for the UI to show when the
        // player is dormant. Without this the bar would animate in only to read "Unknown" + a
        // stale title on cold start.
        val director = FakeDirector()
        val viewModel = PlayerStatusViewModel(director, BluntHatchet())

        director.emitMetadata(trackOf("Schala"))
        director.emitPlayback(PlayerState.IDLE)
        assertFalse(viewModel.state.first { it.title == "Schala" }.visible)

        director.emitPlayback(PlayerState.STOPPED)
        assertFalse(viewModel.state.first { !it.visible || it.title == "Schala" }.visible)
    }

    @Test
    fun `artistsCaption joins multiple artists with comma-space`() = runTest {
        // The reducer renders `artists?.joinToString(", ")` — verify the separator is the one
        // the row's caption renders, since the now-playing UI hangs the second artist off this.
        val director = FakeDirector()
        val viewModel = PlayerStatusViewModel(director, BluntHatchet())

        director.emitMetadata(trackOf("Theme", artistNames = listOf("Composer A", "Composer B")))
        director.emitPlayback(PlayerState.PLAYING)

        val state = viewModel.state.first { it.title == "Theme" }
        assertEquals("Composer A, Composer B", state.artistsCaption)
    }

    @Test
    fun `null artists renders as an empty caption`() = runTest {
        // The Track model's artists field is nullable — orEmpty() means a missing list collapses
        // to "" rather than "null" or crashing the joinToString.
        val director = FakeDirector()
        val viewModel = PlayerStatusViewModel(director, BluntHatchet())

        director.emitMetadata(trackOf("Theme", artistNames = null))
        director.emitPlayback(PlayerState.PLAYING)

        val state = viewModel.state.first { it.title == "Theme" }
        assertEquals("", state.artistsCaption)
    }

    @Test
    fun `PlayPauseClicked action while playing dispatches pause`() = runTest {
        val director = FakeDirector()
        val viewModel = PlayerStatusViewModel(director, BluntHatchet())

        director.emitMetadata(trackOf("Schala"))
        director.emitPlayback(PlayerState.PLAYING)
        // Let the state catch up to PLAYING before the click.
        viewModel.state.first { it.isPlaying }

        viewModel.sendAction(PlayerStatusAction.PlayPauseClicked)

        assertEquals(1, director.pauseCalls)
        assertEquals(0, director.playCalls)
    }

    @Test
    fun `PlayPauseClicked action while paused dispatches play`() = runTest {
        val director = FakeDirector()
        val viewModel = PlayerStatusViewModel(director, BluntHatchet())

        director.emitMetadata(trackOf("Schala"))
        director.emitPlayback(PlayerState.PAUSED)
        viewModel.state.first { !it.isPlaying && it.visible }

        viewModel.sendAction(PlayerStatusAction.PlayPauseClicked)

        assertEquals(1, director.playCalls)
        assertEquals(0, director.pauseCalls)
    }

    @Test
    fun `CardClicked action toggles no playback but is accepted`() = runTest {
        // Navigation is the host's job; the VM only logs the action. Verify it doesn't touch
        // the director (no play/pause leaking out of a card tap).
        val director = FakeDirector()
        val viewModel = PlayerStatusViewModel(director, BluntHatchet())

        director.emitMetadata(trackOf("Schala"))
        director.emitPlayback(PlayerState.PLAYING)
        viewModel.state.first { it.isPlaying }

        viewModel.sendAction(PlayerStatusAction.CardClicked)

        assertEquals(0, director.playCalls)
        assertEquals(0, director.pauseCalls)
    }

    @Test
    fun `metadata becoming null collapses back to Empty`() = runTest {
        // Session teardown emits a null metadata — the row should hide.
        val director = FakeDirector()
        val viewModel = PlayerStatusViewModel(director, BluntHatchet())

        director.emitMetadata(trackOf("Schala"))
        director.emitPlayback(PlayerState.PLAYING)
        viewModel.state.first { it.visible }

        director.emitMetadata(null)
        val cleared = viewModel.state.first { !it.visible }
        assertEquals(PlayerStatusState.Empty, cleared)
    }

    private fun trackOf(
        title: String,
        artistNames: List<String>? = listOf("Default Artist"),
    ): Track = Track(
        id = 1L,
        path = "/library/$title.psf",
        source = "test",
        title = title,
        trackLengthMs = 60_000L,
        trackNumber = 0,
        fadeLengthMs = 0L,
        game = Game(id = 1L, title = "Game", photoUrl = null, artists = null, tracks = null),
        artists = artistNames?.mapIndexed { index, name ->
            Artist(id = (index + 1).toLong(), name = name, photoUrl = null, tracks = null, games = null)
        },
        platform = Platform.OTHER,
    )

    @Suppress("unused")
    private fun playbackOf(state: PlayerState): ChipboxPlaybackState = ChipboxPlaybackState(
        state = state,
        position = 0L,
        generatorProducedMs = 0L,
        playbackSpeed = 1.0f,
        skipForwardAllowed = false,
        errorMessage = null,
    )
}
