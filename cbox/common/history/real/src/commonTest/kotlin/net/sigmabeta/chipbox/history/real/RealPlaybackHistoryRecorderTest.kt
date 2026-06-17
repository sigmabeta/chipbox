package net.sigmabeta.chipbox.history.real

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.history.fake.FakePlaybackHistoryRepository
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.ChipboxPlaybackState
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.player.director.fake.FakeDirector
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [RealPlaybackHistoryRecorder]. A [FakeDirector] feeds the metadata / playback /
 * session streams; a [FakePlaybackHistoryRepository] records which tracks got logged. The
 * unconfined dispatcher runs the observe collector synchronously so each emission's effect is
 * visible right after the emit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealPlaybackHistoryRecorderTest {

    @Test
    fun `records once when position reaches ten seconds`() = runTest {
        val director = FakeDirector()
        val repo = FakePlaybackHistoryRepository()
        val recorder = newRecorder(director, repo)
        recorder.observe()

        director.emitSession(session())
        director.emitMetadata(trackOf(9L))
        director.emitPlayback(playback(PlayerState.PLAYING, positionMs = 5_000L))
        director.emitPlayback(playback(PlayerState.PLAYING, positionMs = 10_000L))
        director.emitPlayback(playback(PlayerState.PLAYING, positionMs = 15_000L))

        assertEquals(listOf(9L), repo.recordedPlays.map { it.id }, "exactly one record, at the 10s crossing")
        recorder.release()
    }

    @Test
    fun `does not record when skipped before ten seconds`() = runTest {
        val director = FakeDirector()
        val repo = FakePlaybackHistoryRepository()
        val recorder = newRecorder(director, repo)
        recorder.observe()

        director.emitSession(session())
        director.emitMetadata(trackOf(9L, trackLengthMs = 60_000L))
        director.emitPlayback(playback(PlayerState.PLAYING, positionMs = 3_000L))
        // Skip to the next track (new setlist index) at 3s — A never reached 10s nor its end.
        director.emitSession(session(currentPosition = 1))
        director.emitMetadata(trackOf(10L))
        director.emitPlayback(playback(PlayerState.PLAYING, positionMs = 1_000L))

        assertEquals(emptyList(), repo.recordedPlays.map { it.id }, "an early skip records nothing")
        recorder.release()
    }

    @Test
    fun `records a short track when it plays to its end`() = runTest {
        val director = FakeDirector()
        val repo = FakePlaybackHistoryRepository()
        val recorder = newRecorder(director, repo)
        recorder.observe()

        director.emitSession(session())
        director.emitMetadata(trackOf(9L, trackLengthMs = 4_000L))
        director.emitPlayback(playback(PlayerState.PLAYING, positionMs = 4_000L))
        // Track drains to the end of the (single-track) setlist.
        director.emitPlayback(playback(PlayerState.STOPPED, positionMs = 4_000L))

        assertEquals(listOf(9L), repo.recordedPlays.map { it.id }, "a fully-played short track is recorded")
        recorder.release()
    }

    @Test
    fun `seeking back to the start does not re-record the same track`() = runTest {
        val director = FakeDirector()
        val repo = FakePlaybackHistoryRepository()
        val recorder = newRecorder(director, repo)
        recorder.observe()

        director.emitSession(session())
        director.emitMetadata(trackOf(9L))
        director.emitPlayback(playback(PlayerState.PLAYING, positionMs = 12_000L))
        // Seek back to the start (same track instance — no metadata/session change) and play past 10s again.
        director.emitPlayback(playback(PlayerState.PLAYING, positionMs = 0L))
        director.emitPlayback(playback(PlayerState.PLAYING, positionMs = 11_000L))

        assertEquals(listOf(9L), repo.recordedPlays.map { it.id }, "a seek-to-start must not re-arm recording")
        recorder.release()
    }

    @Test
    fun `re-records when navigating to a different track`() = runTest {
        val director = FakeDirector()
        val repo = FakePlaybackHistoryRepository()
        val recorder = newRecorder(director, repo)
        recorder.observe()

        director.emitSession(session())
        director.emitMetadata(trackOf(9L))
        director.emitPlayback(playback(PlayerState.PLAYING, positionMs = 11_000L))
        director.emitSession(session(currentPosition = 1))
        director.emitMetadata(trackOf(10L))
        director.emitPlayback(playback(PlayerState.PLAYING, positionMs = 11_000L))

        assertEquals(listOf(9L, 10L), repo.recordedPlays.map { it.id }, "each track-start records its own play")
        recorder.release()
    }

    @Test
    fun `records the song, its game, and all artists via recordPlay`() = runTest {
        val director = FakeDirector()
        val repo = FakePlaybackHistoryRepository()
        val recorder = newRecorder(director, repo)
        recorder.observe()

        val track = trackOf(9L).copy(
            gameId = 3L,
            artists = listOf(artistOf(1L), artistOf(2L)),
        )
        director.emitSession(session())
        director.emitMetadata(track)
        director.emitPlayback(playback(PlayerState.PLAYING, positionMs = 10_000L))

        val recorded = repo.recordedPlays.single()
        assertEquals(9L, recorded.id)
        assertEquals(3L, recorded.gameId)
        assertEquals(listOf(1L, 2L), recorded.artists?.map { it.id })
        recorder.release()
    }

    // ---- helpers ----

    private fun TestScope.newRecorder(
        director: FakeDirector,
        repo: FakePlaybackHistoryRepository,
    ): RealPlaybackHistoryRecorder = RealPlaybackHistoryRecorder(
        director = director,
        repository = repo,
        hatchet = BluntHatchet(),
        dispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    private fun session(currentPosition: Int? = null): Session = Session(
        type = SessionType.GAME,
        contentId = 3L,
        currentPosition = currentPosition,
        id = 1L,
    )

    private fun playback(state: PlayerState, positionMs: Long): ChipboxPlaybackState = ChipboxPlaybackState(
        state = state,
        position = positionMs,
        generatorProducedMs = 0L,
        playbackSpeed = 1.0f,
        skipForwardAllowed = false,
    )

    private fun trackOf(id: Long, trackLengthMs: Long = 60_000L): Track = Track(
        id = id,
        path = "/library/track$id.psf",
        source = "test",
        title = "Track $id",
        trackLengthMs = trackLengthMs,
        trackNumber = 0,
        fadeLengthMs = 0L,
        game = null,
        artists = null,
        platform = Platform.OTHER,
    )

    private fun artistOf(id: Long): Artist = Artist(
        id = id,
        name = "Artist $id",
        photoUrl = null,
        tracks = null,
        games = null,
    )
}
