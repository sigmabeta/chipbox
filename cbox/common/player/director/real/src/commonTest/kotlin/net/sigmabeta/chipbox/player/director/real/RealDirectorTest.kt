package net.sigmabeta.chipbox.player.director.real

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.player.generator.fake.FakeGenerator
import net.sigmabeta.chipbox.player.speaker.fake.FakeSpeaker
import net.sigmabeta.chipbox.repository.fake.FakeRepository
import net.sigmabeta.chipbox.player.generator.GeneratorEvent
import net.sigmabeta.chipbox.player.speaker.SpeakerEvent
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioural tests for the playback state machine in [RealDirector]. With the new
 * [net.sigmabeta.chipbox.player.generator.Generator] and
 * [net.sigmabeta.chipbox.player.speaker.Speaker] interfaces, the Director takes plain in-memory
 * fakes — no buffer manager, no content-source registry, no abstract base classes — so each test
 * can exercise one reducer path without the rest of the pipeline.
 *
 * All sessions use [SessionType.SETLIST] so the explicit `explicitSetlist` carries the track ids
 * directly; that sidesteps [FakeRepository]'s unimplemented `getTracksFor*` methods.
 *
 * `UnconfinedTestDispatcher` makes every `directorScope.launch { ... }` run synchronously on the
 * test thread, so a state change triggered by an emitted event is visible on
 * `playbackState().first()` immediately after the `emit` returns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealDirectorTest {

    private val track1 = trackOf(1L, "Track One")
    private val track2 = trackOf(2L, "Track Two")
    private val track3 = trackOf(3L, "Track Three")

    // ---- start / track loading ----

    @Test
    fun `start with a SETLIST session calls generator startTrack with the first track id`() = runTest {
        val (director, gen, _, _) = newDirector(listOf(track1, track2, track3))
        director.start(setlistSession(listOf(1L, 2L, 3L), startingPosition = 0))
        assertEquals(listOf(1L), gen.startTrackCalls, "first id should be queued exactly once")
        director.release()
    }

    @Test
    fun `start at a non-zero startingPosition picks that track instead of the first`() = runTest {
        val (director, gen, _, _) = newDirector(listOf(track1, track2, track3))
        director.start(setlistSession(listOf(1L, 2L, 3L), startingPosition = 2))
        assertEquals(listOf(3L), gen.startTrackCalls)
        director.release()
    }

    @Test
    fun `Generator Loading from IDLE transitions to BUFFERING and emits metadata`() = runTest {
        // Cold path: state was IDLE, so the reducer emits the loading track's metadata and
        // flips to BUFFERING. This is what the now-playing UI subscribes to on first start.
        val (director, gen, _, _) = newDirector(listOf(track1, track2, track3))
        director.start(setlistSession(listOf(1L, 2L, 3L), startingPosition = 0))

        gen.emit(GeneratorEvent.Loading(trackId = 1L))

        val state = director.playbackState().first { it.state == PlayerState.BUFFERING }
        assertEquals(PlayerState.BUFFERING, state.state)
        assertTrue(state.skipForwardAllowed, "3-track setlist at position 0 should allow skip-forward")
        val track = director.metadataState().first { it?.id == 1L }
        assertEquals("Track One", track?.title)
        director.release()
    }

    @Test
    fun `Speaker Playing after BUFFERING transitions to PLAYING`() = runTest {
        val (director, gen, speaker, _) = newDirector(listOf(track1))
        director.start(setlistSession(listOf(1L)))
        gen.emit(GeneratorEvent.Loading(1L))           // -> BUFFERING
        speaker.emit(SpeakerEvent.Playing(positionMs = 0L))  // -> PLAYING

        val state = director.playbackState().first { it.state == PlayerState.PLAYING }
        assertEquals(PlayerState.PLAYING, state.state)
        director.release()
    }

    @Test
    fun `Speaker Buffering during PLAYING flips back to BUFFERING (underrun)`() = runTest {
        // A mid-track underrun: speaker queue ran dry while we were PLAYING. Producer-side
        // recovery flips BUFFERING -> PLAYING again on the next Speaker.Playing.
        val (director, gen, speaker, _) = newDirector(listOf(track1))
        director.start(setlistSession(listOf(1L)))
        gen.emit(GeneratorEvent.Loading(1L))
        speaker.emit(SpeakerEvent.Playing(0L))         // BUFFERING -> PLAYING
        speaker.emit(SpeakerEvent.Buffering(100L))     // PLAYING -> BUFFERING

        val state = director.playbackState().first { it.state == PlayerState.BUFFERING }
        assertEquals(PlayerState.BUFFERING, state.state)
        director.release()
    }

    @Test
    fun `Speaker TrackChange emits the new track's metadata`() = runTest {
        val (director, gen, speaker, _) = newDirector(listOf(track1, track2))
        director.start(setlistSession(listOf(1L, 2L)))
        gen.emit(GeneratorEvent.Loading(1L))
        speaker.emit(SpeakerEvent.TrackChange(trackId = 2L))

        val track = director.metadataState().first { it?.id == 2L }
        assertEquals("Track Two", track?.title)
        director.release()
    }

    // ---- generator-error recovery ----

    @Test
    fun `Generator Error mid-setlist skips to the next track and switches the speaker over`() = runTest {
        // Documented "bad track" path: the error is treated as a per-track failure, not a fatal
        // session error. The director advances the setlist position and calls speaker.switchTo
        // so any straggler audio from the failed track gets discarded.
        val (director, gen, speaker, _) = newDirector(listOf(track1, track2, track3))
        director.start(setlistSession(listOf(1L, 2L, 3L), startingPosition = 0))

        gen.emit(GeneratorEvent.Error("emulator crashed"))

        assertEquals(listOf(1L, 2L), gen.startTrackCalls, "skip should queue the next track id")
        assertEquals(listOf(2L), speaker.switchToCalls)
        director.release()
    }

    @Test
    fun `Generator Error on the last track stops the session`() = runTest {
        // Documented terminal case: no next track to skip to, so the session ends in STOPPED
        // (not ERROR — ERROR is reserved for the consecutive-failure cutoff).
        val (director, gen, _, _) = newDirector(listOf(track1))
        director.start(setlistSession(listOf(1L), startingPosition = 0))

        gen.emit(GeneratorEvent.Error("emulator crashed on last track"))

        val state = director.playbackState().first { it.state == PlayerState.STOPPED }
        assertEquals(PlayerState.STOPPED, state.state)
        director.release()
    }

    @Test
    fun `three consecutive generator errors with no audio in between transition to ERROR`() = runTest {
        // MAX_CONSECUTIVE_FAILURES = 3: once we've burned through three tracks back-to-back
        // without any of them producing audio, give up. Use a long enough setlist that the
        // "isCurrentTrackLastInSetlist" branch doesn't intervene first.
        val tracks = (1L..6L).map { trackOf(it, "Track $it") }
        val (director, gen, _, _) = newDirector(tracks)
        director.start(setlistSession((1L..6L).toList(), startingPosition = 0))

        repeat(3) { gen.emit(GeneratorEvent.Error("attempt $it failed")) }

        val state = director.playbackState().first { it.state == PlayerState.ERROR }
        assertEquals(PlayerState.ERROR, state.state)
        assertTrue(state.errorMessage?.contains("consecutive track failures") == true,
            "expected the give-up message; got '${state.errorMessage}'")
        director.release()
    }

    @Test
    fun `a successful Emitting between errors resets the failure streak`() = runTest {
        // Verifies the streak counter resets on real audio — without this, a marginal track that
        // occasionally fails after playing successfully would eventually trip the cutoff.
        val tracks = (1L..6L).map { trackOf(it, "Track $it") }
        val (director, gen, _, _) = newDirector(tracks)
        director.start(setlistSession((1L..6L).toList(), startingPosition = 0))

        gen.emit(GeneratorEvent.Error("first hiccup"))      // streak = 1
        gen.emit(GeneratorEvent.Emitting(producedMs = 1L, trackId = 2L)) // streak reset
        gen.emit(GeneratorEvent.Error("second hiccup"))     // streak = 1 again
        gen.emit(GeneratorEvent.Error("third hiccup"))      // streak = 2 (NOT >= 3)

        // No ERROR state — director should still be advancing. Verify by checking it isn't ERROR.
        val state = director.playbackState().first()
        assertTrue(state.state != PlayerState.ERROR,
            "streak should have reset on the successful Emitting; got ${state.state}")
        director.release()
    }

    // ---- skip controls ----

    @Test
    fun `skipForward on the last track in the setlist is a no-op`() = runTest {
        // Documented: skipForward respects skipForwardAllowed = !isLast. Without this guard
        // the director would walk off the end of the setlist.
        val (director, gen, speaker, _) = newDirector(listOf(track1))
        director.start(setlistSession(listOf(1L), startingPosition = 0))

        director.skipForward()

        assertEquals(listOf(1L), gen.startTrackCalls, "no extra startTrack should fire")
        assertTrue(speaker.switchToCalls.isEmpty(), "no speaker switch should happen either")
        director.release()
    }

    @Test
    fun `skipForward advances the setlist and switches the speaker over`() = runTest {
        val (director, gen, speaker, _) = newDirector(listOf(track1, track2, track3))
        director.start(setlistSession(listOf(1L, 2L, 3L), startingPosition = 0))

        director.skipForward()

        assertEquals(listOf(1L, 2L), gen.startTrackCalls)
        assertEquals(listOf(2L), speaker.switchToCalls)
        director.release()
    }

    @Test
    fun `skipBack past the 3 second threshold seeks the current track to zero`() = runTest {
        // Standard music-player semantics: enough into the track and "back" restarts it rather
        // than crossing a track boundary.
        val (director, gen, speaker, _) = newDirector(listOf(track1, track2))
        director.start(setlistSession(listOf(1L, 2L), startingPosition = 1))
        gen.emit(GeneratorEvent.Loading(2L))
        speaker.currentPositionMsValue = 5_000L
        speaker.emit(SpeakerEvent.Playing(5_000L)) // stamps position onto currentState

        director.skipBack()

        assertEquals(listOf(0L), gen.seekCalls, "in-track restart goes through generator.seek(0)")
        assertEquals(1, speaker.seekCalls)
        assertTrue(speaker.switchToCalls.isEmpty(), "no track change for an in-track seek")
        director.release()
    }

    @Test
    fun `skipBack within 3 seconds when not on first track jumps to the previous track`() = runTest {
        val (director, gen, speaker, _) = newDirector(listOf(track1, track2, track3))
        director.start(setlistSession(listOf(1L, 2L, 3L), startingPosition = 1))
        gen.emit(GeneratorEvent.Loading(2L))
        speaker.currentPositionMsValue = 500L            // under SKIP_BACK_THRESHOLD_MS
        speaker.emit(SpeakerEvent.Playing(500L))

        director.skipBack()

        // startTrackCalls now contains [2 (initial), 1 (previous)].
        assertEquals(listOf(2L, 1L), gen.startTrackCalls)
        assertEquals(listOf(1L), speaker.switchToCalls)
        director.release()
    }

    // ---- transport ----

    @Test
    fun `pause pauses the speaker and flips state to PAUSED`() = runTest {
        val (director, gen, speaker, _) = newDirector(listOf(track1))
        director.start(setlistSession(listOf(1L)))
        gen.emit(GeneratorEvent.Loading(1L))
        speaker.emit(SpeakerEvent.Playing(0L))

        director.pause()

        val state = director.playbackState().first { it.state == PlayerState.PAUSED }
        assertEquals(PlayerState.PAUSED, state.state)
        assertEquals(1, speaker.pauseCalls)
        director.release()
    }

    @Test
    fun `stop tears down both speaker and generator and flips state to STOPPED`() = runTest {
        val (director, gen, speaker, _) = newDirector(listOf(track1))
        director.start(setlistSession(listOf(1L)))
        gen.emit(GeneratorEvent.Loading(1L))

        director.stop()

        val state = director.playbackState().first { it.state == PlayerState.STOPPED }
        assertEquals(PlayerState.STOPPED, state.state)
        assertEquals(1, speaker.stopCalls)
        assertEquals(1, gen.stopCalls)
        director.release()
    }

    @Test
    fun `duck pass-through reaches the speaker`() = runTest {
        val (director, _, speaker, _) = newDirector(emptyList())
        director.duck()
        assertEquals(listOf(true), speaker.setDuckedCalls)
        director.release()
    }

    @Test
    fun `resumeFocus undoes duck and starts the speaker again`() = runTest {
        val (director, _, speaker, _) = newDirector(emptyList())
        director.duck()
        director.resumeFocus()
        assertEquals(listOf(true, false), speaker.setDuckedCalls)
        assertTrue(speaker.playCalls >= 1, "resumeFocus should kick the speaker's consume loop")
        director.release()
    }

    @Test
    fun `setVolume forwards the scale to the speaker`() = runTest {
        val (director, _, speaker, _) = newDirector(emptyList())
        director.setVolume(0.75)
        assertEquals(listOf(0.75), speaker.setVolumeCalls)
        director.release()
    }

    // ---- helpers ----

    /** Build a Director wired to fresh in-memory fakes. The unconfined dispatcher lets every
     *  directorScope launch run synchronously so a state change is visible the instant the
     *  triggering [emit] returns. */
    private fun TestScope.newDirector(tracks: List<Track>): DirectorBundle {
        val gen = FakeGenerator()
        val speaker = FakeSpeaker()
        val repo = FakeRepository(tracks.associateBy { it.id })
        val director = RealDirector(
            generator = gen,
            speaker = speaker,
            repository = repo,
            hatchet = BluntHatchet(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        return DirectorBundle(director, gen, speaker, repo)
    }

    private data class DirectorBundle(
        val director: RealDirector,
        val generator: FakeGenerator,
        val speaker: FakeSpeaker,
        val repository: FakeRepository,
    )

    private fun setlistSession(setlist: List<Long>, startingPosition: Int = 0): Session = Session(
        type = SessionType.SETLIST,
        contentId = 0L,
        explicitSetlist = setlist,
        startingPosition = startingPosition,
    )

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
}
