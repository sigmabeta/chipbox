package net.sigmabeta.chipbox.player.director.real

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.ChipboxPlaybackState
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.player.generator.GeneratorEvent
import net.sigmabeta.chipbox.player.generator.fake.FakeGenerator
import net.sigmabeta.chipbox.player.speaker.fake.FakeSpeaker
import net.sigmabeta.chipbox.repository.fake.FakeRepository
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [RealDirector]'s pure reducers. Where [RealDirectorTest] drives the whole machine
 * through its event flows and observes side effects via the fakes, these call `reduce(model, event)`
 * directly and assert the returned `(model, effects)` — no consume loop, no waiting on flows. This
 * is the payoff of making the reducers pure: the track-advance decision (and the error/stall policy
 * it shares) is verifiable as data.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealDirectorReducerTest {

    // ---- track advance ----

    @Test
    fun `TrackChange mid-setlist advances the position and asks to start the next track`() = runTest {
        val director = newDirector(tracks = 3)
        val model = playingModel(setlist = listOf(1L, 2L, 3L), position = 0)

        val (next, effects) = director.reduce(model, GeneratorEvent.TrackChange)

        assertEquals(1, next.session?.currentPosition, "position should advance")
        assertEquals(listOf(RealDirector.Effect.StartTrack(2L)), effects, "start next, no forced switch")
        director.release()
    }

    @Test
    fun `TrackChange on the last track ends the session and stops the generator`() = runTest {
        val director = newDirector(tracks = 1)
        val model = playingModel(setlist = listOf(1L), position = 0)

        val (next, effects) = director.reduce(model, GeneratorEvent.TrackChange)

        assertEquals(PlayerState.ENDING, next.playback.state)
        assertEquals(
            listOf(RealDirector.Effect.CancelWatchdog, RealDirector.Effect.StopGenerator),
            effects,
        )
        director.release()
    }

    // ---- error / stall policy (a stall is fed through reduce as a generator Error) ----

    @Test
    fun `a generator error mid-setlist skips - stop, advance, start, switch`() = runTest {
        val director = newDirector(tracks = 3)
        val model = playingModel(setlist = listOf(1L, 2L, 3L), position = 0)

        val (next, effects) = director.reduce(model, GeneratorEvent.Error("boom"))

        assertEquals(1, next.session?.currentPosition)
        assertEquals(1, next.consecutiveFailures)
        assertEquals(
            listOf(
                RealDirector.Effect.CancelWatchdog,
                RealDirector.Effect.PublishError("boom", 1L),
                RealDirector.Effect.StopGenerator,
                RealDirector.Effect.StartTrack(2L),
                RealDirector.Effect.SwitchSpeaker(2L),
            ),
            effects,
            "stop-before-start ordering matters for the relaunch race",
        )
        director.release()
    }

    @Test
    fun `a generator error on the last track stops the session`() = runTest {
        val director = newDirector(tracks = 1)
        val model = playingModel(setlist = listOf(1L), position = 0)

        val (next, effects) = director.reduce(model, GeneratorEvent.Error("boom"))

        assertEquals(PlayerState.STOPPED, next.playback.state)
        assertEquals(
            listOf(
                RealDirector.Effect.CancelWatchdog,
                RealDirector.Effect.PublishError("boom", 1L),
                RealDirector.Effect.StopSpeaker,
                RealDirector.Effect.StopGenerator,
            ),
            effects,
        )
        director.release()
    }

    // ---- emitting / rendering ----

    @Test
    fun `a straggler Emitting from a passed track is a no-op`() = runTest {
        val director = newDirector(tracks = 2)
        val model = playingModel(setlist = listOf(1L, 2L), position = 1) // current track is 2

        val (next, effects) = director.reduce(model, GeneratorEvent.Emitting(producedMs = 99L, trackId = 1L))

        assertEquals(model, next, "stale buffer must not change the model")
        assertEquals(emptyList(), effects)
        director.release()
    }

    @Test
    fun `Rendering re-arms the watchdog only when the watermark advances`() = runTest {
        val director = newDirector(tracks = 1)
        val model = playingModel(setlist = listOf(1L), position = 0).copy(lastRenderProgressMs = 1_000L)

        val (advanced, advancedEffects) = director.reduce(model, GeneratorEvent.Rendering(cachedMs = 1_500L))
        assertEquals(listOf(RealDirector.Effect.ArmWatchdog), advancedEffects, "progress re-arms")
        assertEquals(1_500L, advanced.lastRenderProgressMs)

        val (wedged, wedgedEffects) = director.reduce(model, GeneratorEvent.Rendering(cachedMs = 1_000L))
        assertEquals(emptyList(), wedgedEffects, "no progress -> let the timer run")
        assertEquals(1_000L, wedged.lastRenderProgressMs)
        director.release()
    }

    // ---- helpers ----

    private fun TestScope.newDirector(tracks: Int): RealDirector {
        val all = (1L..tracks).map { trackOf(it) }
        return RealDirector(
            generator = FakeGenerator(),
            speaker = FakeSpeaker(),
            repository = FakeRepository(all.associateBy { it.id }),
            hatchet = BluntHatchet(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )
    }

    private fun playingModel(setlist: List<Long>, position: Int): RealDirector.Model = RealDirector.Model(
        playback = ChipboxPlaybackState(
            state = PlayerState.PLAYING,
            position = 0L,
            generatorProducedMs = 0L,
            playbackSpeed = 1.0f,
            skipForwardAllowed = true,
        ),
        session = Session(
            type = SessionType.SETLIST,
            contentId = 0L,
            explicitSetlist = setlist,
            currentPosition = position,
        ),
        setlist = setlist,
        consecutiveFailures = 0,
        lastRenderProgressMs = 0L,
    )

    private fun trackOf(id: Long): Track = Track(
        id = id,
        path = "/library/track$id.psf",
        source = "test",
        title = "Track $id",
        trackLengthMs = 60_000L,
        trackNumber = 0,
        fadeLengthMs = 0L,
        game = null,
        artists = null,
        platform = Platform.OTHER,
    )
}
