package net.sigmabeta.chipbox.player.director.real

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
    fun `start with a SINGLE_TRACK session uses contentId as the track id with no position hint`() = runTest {
        // SINGLE_TRACK sessions are self-contained — contentId is the track, the resolved
        // setlist has one entry, no startingPosition needs to be passed.
        val (director, gen, _, _) = newDirector(listOf(track1, track2, track3))
        director.start(Session(type = SessionType.SINGLE_TRACK, contentId = 2L))
        assertEquals(listOf(2L), gen.startTrackCalls)
        director.release()
    }

    @Test
    fun `starting a new session while audio is flowing abandons the in-flight render and cuts over`() = runTest {
        // Regression: clicking a track starts a fresh session. If the generator is mid-render on a
        // slow/silent track, the new track must not be queued behind it — otherwise the stuck
        // track's eventual failure/stall is misattributed to, and skips, the just-clicked track.
        val (director, gen, speaker, _) = newDirector(listOf(track1, track2, track3))
        director.start(setlistSession(listOf(1L), startingPosition = 0))
        gen.emit(GeneratorEvent.Loading(1L))
        speaker.emit(SpeakerEvent.Playing(0L)) // session A is playing

        val stopsBefore = gen.stopCalls
        director.start(setlistSession(listOf(2L, 3L), startingPosition = 0)) // user clicks a new track

        assertTrue(gen.stopCalls > stopsBefore, "the in-flight render must be abandoned, not queued behind")
        assertEquals(2L, gen.startTrackCalls.last(), "the new track is started")
        assertEquals(listOf(2L), speaker.switchToCalls, "speaker cuts over to the new track")
        director.release()
    }

    @Test
    fun `a cold start does not stop the generator or cut the speaker over`() = runTest {
        // Cold start has nothing to abandon, and switching the speaker before the buffer manager is
        // initialised would start a consume loop with no pool — so the handoff is gated on there
        // being active audio.
        val (director, gen, speaker, _) = newDirector(listOf(track1))
        director.start(setlistSession(listOf(1L)))

        assertEquals(0, gen.stopCalls, "nothing to abandon on a cold start")
        assertTrue(speaker.switchToCalls.isEmpty(), "no cut-over before the buffer manager is initialised")
        assertEquals(listOf(1L), gen.startTrackCalls)
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
        gen.emit(GeneratorEvent.Loading(1L)) // -> BUFFERING
        speaker.emit(SpeakerEvent.Playing(positionMs = 0L)) // -> PLAYING

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
        speaker.emit(SpeakerEvent.Playing(0L)) // BUFFERING -> PLAYING
        speaker.emit(SpeakerEvent.Buffering(100L)) // PLAYING -> BUFFERING

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

    // ---- auto-advance / setlist scheduling ----

    @Test
    fun `Generator TrackChange mid-setlist auto-advances to the next track without a speaker switch`() = runTest {
        // The core scheduling path: when a track ends naturally the generator emits TrackChange and
        // the director queues the next setlist id. Unlike skip/error-skip this is NOT a forced cut
        // — playback has already reached end-of-buffer — so the speaker is left to roll into the
        // next track's buffers on its own (no switchTo, which would drop in-flight audio).
        val (director, gen, speaker, _) = newDirector(listOf(track1, track2, track3))
        director.start(setlistSession(listOf(1L, 2L, 3L), startingPosition = 0))
        gen.emit(GeneratorEvent.Loading(1L))
        speaker.emit(SpeakerEvent.Playing(0L))

        gen.emit(GeneratorEvent.TrackChange)

        assertEquals(listOf(1L, 2L), gen.startTrackCalls, "natural end should queue the next id")
        assertTrue(speaker.switchToCalls.isEmpty(), "auto-advance must not force a speaker switch")
        director.release()
    }

    @Test
    fun `Generator TrackChange on the last track transitions to ENDING and stops the generator`() = runTest {
        // Setlist exhausted: there's no next id to queue, so the director flips to ENDING and stops
        // the generator while the speaker plays out the audio already buffered. STOPPED comes later,
        // once the speaker drains (covered separately).
        val (director, gen, speaker, _) = newDirector(listOf(track1))
        director.start(setlistSession(listOf(1L), startingPosition = 0))
        gen.emit(GeneratorEvent.Loading(1L))
        speaker.emit(SpeakerEvent.Playing(0L))

        gen.emit(GeneratorEvent.TrackChange)

        val state = director.playbackState().first { it.state == PlayerState.ENDING }
        assertEquals(PlayerState.ENDING, state.state)
        assertEquals(listOf(1L), gen.startTrackCalls, "no further track should be queued")
        assertTrue(gen.stopCalls >= 1, "generator should be stopped once the setlist is exhausted")
        director.release()
    }

    @Test
    fun `the speaker draining while ENDING completes the setlist into STOPPED and tears both down`() = runTest {
        // The tail of the final track: ENDING + the speaker reporting it ran dry (Buffering) is the
        // signal that everything queued has now played. The director completes the session into
        // STOPPED and tears down both halves of the pipeline.
        val (director, gen, speaker, _) = newDirector(listOf(track1))
        director.start(setlistSession(listOf(1L)))
        gen.emit(GeneratorEvent.Loading(1L))
        speaker.emit(SpeakerEvent.Playing(0L))
        gen.emit(GeneratorEvent.TrackChange) // -> ENDING

        speaker.emit(SpeakerEvent.Buffering(0L)) // speaker drained -> Setlist complete

        val state = director.playbackState().first { it.state == PlayerState.STOPPED }
        assertEquals(PlayerState.STOPPED, state.state)
        assertEquals(1, speaker.stopCalls, "speaker sink torn down on completion")
        assertTrue(gen.stopCalls >= 1, "generator torn down on completion")
        director.release()
    }

    @Test
    fun `a straggler Emitting from a track already advanced past is ignored`() = runTest {
        // After auto-advancing, a late buffer from the outgoing track can still surface. It must be
        // dropped: applying it would rewind the high-water mark (and clear the failure streak)
        // against audio the user is no longer hearing.
        val (director, gen, _, _) = newDirector(listOf(track1, track2, track3))
        director.start(setlistSession(listOf(1L, 2L, 3L), startingPosition = 0))
        gen.emit(GeneratorEvent.Loading(1L))
        gen.emit(GeneratorEvent.TrackChange) // now on track 2

        // A real buffer for the current track advances the high-water mark.
        gen.emit(GeneratorEvent.Emitting(producedMs = 5_000L, trackId = 2L))
        val afterCurrent = director.playbackState().first { it.generatorProducedMs == 5_000L }
        assertEquals(5_000L, afterCurrent.generatorProducedMs)

        // A straggler from the already-finished track 1 must not move it.
        gen.emit(GeneratorEvent.Emitting(producedMs = 999_999L, trackId = 1L))
        val afterStraggler = director.playbackState().first()
        assertEquals(
            5_000L,
            afterStraggler.generatorProducedMs,
            "a buffer from a passed track must not move the high-water mark",
        )
        director.release()
    }

    @Test
    fun `loading the final track during a track change disallows skip-forward`() = runTest {
        // skipForwardAllowed gates the UI's skip button. It must track the live setlist position
        // across an in-flight track change, not just the cold-start load — otherwise the button
        // stays enabled on the last track and skipForward walks off the end.
        val (director, gen, speaker, _) = newDirector(listOf(track1, track2))
        director.start(setlistSession(listOf(1L, 2L), startingPosition = 0))

        gen.emit(GeneratorEvent.Loading(1L))
        val early = director.playbackState().first { it.state == PlayerState.BUFFERING }
        assertTrue(early.skipForwardAllowed, "skip-forward allowed while a later track remains")

        speaker.emit(SpeakerEvent.Playing(0L))
        gen.emit(GeneratorEvent.TrackChange) // advance to the final track
        gen.emit(GeneratorEvent.Loading(2L)) // track change in flight, from PLAYING

        val onLast = director.playbackState().first { !it.skipForwardAllowed }
        assertTrue(!onLast.skipForwardAllowed, "skip-forward disallowed once the final track loads")
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
        assertTrue(
            state.errorMessage?.contains("consecutive track failures") == true,
            "expected the give-up message; got '${state.errorMessage}'"
        )
        director.release()
    }

    @Test
    fun `a successful Emitting between errors resets the failure streak`() = runTest {
        // Verifies the streak counter resets on real audio — without this, a marginal track that
        // occasionally fails after playing successfully would eventually trip the cutoff.
        val tracks = (1L..6L).map { trackOf(it, "Track $it") }
        val (director, gen, _, _) = newDirector(tracks)
        director.start(setlistSession((1L..6L).toList(), startingPosition = 0))

        gen.emit(GeneratorEvent.Error("first hiccup")) // streak = 1
        gen.emit(GeneratorEvent.Emitting(producedMs = 1L, trackId = 2L)) // streak reset
        gen.emit(GeneratorEvent.Error("second hiccup")) // streak = 1 again
        gen.emit(GeneratorEvent.Error("third hiccup")) // streak = 2 (NOT >= 3)

        // No ERROR state — director should still be advancing. Verify by checking it isn't ERROR.
        val state = director.playbackState().first()
        assertTrue(
            state.state != PlayerState.ERROR,
            "streak should have reset on the successful Emitting; got ${state.state}"
        )
        director.release()
    }

    // ---- stall watchdog ----

    @Test
    fun `no generator progress for the stall timeout skips the track`() = runTest {
        // The director (not the speaker / PCM source) owns the "no audio for too long" guard. With
        // the generator silent past the timeout, the stalled track is skipped like any bad track.
        val (director, gen, speaker, _) = newDirector(listOf(track1, track2, track3))
        director.start(setlistSession(listOf(1L, 2L, 3L), startingPosition = 0))
        gen.emit(GeneratorEvent.Loading(1L)) // arms the watchdog
        speaker.emit(SpeakerEvent.Playing(0L))

        advanceUntilIdle() // let the 5s stall timer fire

        assertEquals(listOf(1L, 2L), gen.startTrackCalls, "stall should skip to the next track")
        assertEquals(listOf(2L), speaker.switchToCalls)
        assertTrue(gen.stopCalls >= 1, "the wedged generator loop must be cancelled before the skip")
        director.release()
    }

    @Test
    fun `a stall on the last track stops the session`() = runTest {
        val (director, gen, _, _) = newDirector(listOf(track1))
        director.start(setlistSession(listOf(1L), startingPosition = 0))
        gen.emit(GeneratorEvent.Loading(1L)) // arms the watchdog

        advanceUntilIdle()

        val state = director.playbackState().first { it.state == PlayerState.STOPPED }
        assertEquals(PlayerState.STOPPED, state.state, "no track to skip to -> stop")
        assertEquals(listOf(1L), gen.startTrackCalls, "nothing new queued")
        director.release()
    }

    @Test
    fun `render progress keeps resetting the watchdog so a long uncached seek does not stall`() = runTest {
        // The key seek case: the writer renders forward (cachedMs climbs) for far longer than the
        // stall timeout, but because progress keeps arriving the guard never trips.
        val (director, gen, speaker, _) = newDirector(listOf(track1, track2))
        director.start(setlistSession(listOf(1L, 2L), startingPosition = 0))
        gen.emit(GeneratorEvent.Loading(1L))
        speaker.emit(SpeakerEvent.Playing(0L))

        repeat(10) { i ->
            advanceTimeBy(3_000) // each gap is < 5s, but they sum to 30s
            gen.emit(GeneratorEvent.Rendering(cachedMs = (i + 1) * 1_000L))
        }
        runCurrent()

        assertEquals(listOf(1L), gen.startTrackCalls, "a progressing render must not be treated as a stall")
        assertTrue(speaker.switchToCalls.isEmpty())
        director.release()
    }

    @Test
    fun `a render that stops progressing eventually trips the watchdog`() = runTest {
        // Same render-wait, but the watermark wedges: identical (non-advancing) progress doesn't
        // reset the timer, so the guard still fires.
        val (director, gen, speaker, _) = newDirector(listOf(track1, track2))
        director.start(setlistSession(listOf(1L, 2L), startingPosition = 0))
        gen.emit(GeneratorEvent.Loading(1L))
        speaker.emit(SpeakerEvent.Playing(0L))
        gen.emit(GeneratorEvent.Rendering(cachedMs = 1_000L)) // some progress, then nothing more

        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), gen.startTrackCalls, "a wedged render should still be caught")
        assertEquals(listOf(2L), speaker.switchToCalls)
        director.release()
    }

    @Test
    fun `a paused session is not mistaken for a stall`() = runTest {
        // Paused audio is meant to be silent; the watchdog must be disarmed so the gap isn't read
        // as a fault.
        val (director, gen, speaker, _) = newDirector(listOf(track1, track2))
        director.start(setlistSession(listOf(1L, 2L), startingPosition = 0))
        gen.emit(GeneratorEvent.Loading(1L))
        speaker.emit(SpeakerEvent.Playing(0L))

        director.pause()
        advanceUntilIdle()

        assertEquals(listOf(1L), gen.startTrackCalls, "pause must not skip")
        assertTrue(speaker.switchToCalls.isEmpty())
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
    fun `skipForward abandons the in-flight render before starting the next track`() = runTest {
        // Same clean-handoff as start(): a skip must stop the current render rather than queue the
        // next track behind a possibly-stuck one, keeping the model and generator in sync.
        val (director, gen, speaker, _) = newDirector(listOf(track1, track2, track3))
        director.start(setlistSession(listOf(1L, 2L, 3L), startingPosition = 0))
        gen.emit(GeneratorEvent.Loading(1L))
        speaker.emit(SpeakerEvent.Playing(0L)) // track 1 playing

        val stopsBefore = gen.stopCalls
        director.skipForward()

        assertTrue(gen.stopCalls > stopsBefore, "skip must abandon the current render, not queue behind it")
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
        speaker.currentPositionMsValue = 500L // under SKIP_BACK_THRESHOLD_MS
        speaker.emit(SpeakerEvent.Playing(500L))

        director.skipBack()

        // startTrackCalls now contains [2 (initial), 1 (previous)].
        assertEquals(listOf(2L, 1L), gen.startTrackCalls)
        assertEquals(listOf(1L), speaker.switchToCalls)
        director.release()
    }

    @Test
    fun `skipBack on the first track within the threshold restarts it instead of underflowing`() = runTest {
        // Even under the 3s threshold, there's no previous track to cross to at position 0, so
        // "back" must restart the current track (seek 0) rather than indexing to -1.
        val (director, gen, speaker, _) = newDirector(listOf(track1, track2))
        director.start(setlistSession(listOf(1L, 2L), startingPosition = 0))
        gen.emit(GeneratorEvent.Loading(1L))
        speaker.currentPositionMsValue = 500L // under SKIP_BACK_THRESHOLD_MS
        speaker.emit(SpeakerEvent.Playing(500L))

        director.skipBack()

        assertEquals(listOf(0L), gen.seekCalls, "first-track back restarts via generator.seek(0)")
        assertEquals(1, speaker.seekCalls)
        assertTrue(speaker.switchToCalls.isEmpty(), "no track change off the first track")
        assertEquals(listOf(1L), gen.startTrackCalls, "no previous track should be loaded")
        director.release()
    }

    @Test
    fun `skipForward after auto-advancing to the last track is a no-op`() = runTest {
        // Position is tracked through auto-advance, so skipForward respects the end of the setlist
        // even when we arrived at the last track by natural progression rather than a cold start.
        val (director, gen, speaker, _) = newDirector(listOf(track1, track2))
        director.start(setlistSession(listOf(1L, 2L), startingPosition = 0))
        gen.emit(GeneratorEvent.Loading(1L))
        speaker.emit(SpeakerEvent.Playing(0L))
        gen.emit(GeneratorEvent.TrackChange) // auto-advance to the final track (position 1)

        director.skipForward()

        assertEquals(listOf(1L, 2L), gen.startTrackCalls, "no extra startTrack past the last track")
        assertTrue(speaker.switchToCalls.isEmpty(), "no forced switch on a no-op skip")
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
    fun `play after a setlist completes restarts the final track from the beginning`() = runTest {
        // Regression: finishing a setlist leaves the director STOPPED with the generator loop and
        // speaker sink torn down, but the now-playing UI still shows the last track. Tapping play
        // used to call only generator.play(), which spins the (re-launched) generation loop on an
        // empty channel forever because no track was ever queued — so nothing played. play() must
        // re-issue the current track via startTrack instead.
        val (director, gen, speaker, _) = newDirector(listOf(track1, track2))
        director.start(setlistSession(listOf(1L, 2L), startingPosition = 0))
        gen.emit(GeneratorEvent.Loading(1L)) // -> BUFFERING (track 1)
        speaker.emit(SpeakerEvent.Playing(0L)) // -> PLAYING

        // Track 1 ends; director auto-advances to the final track (track 2).
        gen.emit(GeneratorEvent.TrackChange)
        gen.emit(GeneratorEvent.Loading(2L))
        speaker.emit(SpeakerEvent.Playing(0L))
        assertEquals(listOf(1L, 2L), gen.startTrackCalls, "should have advanced to the last track")

        // Final track ends: TrackChange with no track left -> ENDING, then the speaker drains and
        // emits Buffering -> the director completes the setlist into STOPPED.
        gen.emit(GeneratorEvent.TrackChange)
        speaker.emit(SpeakerEvent.Buffering(0L))
        val stopped = director.playbackState().first { it.state == PlayerState.STOPPED }
        assertEquals(PlayerState.STOPPED, stopped.state, "setlist should complete into STOPPED")

        // The user taps play. The director must re-queue the track still on screen (track 2),
        // not merely poke the idle generator.
        director.play()

        assertEquals(
            listOf(1L, 2L, 2L),
            gen.startTrackCalls,
            "play() after completion should restart the final track via startTrack",
        )
        director.release()
    }

    @Test
    fun `pauseTemporarily pauses the speaker and reports PAUSED`() = runTest {
        // A transient audio-focus loss stops the consume loop, so the state must reflect PAUSED
        // rather than leaving the UI showing PLAYING with no audio.
        val (director, gen, speaker, _) = newDirector(listOf(track1))
        director.start(setlistSession(listOf(1L)))
        gen.emit(GeneratorEvent.Loading(1L))
        speaker.emit(SpeakerEvent.Playing(0L))

        director.pauseTemporarily()

        val state = director.playbackState().first { it.state == PlayerState.PAUSED }
        assertEquals(PlayerState.PAUSED, state.state)
        assertEquals(1, speaker.pauseCalls)
        director.release()
    }

    @Test
    fun `resumeFocus after a temporary pause returns to PLAYING`() = runTest {
        val (director, gen, speaker, _) = newDirector(listOf(track1))
        director.start(setlistSession(listOf(1L)))
        gen.emit(GeneratorEvent.Loading(1L))
        speaker.emit(SpeakerEvent.Playing(0L))
        director.pauseTemporarily()
        director.playbackState().first { it.state == PlayerState.PAUSED }

        director.resumeFocus()

        val state = director.playbackState().first { it.state == PlayerState.PLAYING }
        assertEquals(PlayerState.PLAYING, state.state)
        assertTrue(speaker.playCalls >= 1, "resumeFocus should restart the consume loop")
        director.release()
    }

    @Test
    fun `error-skip stops the failed generator loop before relaunching the next track`() = runTest {
        // Regression: relaunching via startTrack without first tearing down the failed loop can
        // race play() into an "Already looping" no-op, stranding the queued track. The skip must
        // stop the generator first.
        val (director, gen, speaker, _) = newDirector(listOf(track1, track2, track3))
        director.start(setlistSession(listOf(1L, 2L, 3L), startingPosition = 0))

        gen.emit(GeneratorEvent.Error("emulator crashed"))

        assertEquals(listOf(1L, 2L), gen.startTrackCalls, "skip should still queue the next track")
        assertEquals(listOf(2L), speaker.switchToCalls)
        assertTrue(gen.stopCalls >= 1, "the failed loop must be stopped before the relaunch")
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
