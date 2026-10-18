package net.sigmabeta.chipbox.browserstate

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StorageFavoritesRepositoryTest {

    @Test
    fun `favorites survive repository recreation`() = runTest {
        val storage = FakeStorage()
        val events = BrowserStateEvents()
        val clock = FakeClock()
        val repo = StorageFavoritesRepository(storage, events, clock::now)

        repo.setTrackFavorite(10L, true)
        repo.setGameFavorite(20L, true)
        repo.setArtistFavorite(30L, true)

        val reloaded = StorageFavoritesRepository(storage, events, clock::now)
        assertEquals(listOf(10L), reloaded.favoriteTrackIds().first())
        assertEquals(listOf(20L), reloaded.favoriteGameIds().first())
        assertEquals(listOf(30L), reloaded.favoriteArtistIds().first())
        assertEquals(true, reloaded.isTrackFavorite(10L).first())
        assertEquals(false, reloaded.isTrackFavorite(11L).first())
    }

    @Test
    fun `favorites are newest first and re-favoriting refreshes the timestamp`() = runTest {
        val storage = FakeStorage()
        val clock = FakeClock()
        val repo = StorageFavoritesRepository(storage, BrowserStateEvents(), clock::now)

        repo.setTrackFavorite(10L, true) // t=1
        repo.setTrackFavorite(20L, true) // t=2
        repo.setTrackFavorite(30L, true) // t=3
        assertEquals(listOf(30L, 20L, 10L), repo.favoriteTrackIds().first())

        repo.setTrackFavorite(10L, true) // refreshes at t=4
        assertEquals(listOf(10L, 30L, 20L), repo.favoriteTrackIds().first())
    }

    @Test
    fun `un-favoriting removes only the requested id`() = runTest {
        val storage = FakeStorage()
        val clock = FakeClock()
        val repo = StorageFavoritesRepository(storage, BrowserStateEvents(), clock::now)

        repo.setTrackFavorite(1L, true)
        repo.setTrackFavorite(2L, true)
        repo.setTrackFavorite(1L, false)

        assertEquals(listOf(2L), repo.favoriteTrackIds().first())
        assertEquals(false, repo.isTrackFavorite(1L).first())
    }

    @Test
    fun `clearFavorites wipes all three kinds`() = runTest {
        val repo = StorageFavoritesRepository(FakeStorage(), BrowserStateEvents(), FakeClock()::now)
        repo.setTrackFavorite(1L, true)
        repo.setGameFavorite(2L, true)
        repo.setArtistFavorite(3L, true)

        repo.clearFavorites()

        assertEquals(emptyList(), repo.favoriteTrackIds().first())
        assertEquals(emptyList(), repo.favoriteGameIds().first())
        assertEquals(emptyList(), repo.favoriteArtistIds().first())
    }

    @Test
    fun `another tab's write reaches a live favorites collector`() = runTest {
        val storage = FakeStorage()
        val events = BrowserStateEvents()
        val repo = StorageFavoritesRepository(storage, events, FakeClock()::now)
        val seen = mutableListOf<List<Long>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.favoriteTrackIds().collect { seen += it }
        }

        val external = BrowserStateCodecs.favorites.encode(
            FavoritesState(tracks = listOf(FavoriteEntry(id = 77L, favoritedAtMs = 5L))),
        )
        storage.writeFromOtherTab(BrowserStateKeys.FAVORITES, external)

        assertEquals(listOf(emptyList(), listOf(77L)), seen)
    }

    @Test
    fun `corrupt data falls back to empty and reports one non-fatal message`() = runTest {
        val storage = FakeStorage(mapOf(BrowserStateKeys.FAVORITES to "{corrupt"))
        val events = BrowserStateEvents()
        val seen = collectEvents(events)
        val repo = StorageFavoritesRepository(storage, events, FakeClock()::now)

        assertEquals(emptyList(), repo.favoriteTrackIds().first())
        assertEquals(emptyList(), repo.favoriteGameIds().first())

        val corrupt = seen.filter { it.reason == BrowserStateReason.CORRUPT }
        assertEquals(1, corrupt.size, "two collectors must not spam duplicate reports: $seen")
        assertTrue(corrupt.single().message.contains("favorites"), corrupt.single().message)
    }

    @Test
    fun `a newer schema version falls back safely and reports a message`() = runTest {
        val newer = """{"schemaVersion":99,"payload":{"tracks":[]}}"""
        val storage = FakeStorage(mapOf(BrowserStateKeys.FAVORITES to newer))
        val events = BrowserStateEvents()
        val seen = collectEvents(events)

        val repo = StorageFavoritesRepository(storage, events, FakeClock()::now)

        assertEquals(emptyList(), repo.favoriteTrackIds().first())
        assertTrue(seen.any { it.reason == BrowserStateReason.UNSUPPORTED_VERSION })
    }

    @Test
    fun `an older schema version is upgraded when its payload still reads`() = runTest {
        val older = """{"schemaVersion":0,"payload":{"tracks":[{"id":4,"favoritedAtMs":9}]}}"""
        val storage = FakeStorage(mapOf(BrowserStateKeys.FAVORITES to older))
        val events = BrowserStateEvents()
        val seen = collectEvents(events)

        val repo = StorageFavoritesRepository(storage, events, FakeClock()::now)

        assertEquals(listOf(4L), repo.favoriteTrackIds().first())
        assertTrue(seen.any { it.reason == BrowserStateReason.MIGRATED })
    }

    @Test
    fun `a quota failure is caught, surfaced, and does not throw`() = runTest {
        val storage = FakeStorage()
        val events = BrowserStateEvents()
        val seen = collectEvents(events)
        val repo = StorageFavoritesRepository(storage, events, FakeClock()::now)
        storage.failWrites = true

        repo.setTrackFavorite(5L, true)

        assertTrue(seen.any { it.reason == BrowserStateReason.WRITE_FAILED }, "$seen")
        assertEquals(emptyList(), repo.favoriteTrackIds().first())
    }

    @Test
    fun `unknown ids are stored verbatim for the caller to resolve`() = runTest {
        val repo = StorageFavoritesRepository(FakeStorage(), BrowserStateEvents(), FakeClock()::now)
        repo.setTrackFavorite(404L, true)
        repo.setTrackFavorite(7L, true)

        assertEquals(setOf(7L, 404L), repo.favoriteTrackIds().first().toSet())
    }

    private fun TestScope.collectEvents(events: BrowserStateEvents): MutableList<BrowserStateEvent> {
        val seen = mutableListOf<BrowserStateEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            events.events.collect { seen += it }
        }
        return seen
    }
}
