package net.sigmabeta.chipbox.browserstate

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import net.sigmabeta.chipbox.player.common.RepeatMode
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.persistence.SessionSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The web playback-session store: one versioned envelope at the shared session key. The raw
 * (unversioned) snapshot older builds wrote is schema version 0, so upgrading never loses a
 * resumable session.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StoragePlaybackSessionStoreTest {

    private val snapshot = SessionSnapshot(
        type = SessionType.GAME,
        contentId = 7L,
        explicitSetlist = listOf(10L, 11L),
        sourceName = "Game",
        currentTrackId = 11L,
        shuffled = true,
        repeatMode = RepeatMode.ALL,
        positionMs = 42_000L,
        resolvedSetlist = listOf(10L, 11L, 12L),
        modified = true,
    )

    @Test
    fun `a saved snapshot survives store recreation and clear drops it`() = runTest {
        val storage = FakeStorage()
        val events = BrowserStateEvents()
        val store = StoragePlaybackSessionStore(storage, events)

        store.save(snapshot)

        assertTrue(
            storage.raw(BrowserStateKeys.PLAYBACK_SESSION)!!.contains("\"schemaVersion\":1"),
            "the snapshot is stored inside the versioned envelope",
        )
        assertEquals(snapshot, StoragePlaybackSessionStore(storage, events).load())

        store.clear()

        assertNull(StoragePlaybackSessionStore(storage, events).load(), "a real stop must not resurrect on reload")
    }

    @Test
    fun `a legacy raw snapshot is read as version zero and reported as migrated`() = runTest {
        val legacy = Json.encodeToString(SessionSnapshot.serializer(), snapshot)
        val storage = FakeStorage(mapOf(BrowserStateKeys.PLAYBACK_SESSION to legacy))
        val events = BrowserStateEvents()
        val seen = collectEvents(events)

        val loaded = StoragePlaybackSessionStore(storage, events).load()

        assertEquals(snapshot, loaded)
        assertTrue(seen.any { it.reason == BrowserStateReason.MIGRATED }, "$seen")
    }

    @Test
    fun `corrupt session data falls back to none with a non-fatal message`() = runTest {
        val storage = FakeStorage(mapOf(BrowserStateKeys.PLAYBACK_SESSION to "{not json"))
        val events = BrowserStateEvents()
        val seen = collectEvents(events)

        assertNull(StoragePlaybackSessionStore(storage, events).load())
        assertTrue(seen.any { it.reason == BrowserStateReason.CORRUPT }, "$seen")
    }

    @Test
    fun `a newer session schema falls back to none safely`() = runTest {
        val storage = FakeStorage(
            mapOf(BrowserStateKeys.PLAYBACK_SESSION to """{"schemaVersion":99,"payload":{"snapshot":null}}"""),
        )
        val events = BrowserStateEvents()
        val seen = collectEvents(events)

        assertNull(StoragePlaybackSessionStore(storage, events).load())
        assertTrue(seen.any { it.reason == BrowserStateReason.UNSUPPORTED_VERSION }, "$seen")
    }

    @Test
    fun `clearing corrupt session data rewrites the key and stays cleared`() = runTest {
        val storage = FakeStorage(mapOf(BrowserStateKeys.PLAYBACK_SESSION to "{not json"))
        val events = BrowserStateEvents()
        val store = StoragePlaybackSessionStore(storage, events)
        assertNull(store.load(), "corrupt data reads as no session")

        store.clear()

        assertTrue(
            BrowserStateCodecs.playbackSession.decode(storage.raw(BrowserStateKeys.PLAYBACK_SESSION)) is DecodeOutcome.Success,
            "the corrupt string is replaced, not left in storage: ${storage.raw(BrowserStateKeys.PLAYBACK_SESSION)}",
        )
        assertNull(StoragePlaybackSessionStore(storage, events).load(), "a reload stays without a session")
        StoragePlaybackSessionStore(storage, events).save(snapshot)
        assertEquals(snapshot, StoragePlaybackSessionStore(storage, events).load(), "the store is usable again")
    }

    @Test
    fun `a quota failure on save is caught and surfaced without throwing`() = runTest {
        val storage = FakeStorage()
        val events = BrowserStateEvents()
        val seen = collectEvents(events)
        val store = StoragePlaybackSessionStore(storage, events)
        storage.failWrites = true

        store.save(snapshot)

        assertTrue(seen.any { it.reason == BrowserStateReason.WRITE_FAILED }, "$seen")
        assertNull(store.load())
    }

    @Test
    fun `a read failure falls back to none`() = runTest {
        val storage = FakeStorage()
        val events = BrowserStateEvents()
        val seen = collectEvents(events)
        val store = StoragePlaybackSessionStore(storage, events)
        storage.failReads = true

        assertNull(store.load())
        assertTrue(seen.any { it.reason == BrowserStateReason.READ_FAILED }, "$seen")
    }

    private fun TestScope.collectEvents(events: BrowserStateEvents): MutableList<BrowserStateEvent> {
        val seen = mutableListOf<BrowserStateEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            events.events.collect { seen += it }
        }
        return seen
    }
}
