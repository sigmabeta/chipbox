package net.sigmabeta.chipbox.player.director.real

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.favorites.fake.FakeFavoritesRepository
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.SessionRequest
import net.sigmabeta.chipbox.player.generator.fake.FakeGenerator
import net.sigmabeta.chipbox.player.speaker.fake.FakeSpeaker
import net.sigmabeta.chipbox.playlists.fake.FakePlaylistsRepository
import net.sigmabeta.chipbox.repository.fake.FakeRepository
import net.sigmabeta.chipbox.settings.fake.FakeChipboxSettingsManager
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Browser autoplay policies — Safari in particular — only unlock an `AudioContext` when
 * `resume()` runs inside the user's click handler. [RealDirector.request] is the last synchronous
 * point on that path: everything after it hops onto `directorScope`. These tests pin the speaker
 * [net.sigmabeta.chipbox.player.speaker.Speaker.prepareForPlayback] call to that synchronous
 * window, so a future refactor can't move it into a coroutine and silently break first-play in
 * Safari.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealDirectorGestureUnlockTest {

    private val track = Track(
        id = 1L,
        path = "/library/track.psc",
        source = "test",
        title = "Track One",
        trackLengthMs = 60_000L,
        trackNumber = 0,
        fadeLengthMs = 0L,
        game = null,
        artists = null,
        platform = Platform.OTHER,
    )

    private fun TestScope.newDirector(
        dispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
    ): DirectorBundle {
        val gen = FakeGenerator()
        val speaker = FakeSpeaker()
        val director = RealDirector(
            generator = gen,
            speaker = speaker,
            repository = FakeRepository(mapOf(track.id to track)),
            playlistsRepository = FakePlaylistsRepository(),
            favoritesRepository = FakeFavoritesRepository(),
            settingsManager = FakeChipboxSettingsManager(),
            hatchet = BluntHatchet(),
            // Standard (not Unconfined) so a call that must happen *before* the launch is
            // observably synchronous: the launched work is queued until advanceUntilIdle().
            dispatcher = dispatcher,
        )
        return DirectorBundle(director, gen, speaker)
    }

    private fun setlistSession(): Session = Session(
        type = SessionType.SETLIST,
        contentId = 0L,
        explicitSetlist = listOf(track.id),
        startingPosition = 0,
    )

    @Test
    fun `Start unlocks the speaker synchronously before the director launches any work`() = runTest {
        val (director, gen, speaker) = newDirector()

        director.request(SessionRequest.Start(setlistSession()))

        assertEquals(1, speaker.prepareCalls, "the play gesture must reach the sink synchronously")
        assertTrue(gen.startTrackCalls.isEmpty(), "track startup belongs to the async half")

        advanceUntilIdle()
        assertEquals(listOf(track.id), gen.startTrackCalls)
        director.release()
    }

    @Test
    fun `Play unlocks the speaker synchronously before the director resumes`() = runTest {
        val (director, gen, speaker) = newDirector()
        director.request(SessionRequest.Start(setlistSession()))
        advanceUntilIdle()
        gen.startTrackCalls.clear()
        val unlocksBefore = speaker.prepareCalls

        director.request(SessionRequest.Play)

        assertEquals(
            unlocksBefore + 1,
            speaker.prepareCalls,
            "resume is also a user gesture and must unlock first",
        )
        assertTrue(gen.playCalls == 0, "the generator resume is queued, not synchronous")

        advanceUntilIdle()
        assertEquals(1, gen.playCalls)
        director.release()
    }

    @Test
    fun `Pause and Stop do not unlock the sink`() = runTest {
        val (director, gen, speaker) = newDirector()
        director.request(SessionRequest.Start(setlistSession()))
        advanceUntilIdle()

        director.request(SessionRequest.Pause)
        director.request(SessionRequest.Stop)
        advanceUntilIdle()

        assertEquals(1, speaker.prepareCalls, "only the play gestures unlock; transport pause/stop do not")
        assertTrue(gen.stopCalls >= 1)
        director.release()
    }

    @Test
    fun `a single-track failure surfaces ERROR rather than a normal stop`() = runTest {
        // The last-track error path used to land STOPPED, so the mini-player showed a play icon
        // and a missing WASM module looked like an ordinary end-of-track. A failed load is a
        // failure: ERROR (with the message) drives the warning icon and the app-level snackbar.
        val (director, gen, _) = newDirector(dispatcher = UnconfinedTestDispatcher(testScheduler))
        director.request(SessionRequest.Start(setlistSession()))

        gen.emit(
            net.sigmabeta.chipbox.player.generator.GeneratorEvent.Error(
                "Couldn't load the 'gme' emulator module from /wasm/chipbox_gme.js",
            ),
        )

        val state = director.playbackState().first { it.state == net.sigmabeta.chipbox.player.director.PlayerState.ERROR }
        assertTrue(
            state.errorMessage?.contains("chipbox_gme.js") == true,
            "the actionable load error must survive into playback state; got '${state.errorMessage}'",
        )
        director.release()
    }

    private data class DirectorBundle(
        val director: RealDirector,
        val generator: FakeGenerator,
        val speaker: FakeSpeaker,
    )
}
