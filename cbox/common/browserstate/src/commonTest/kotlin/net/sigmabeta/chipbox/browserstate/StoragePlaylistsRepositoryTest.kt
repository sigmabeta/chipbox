package net.sigmabeta.chipbox.browserstate

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StoragePlaylistsRepositoryTest {

    @Test
    fun `ids are monotonic across reloads and clears`() = runTest {
        val storage = FakeStorage()
        val events = BrowserStateEvents()
        val clock = FakeClock()
        val repo = StoragePlaylistsRepository(storage, events, clock::now)

        val first = repo.createPlaylist("First")!!
        val second = repo.createPlaylist("Second")!!
        assertEquals(listOf(1L, 2L), listOf(first, second))

        val reloaded = StoragePlaylistsRepository(storage, events, clock::now)
        assertEquals(3L, reloaded.createPlaylist("Third")!!, "next-id is persisted, not recomputed from a counter")

        reloaded.clearAll()
        val afterClear = StoragePlaylistsRepository(storage, events, clock::now)
        assertEquals(4L, afterClear.createPlaylist("After clear")!!, "clear never reuses an id")
    }

    @Test
    fun `playlist list is newest-created first`() = runTest {
        val repo = StoragePlaylistsRepository(FakeStorage(), BrowserStateEvents(), FakeClock()::now)

        repo.createPlaylist("Old")
        repo.createPlaylist("Middle")
        repo.createPlaylist("New")

        val playlists = repo.playlists().first()
        assertEquals(listOf("New", "Middle", "Old"), playlists.map { it.name })
        assertEquals(listOf(3L, 2L, 1L), playlists.map { it.id })
        assertEquals(listOf(0, 0, 0), playlists.map { it.trackCount })
    }

    @Test
    fun `membership keeps insertion order and append de-dupes`() = runTest {
        val repo = StoragePlaylistsRepository(FakeStorage(), BrowserStateEvents(), FakeClock()::now)
        val id = repo.createPlaylist("Ordered")!!

        repo.addTracks(id, listOf(10L, 11L, 10L, 12L))
        assertEquals(listOf(10L, 11L, 12L), repo.trackIds(id).first(), "dupes in one append collapse")

        repo.addTracks(id, listOf(11L, 13L))
        assertEquals(listOf(10L, 11L, 12L, 13L), repo.trackIds(id).first(), "already-present ids are skipped")
        assertEquals(4, repo.playlist(id).first()?.trackCount)
    }

    @Test
    fun `rename delete add remove and reorder all persist`() = runTest {
        val storage = FakeStorage()
        val events = BrowserStateEvents()
        val clock = FakeClock()
        val repo = StoragePlaylistsRepository(storage, events, clock::now)
        val id = repo.createPlaylist("Original")!!
        repo.addTracks(id, listOf(1L, 2L, 3L))

        repo.renamePlaylist(id, "Renamed")
        repo.removeTrack(id, 1L)
        repo.setTrackOrder(id, listOf(3L, 2L, 3L))

        val reloaded = StoragePlaylistsRepository(storage, events, clock::now)
        assertEquals("Renamed", reloaded.playlist(id).first()?.name)
        assertEquals(listOf(3L, 2L), reloaded.trackIds(id).first(), "reorder replaces membership and drops dupes")

        reloaded.deletePlaylist(id)
        val afterDelete = StoragePlaylistsRepository(storage, events, clock::now)
        assertNull(afterDelete.playlist(id).first())
        assertEquals(emptyList(), afterDelete.trackIds(id).first())
    }

    @Test
    fun `duplicate members rehydrate as one and track order survives`() = runTest {
        val storage = FakeStorage(
            mapOf(
                BrowserStateKeys.PLAYLISTS to
                    """{"schemaVersion":1,"payload":{"nextId":2,"playlists":[{"id":1,"name":"P","createdAtMs":5,"trackIds":[9,4,9,7]}]}}""",
            ),
        )
        val repo = StoragePlaylistsRepository(storage, BrowserStateEvents(), FakeClock()::now)

        assertEquals(listOf(9L, 4L, 7L), repo.trackIds(1L).first())
        assertEquals(3, repo.playlist(1L).first()?.trackCount)
    }

    @Test
    fun `mutating a missing playlist is a no-op`() = runTest {
        val repo = StoragePlaylistsRepository(FakeStorage(), BrowserStateEvents(), FakeClock()::now)

        repo.renamePlaylist(99L, "nope")
        repo.addTracks(99L, listOf(1L))
        repo.removeTrack(99L, 1L)
        repo.setTrackOrder(99L, listOf(1L))
        repo.deletePlaylist(99L)

        assertEquals(emptyList(), repo.playlists().first())
    }

    @Test
    fun `unknown track ids are kept in the store`() = runTest {
        val repo = StoragePlaylistsRepository(FakeStorage(), BrowserStateEvents(), FakeClock()::now)
        val id = repo.createPlaylist("Missing ids")!!
        repo.addTracks(id, listOf(7L, 404L))

        assertEquals(listOf(7L, 404L), repo.trackIds(id).first(), "the store persists ids, not models")
        assertEquals(2, repo.playlist(id).first()?.trackCount, "the count tracks persisted membership")
    }

    @Test
    fun `another tab's write reaches a live playlist collector`() = runTest {
        val storage = FakeStorage()
        val events = BrowserStateEvents()
        val repo = StoragePlaylistsRepository(storage, events, FakeClock()::now)
        val seen = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.playlists().collect { rows -> seen += rows.map { it.name } }
        }

        val external = BrowserStateCodecs.playlists.encode(
            PlaylistsState(
                nextId = 2,
                playlists = listOf(PlaylistEntry(id = 1L, name = "From another tab", createdAtMs = 1L)),
            ),
        )
        storage.writeFromOtherTab(BrowserStateKeys.PLAYLISTS, external)

        assertEquals(listOf(emptyList(), listOf("From another tab")), seen)
    }

    @Test
    fun `corrupt playlists fall back to empty and surface a message`() = runTest {
        val storage = FakeStorage(mapOf(BrowserStateKeys.PLAYLISTS to "not json"))
        val events = BrowserStateEvents()
        val seen = collectEvents(events)
        val repo = StoragePlaylistsRepository(storage, events, FakeClock()::now)

        assertEquals(emptyList(), repo.playlists().first())
        assertTrue(seen.any { it.reason == BrowserStateReason.CORRUPT })
    }

    @Test
    fun `a newer playlists schema falls back safely`() = runTest {
        val storage = FakeStorage(
            mapOf(BrowserStateKeys.PLAYLISTS to """{"schemaVersion":42,"payload":{"whatever":true}}"""),
        )
        val events = BrowserStateEvents()
        val seen = collectEvents(events)

        val repo = StoragePlaylistsRepository(storage, events, FakeClock()::now)

        assertEquals(emptyList(), repo.playlists().first())
        assertTrue(seen.any { it.reason == BrowserStateReason.UNSUPPORTED_VERSION })
    }

    @Test
    fun `clearing corrupt playlists rewrites the key so a reload is empty and usable`() = runTest {
        val storage = FakeStorage(mapOf(BrowserStateKeys.PLAYLISTS to "{corrupt"))
        val events = BrowserStateEvents()
        val repo = StoragePlaylistsRepository(storage, events, FakeClock()::now)

        repo.clearAll()

        assertTrue(
            BrowserStateCodecs.playlists.decode(storage.raw(BrowserStateKeys.PLAYLISTS)) is DecodeOutcome.Success,
            "the corrupt string is replaced, not left in storage: ${storage.raw(BrowserStateKeys.PLAYLISTS)}",
        )
        val reloaded = StoragePlaylistsRepository(storage, events, FakeClock()::now)
        assertEquals(emptyList(), reloaded.playlists().first(), "a reload stays empty")
        assertEquals(1L, reloaded.createPlaylist("After repair"), "the store is usable again")
    }

    @Test
    fun `a quota failure on create is caught, does not throw, and returns no id`() = runTest {
        val storage = FakeStorage()
        val events = BrowserStateEvents()
        val seen = collectEvents(events)
        val repo = StoragePlaylistsRepository(storage, events, FakeClock()::now)
        storage.failWrites = true

        val id = repo.createPlaylist("No room")

        assertNull(id, "a failed write must not hand back an id for a playlist that does not exist")
        assertTrue(seen.any { it.reason == BrowserStateReason.WRITE_FAILED })
        assertEquals(emptyList(), repo.playlists().first())
    }

    private fun TestScope.collectEvents(events: BrowserStateEvents): MutableList<BrowserStateEvent> {
        val seen = mutableListOf<BrowserStateEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            events.events.collect { seen += it }
        }
        return seen
    }
}
