package net.sigmabeta.chipbox.browserstate

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StorageSearchHistoryRepositoryTest {

    @Test
    fun `recent searches are newest-first and persist across reloads`() = runTest {
        val storage = FakeStorage()
        val events = BrowserStateEvents()
        val repo = StorageSearchHistoryRepository(storage, events)

        repo.add("chrono")
        repo.add("mega man")

        assertEquals(listOf("mega man", "chrono"), repo.recent().first().map { it.query })
        assertEquals(
            listOf("mega man", "chrono"),
            StorageSearchHistoryRepository(storage, events).recent().first().map { it.query },
            "search history is durable browser state",
        )
    }

    @Test
    fun `re-searching a term moves it to the top without duplicating it`() = runTest {
        val repo = StorageSearchHistoryRepository(FakeStorage(), BrowserStateEvents())
        repo.add("alpha")
        repo.add("beta")
        repo.add("alpha")

        assertEquals(listOf("alpha", "beta"), repo.recent().first().map { it.query })
    }

    @Test
    fun `removing a row drops only that id`() = runTest {
        val repo = StorageSearchHistoryRepository(FakeStorage(), BrowserStateEvents())
        repo.add("alpha")
        repo.add("beta")
        val beta = repo.recent().first().first { it.query == "beta" }

        repo.remove(beta.id)

        assertEquals(listOf("alpha"), repo.recent().first().map { it.query })
    }

    @Test
    fun `the list is capped at the newest ten queries`() = runTest {
        val repo = StorageSearchHistoryRepository(FakeStorage(), BrowserStateEvents())
        repeat(SearchHistoryState.MAX_ENTRIES + 3) { repo.add("query $it") }

        val queries = repo.recent().first().map { it.query }
        assertEquals(SearchHistoryState.MAX_ENTRIES, queries.size)
        assertEquals("query 12", queries.first())
        assertTrue("query 0" !in queries, "the oldest query is evicted")
    }

    @Test
    fun `clearing corrupt search history rewrites the key so a reload is empty and usable`() = runTest {
        val storage = FakeStorage(mapOf(BrowserStateKeys.SEARCH_HISTORY to "{corrupt"))
        val events = BrowserStateEvents()
        val repo = StorageSearchHistoryRepository(storage, events)

        repo.clear()

        assertTrue(
            BrowserStateCodecs.searchHistory.decode(storage.raw(BrowserStateKeys.SEARCH_HISTORY)) is DecodeOutcome.Success,
            "the corrupt string is replaced, not left in storage: ${storage.raw(BrowserStateKeys.SEARCH_HISTORY)}",
        )
        val reloaded = StorageSearchHistoryRepository(storage, events)
        assertEquals(emptyList(), reloaded.recent().first(), "a reload stays empty")
        reloaded.add("after repair")
        assertEquals(listOf("after repair"), reloaded.recent().first().map { it.query }, "the store is usable again")
    }

    @Test
    fun `a quota failure on add is caught and leaves the list unchanged`() = runTest {
        val storage = FakeStorage()
        val events = BrowserStateEvents()
        val seen = mutableListOf<BrowserStateEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            events.events.collect { seen += it }
        }
        val repo = StorageSearchHistoryRepository(storage, events)
        repo.add("kept")
        storage.failWrites = true

        repo.add("lost")

        assertEquals(listOf("kept"), repo.recent().first().map { it.query })
        assertTrue(seen.any { it.reason == BrowserStateReason.WRITE_FAILED })
    }
}
