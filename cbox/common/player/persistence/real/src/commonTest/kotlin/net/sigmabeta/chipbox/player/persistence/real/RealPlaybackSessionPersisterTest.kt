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
import net.sigmabeta.chipbox.player.persistence.SessionSnapshotFilter
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
    fun `the saved snapshot captures the live setlist order and modified flag`() = runTest {
        val director = FakeDirector()
        val store = FakePlaybackSessionStore()
        val persister = newPersister(director, store)
        persister.observe()

        director.emitSession(Session(type = SessionType.GAME, contentId = 3L, modified = true))
        director.emitMetadata(trackOf(2L))
        director.emitSetlist(listOf(3L, 1L, 2L))
        director.emitPlayback(playback(PlayerState.PLAYING, positionMs = 4_000L))
        director.emitPlayback(playback(PlayerState.PAUSED, positionMs = 8_000L))

        val saved = store.saveCalls.single()
        assertEquals(listOf(3L, 1L, 2L), saved.resolvedSetlist, "the live play order is persisted verbatim")
        assertTrue(saved.modified, "the user-edited flag is persisted")
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
    fun `a STOPPED caused by teardown after snapshotNow does not clear the snapshot`() = runTest {
        val director = FakeDirector()
        val store = FakePlaybackSessionStore()
        val persister = newPersister(director, store)
        persister.observe()
        director.emitSession(Session(type = SessionType.GAME, contentId = 3L))
        director.emitMetadata(trackOf(9L))
        director.emitPlayback(playback(PlayerState.PLAYING, positionMs = 12_000L))

        // Teardown net fires (onTaskRemoved/onDestroy), then releasing the player drives STOPPED.
        persister.snapshotNow()
        director.emitPlayback(playback(PlayerState.STOPPED, positionMs = 0L))

        assertEquals(0, store.clearCalls, "teardown STOPPED must not wipe what snapshotNow saved")
        assertEquals(3L, store.stored?.contentId)
        assertEquals(12_000L, store.stored?.positionMs)
        persister.release()
    }

    @Test
    fun `playback resuming after a teardown snapshot re-arms the STOPPED clear`() = runTest {
        val director = FakeDirector()
        val store = FakePlaybackSessionStore()
        val persister = newPersister(director, store)
        persister.observe()
        director.emitSession(Session(type = SessionType.GAME, contentId = 3L))
        director.emitMetadata(trackOf(9L))
        director.emitPlayback(playback(PlayerState.PLAYING, positionMs = 12_000L))
        persister.snapshotNow()

        // Backgrounded-but-still-playing session keeps going and plays its setlist to the end:
        // the PLAYING tick clears the teardown guard, so the terminal STOPPED clears as normal.
        director.emitPlayback(playback(PlayerState.PLAYING, positionMs = 30_000L))
        director.emitPlayback(playback(PlayerState.STOPPED, positionMs = 0L))

        assertTrue(store.clearCalls >= 1, "a genuine stop after resumed playback still clears")
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

    @Test
    fun `a pre-restore filter rewrites the snapshot before it reaches the director`() = runTest {
        val director = FakeDirector()
        val store = FakePlaybackSessionStore(
            SessionSnapshot(
                type = SessionType.SETLIST,
                contentId = 0L,
                resolvedSetlist = listOf(404L, 7L),
                currentTrackId = 404L,
                positionMs = 3_000L,
            ),
        )
        val persister = newPersister(
            director,
            store,
            filter = SessionSnapshotFilter { snapshot ->
                snapshot.copy(resolvedSetlist = listOf(7L), explicitSetlist = listOf(7L), currentTrackId = 7L)
            },
        )

        persister.restore()

        val (session, positionMs) = director.restoreCalls.single()
        assertEquals(listOf(7L), session.explicitSetlist)
        assertEquals(7L, session.startingTrackId)
        assertEquals(3_000L, positionMs)
        persister.release()
    }

    @Test
    fun `a pre-restore filter that drops the snapshot clears it and restores nothing`() = runTest {
        val director = FakeDirector()
        val store = FakePlaybackSessionStore(
            SessionSnapshot(type = SessionType.GAME, contentId = 1L, currentTrackId = 404L),
        )
        val persister = newPersister(director, store, filter = SessionSnapshotFilter { null })

        persister.restore()

        assertTrue(director.restoreCalls.isEmpty(), "nothing survives -> nothing restored")
        assertTrue(store.clearCalls >= 1, "the stale snapshot is cleared so every reload does not retry it")
        assertNull(store.stored)
        persister.release()
    }

    @Test
    fun `a failing pre-restore filter falls back to the saved snapshot`() = runTest {
        val director = FakeDirector()
        val store = FakePlaybackSessionStore(
            SessionSnapshot(type = SessionType.GAME, contentId = 1L, currentTrackId = 42L),
        )
        val persister = newPersister(
            director,
            store,
            filter = SessionSnapshotFilter { error("repository unavailable") },
        )

        persister.restore()

        assertEquals(42L, director.restoreCalls.single().first.startingTrackId)
        persister.release()
    }

    @Test
    fun `a visibility flush does not suppress a later real stop`() = runTest {
        val director = FakeDirector()
        val store = FakePlaybackSessionStore()
        val persister = newPersister(director, store)
        persister.observe()
        director.emitSession(Session(type = SessionType.GAME, contentId = 3L))
        director.emitMetadata(trackOf(9L))
        director.emitPlayback(playback(PlayerState.PLAYING, positionMs = 12_000L))

        // Backgrounding the tab saves the position but is not teardown: the page stays alive.
        persister.snapshotNow(teardown = false)
        assertEquals(3L, store.stored?.contentId)

        // The user returns (no PLAYING tick yet) and stops: the stop must still clear.
        director.emitPlayback(playback(PlayerState.STOPPED, positionMs = 0L))

        assertTrue(store.clearCalls >= 1, "a non-teardown flush must not disarm the stop clear")
        assertNull(store.stored)
        persister.release()
    }

    @Test
    fun `a restored page after a teardown flush clears on the next real stop`() = runTest {
        val director = FakeDirector()
        val store = FakePlaybackSessionStore()
        val persister = newPersister(director, store)
        persister.observe()
        director.emitSession(Session(type = SessionType.GAME, contentId = 3L))
        director.emitMetadata(trackOf(9L))
        director.emitPlayback(playback(PlayerState.PLAYING, positionMs = 12_000L))

        // `pagehide` flushes as teardown, then the browser serves the page from BFCache and fires
        // `pageshow`; the page is alive again, so the guard must not outlive the restore.
        persister.snapshotNow()
        persister.resumeAfterTeardown()
        director.emitPlayback(playback(PlayerState.STOPPED, positionMs = 0L))

        assertTrue(store.clearCalls >= 1, "a real stop after a BFCache restore must clear the snapshot")
        assertNull(store.stored)
        persister.release()
    }

    // ---- helpers ----

    private fun TestScope.newPersister(
        director: FakeDirector,
        store: FakePlaybackSessionStore,
        filter: SessionSnapshotFilter? = null,
    ): RealPlaybackSessionPersister = RealPlaybackSessionPersister(
        director = director,
        store = store,
        hatchet = BluntHatchet(),
        dispatcher = UnconfinedTestDispatcher(testScheduler),
        filter = filter,
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
