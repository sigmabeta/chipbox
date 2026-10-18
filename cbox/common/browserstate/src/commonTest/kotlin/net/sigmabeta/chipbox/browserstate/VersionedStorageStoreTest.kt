package net.sigmabeta.chipbox.browserstate

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.sigmabeta.sage.storage.common.Storage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The mechanics every browser store shares: a read-modify-write that is serialized in one tab, a
 * corrupt blob that an explicit clear can erase, and cancellation that is not mistaken for storage
 * corruption.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VersionedStorageStoreTest {

    private fun Storage.favoritesStore(events: BrowserStateEvents = BrowserStateEvents()) =
        versionedStore(
            key = BrowserStateKeys.FAVORITES,
            codec = BrowserStateCodecs.favorites,
            emptyValue = FavoritesState(),
            events = events,
        )

    @Test
    fun `two concurrent updates in one tab both land`() = runTest {
        val storage = FakeStorage()
        storage.interleaveReads = true
        val store = storage.favoritesStore()

        val first = launch { store.update { it.copy(tracks = it.tracks + FavoriteEntry(id = 1L, favoritedAtMs = 1L)) } }
        val second = launch { store.update { it.copy(tracks = it.tracks + FavoriteEntry(id = 2L, favoritedAtMs = 2L)) } }
        first.join()
        second.join()

        assertEquals(
            setOf(1L, 2L),
            store.current().tracks.map { it.id }.toSet(),
            "serialized read-modify-write must not lose the first mutation",
        )
    }

    @Test
    fun `a cancellation thrown by a read propagates instead of reporting a read failure`() = runTest {
        val storage = FakeStorage()
        val events = BrowserStateEvents()
        val seen = collectEvents(events)
        val cancellation = CancellationException("collector cancelled")
        storage.readFailure = cancellation

        val thrown = assertFailsWith<CancellationException> { storage.favoritesStore(events).current() }

        assertEquals(cancellation, thrown)
        assertTrue(seen.none { it.reason == BrowserStateReason.READ_FAILED }, "$seen")
    }

    @Test
    fun `cancelling a values collector does not report a read failure`() = runTest {
        val storage = FakeStorage()
        val events = BrowserStateEvents()
        val seen = collectEvents(events)
        val store = storage.favoritesStore(events)
        val values = mutableListOf<FavoritesState>()
        val collector: Job = launch { store.values().collect { values += it } }
        runCurrent()

        collector.cancel()
        collector.join()

        assertEquals(1, values.size, "the initial emission is delivered before cancellation")
        assertTrue(seen.none { it.reason == BrowserStateReason.READ_FAILED }, "$seen")
    }

    @Test
    fun `clearing a corrupt blob rewrites it as the encoded empty value`() = runTest {
        val storage = FakeStorage(mapOf(BrowserStateKeys.FAVORITES to "{corrupt"))
        val events = BrowserStateEvents()
        val store = storage.favoritesStore(events)
        assertEquals(emptyList(), store.current().tracks, "corrupt data reads as empty")

        store.update { FavoritesState() }

        assertTrue(
            BrowserStateCodecs.favorites.decode(storage.raw(BrowserStateKeys.FAVORITES)) is DecodeOutcome.Success,
            "the corrupt string must be replaced, not left in place: ${storage.raw(BrowserStateKeys.FAVORITES)}",
        )
        assertEquals(emptyList(), storage.favoritesStore(events).values().first().tracks)
    }

    @Test
    fun `a clean empty value is not rewritten by a no-op update`() = runTest {
        val storage = FakeStorage()
        val store = storage.favoritesStore()
        assertTrue(store.update { FavoritesState() } == FavoritesState())
        assertEquals(null, storage.raw(BrowserStateKeys.FAVORITES), "a first-run no-op must not create a key")
    }

    private fun TestScope.collectEvents(events: BrowserStateEvents): MutableList<BrowserStateEvent> {
        val seen = mutableListOf<BrowserStateEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            events.events.collect { seen += it }
        }
        return seen
    }
}
