package net.sigmabeta.chipbox.player.persistence.real

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
import net.sigmabeta.chipbox.player.director.fake.FakeDirector
import net.sigmabeta.chipbox.player.persistence.SessionSnapshot
import net.sigmabeta.chipbox.player.persistence.fake.FakePlaybackSessionStore
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [RealPlaybackSessionPersister]. A [FakeDirector] feeds the session / playback /
 * metadata streams the persister observes, and a [FakePlaybackSessionStore] records what it
 * decides to save or clear. The unconfined test dispatcher makes the observe collector run
 * synchronously so each emission's effect is visible right after the emit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealPlaybackSessionPersisterTest {

    @Test
    fun `restore maps the saved snapshot to a session and hands it to the director`() = runTest {
        val director = FakeDirector()
        val store = FakePlaybackSessionStore(
            SessionSnapshot(type = SessionType.GAME, contentId = 7L, currentTrackId = 42L, positionMs = 5_000L),
        )
        val persister = newPersister(director, store)

        persister.restore()

        assertEquals(1, director.restoreCalls.size)
        val (session, positionMs) = director.restoreCalls.single()
        assertEquals(SessionType.GAME, session.type)
        assertEquals(7L, session.contentId)
        assertEquals(42L, session.startingTrackId, "saved track id becomes the starting hint")
        assertEquals(5_000L, positionMs)
        persister.release()
    }

    @Test
    fun `restore with no saved snapshot does nothing`() = runTest {
        val director = FakeDirector()
        val store = FakePlaybackSessionStore(initial = null)
        val persister = newPersister(director, store)

        persister.restore()

        assertTrue(director.restoreCalls.isEmpty(), "nothing saved -> nothing restored")
        persister.release()
    }

    @Test
    fun `a play to pause transition saves the current session, track, and position`() = runTest {
        val director = FakeDirector()
        val store = FakePlaybackSessionStore()
        val persister = newPersister(director, store)
        persister.observe()

        director.emitSession(Session(type = SessionType.GAME, contentId = 3L))
        director.emitMetadata(trackOf(9L))
        director.emitPlayback(playback(PlayerState.PLAYING, positionMs = 4_000L))
        director.emitPlayback(playback(PlayerState.PAUSED, positionMs = 8_000L))

        assertEquals(1, store.saveCalls.size, "exactly one save, on the pause edge")
        val saved = store.saveCalls.single()
        assertEquals(3L, saved.contentId)
        assertEquals(9L, saved.currentTrackId)
        assertEquals(8_000L, saved.positionMs)
        persister.release()
    }

    @Test
    fun `entering PAUSED from BUFFERING does not save (a restore must not clobber its own snapshot)`() = runTest {
        val director = FakeDirector()
        val store = FakePlaybackSessionStore()
        val persister = newPersister(director, store)
        persister.observe()

        director.emitSession(Session(type = SessionType.GAME, contentId = 3L))
        director.emitPlayback(playback(PlayerState.BUFFERING, positionMs = 0L))
        director.emitPlayback(playback(PlayerState.PAUSED, positionMs = 0L))

        assertTrue(store.saveCalls.isEmpty(), "only PLAYING -> PAUSED saves, not BUFFERING -> PAUSED")
        persister.release()
    }

    @Test
    fun `reaching STOPPED clears the saved snapshot`() = runTest {
        val director = FakeDirector()
        val store = FakePlaybackSessionStore(SessionSnapshot(type = SessionType.GAME, contentId = 1L))
        val persister = newPersister(director, store)
        persister.observe()

        director.emitPlayback(playback(PlayerState.STOPPED, positionMs = 0L))

        assertTrue(store.clearCalls >= 1)
        assertNull(store.stored)
        persister.release()
    }

    @Test
    fun `snapshotNow writes the latest position even without a pause`() = runTest {
        val director = FakeDirector()
        val store = FakePlaybackSessionStore()
        val persister = newPersister(director, store)
        persister.observe()
        director.emitSession(Session(type = SessionType.ARTIST, contentId = 5L))
        director.emitMetadata(trackOf(11L))
        director.emitPlayback(playback(PlayerState.PLAYING, positionMs = 20_000L))

        persister.snapshotNow()

        val saved = store.saveCalls.last()
        assertEquals(5L, saved.contentId)
        assertEquals(11L, saved.currentTrackId)
        assertEquals(20_000L, saved.positionMs)
        persister.release()
    }

    // ---- helpers ----

    private fun TestScope.newPersister(
        director: FakeDirector,
        store: FakePlaybackSessionStore,
    ): RealPlaybackSessionPersister = RealPlaybackSessionPersister(
        director = director,
        store = store,
        hatchet = BluntHatchet(),
        dispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    private fun playback(state: PlayerState, positionMs: Long): ChipboxPlaybackState = ChipboxPlaybackState(
        state = state,
        position = positionMs,
        generatorProducedMs = 0L,
        playbackSpeed = 1.0f,
        skipForwardAllowed = false,
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
