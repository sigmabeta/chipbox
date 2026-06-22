package net.sigmabeta.chipbox.player.director.real

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.RepeatMode
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.ChipboxPlaybackState
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.player.generator.GeneratorEvent
import net.sigmabeta.chipbox.player.generator.fake.FakeGenerator
import net.sigmabeta.chipbox.player.speaker.fake.FakeSpeaker
import net.sigmabeta.chipbox.favorites.fake.FakeFavoritesRepository
import net.sigmabeta.chipbox.playlists.fake.FakePlaylistsRepository
import net.sigmabeta.chipbox.repository.fake.FakeRepository
import net.sigmabeta.chipbox.settings.fake.FakeChipboxSettingsManager
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

    @Test
    fun `TrackChange with repeat-one restarts the current track without advancing`() = runTest {
        val director = newDirector(tracks = 3)
        val model = playingModel(setlist = listOf(1L, 2L, 3L), position = 1, repeatMode = RepeatMode.ONE)

        val (next, effects) = director.reduce(model, GeneratorEvent.TrackChange)

        assertEquals(1, next.session?.currentPosition, "repeat-one leaves the position put")
        assertEquals(listOf(RealDirector.Effect.StartTrack(2L)), effects, "restart the same track")
        director.release()
    }

    @Test
    fun `TrackChange on the last track with repeat-all wraps back to the first track`() = runTest {
        val director = newDirector(tracks = 3)
        val model = playingModel(setlist = listOf(1L, 2L, 3L), position = 2, repeatMode = RepeatMode.ALL)

        val (next, effects) = director.reduce(model, GeneratorEvent.TrackChange)

        assertEquals(0, next.session?.currentPosition, "wrap back to the top")
        assertEquals(listOf(RealDirector.Effect.StartTrack(1L)), effects, "start the first track again")
        assertEquals(PlayerState.PLAYING, next.playback.state, "no ENDING transition while wrapping")
        director.release()
    }

    @Test
    fun `TrackChange mid-setlist with repeat-all still advances normally`() = runTest {
        // Repeat-all only changes the end-of-setlist behaviour; mid-setlist it's a plain advance.
        val director = newDirector(tracks = 3)
        val model = playingModel(setlist = listOf(1L, 2L, 3L), position = 0, repeatMode = RepeatMode.ALL)

        val (next, effects) = director.reduce(model, GeneratorEvent.TrackChange)

        assertEquals(1, next.session?.currentPosition)
        assertEquals(listOf(RealDirector.Effect.StartTrack(2L)), effects)
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
    fun `the first buffer of a restored session lands PAUSED without starting the speaker`() = runTest {
        // Restore loads the track but resumes paused: the first Emitting while BUFFERING with a
        // pending offset must NOT issue SpeakerPlay (that would start audio) — it lands PAUSED and
        // keeps pendingResumeMs for the first play() to seek to.
        val director = newDirector(tracks = 1)
        val model = bufferingModel(setlist = listOf(1L), position = 0, pendingResumeMs = 30_000L)

        val (next, effects) = director.reduce(model, GeneratorEvent.Emitting(producedMs = 100L, trackId = 1L))

        assertEquals(PlayerState.PAUSED, next.playback.state)
        assertEquals(30_000L, next.pendingResumeMs, "offset kept for the first play() to seek to")
        assertEquals(listOf(RealDirector.Effect.CancelWatchdog), effects, "speaker must not start")
        director.release()
    }

    @Test
    fun `the first buffer of a normal session kicks the speaker`() = runTest {
        // The non-restore counterpart: no pending offset, so the first buffer while BUFFERING starts
        // the speaker as usual.
        val director = newDirector(tracks = 1)
        val model = bufferingModel(setlist = listOf(1L), position = 0, pendingResumeMs = null)

        val (_, effects) = director.reduce(model, GeneratorEvent.Emitting(producedMs = 100L, trackId = 1L))

        assertEquals(
            listOf(RealDirector.Effect.SpeakerPlay, RealDirector.Effect.ArmWatchdog),
            effects,
        )
        director.release()
    }

    @Test
    fun `loading a restored session lands PAUSED and does not arm the stall watchdog`() = runTest {
        // A restore loads toward paused — no audio should flow until the user hits play — so the
        // Loading reduce must land PAUSED and must NOT arm the watchdog. Arming it let the watchdog
        // "recover" a deliberately-silent restore by skipping to (and starting) the next track.
        val director = newDirector(tracks = 1)
        val model = stoppedModel(setlist = listOf(1L), position = 0, pendingResumeMs = 22_988L)

        val (next, effects) = director.reduce(model, GeneratorEvent.Loading(1L))

        assertEquals(PlayerState.PAUSED, next.playback.state)
        assertEquals(22_988L, next.pendingResumeMs, "offset kept for the first play() to seek to")
        assertEquals(
            listOf(RealDirector.Effect.EmitMetadata(trackOf(1L))),
            effects,
            "metadata only — the watchdog must stay off for a paused restore",
        )
        director.release()
    }

    @Test
    fun `loading a fresh session lands BUFFERING and arms the watchdog`() = runTest {
        // The non-restore counterpart: no pending offset, so a cold load shows BUFFERING and arms
        // the stall guard while it waits for the first buffer.
        val director = newDirector(tracks = 1)
        val model = stoppedModel(setlist = listOf(1L), position = 0, pendingResumeMs = null)

        val (next, effects) = director.reduce(model, GeneratorEvent.Loading(1L))

        assertEquals(PlayerState.BUFFERING, next.playback.state)
        assertEquals(
            listOf(RealDirector.Effect.ArmWatchdog, RealDirector.Effect.EmitMetadata(trackOf(1L))),
            effects,
        )
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
            playlistsRepository = FakePlaylistsRepository(),
            favoritesRepository = FakeFavoritesRepository(),
            settingsManager = FakeChipboxSettingsManager(),
            hatchet = BluntHatchet(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )
    }

    private fun playingModel(
        setlist: List<Long>,
        position: Int,
        repeatMode: RepeatMode = RepeatMode.OFF,
    ): RealDirector.Model = RealDirector.Model(
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
            repeatMode = repeatMode,
        ),
        setlist = setlist,
        consecutiveFailures = 0,
        lastRenderProgressMs = 0L,
    )

    private fun bufferingModel(
        setlist: List<Long>,
        position: Int,
        pendingResumeMs: Long?,
    ): RealDirector.Model = RealDirector.Model(
        playback = ChipboxPlaybackState(
            state = PlayerState.BUFFERING,
            position = 0L,
            generatorProducedMs = 0L,
            playbackSpeed = 1.0f,
            skipForwardAllowed = false,
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
        pendingResumeMs = pendingResumeMs,
    )

    private fun stoppedModel(
        setlist: List<Long>,
        position: Int,
        pendingResumeMs: Long?,
    ): RealDirector.Model = RealDirector.Model(
        playback = ChipboxPlaybackState(
            state = PlayerState.STOPPED,
            position = 0L,
            generatorProducedMs = 0L,
            playbackSpeed = 1.0f,
            skipForwardAllowed = false,
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
        pendingResumeMs = pendingResumeMs,
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
