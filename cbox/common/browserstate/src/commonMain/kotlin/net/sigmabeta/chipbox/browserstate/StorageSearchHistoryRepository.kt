package net.sigmabeta.chipbox.browserstate

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.sigmabeta.chipbox.models.SearchHistory
import net.sigmabeta.sage.storage.common.Storage

/**
 * Recent searches over browser `localStorage` (through [VersionedStorageStore]).
 *
 * Search history is user state, so it never leaves the browser: the web app no longer POSTs it to
 * chipbox-server. The list is newest-first, de-duplicated by query, capped at
 * [SearchHistoryState.MAX_ENTRIES], and cross-tab-live because reads derive from the storage flow.
 * Writes are non-throwing; a quota failure surfaces as a [BrowserStateEvent].
 */
class StorageSearchHistoryRepository(
    private val store: VersionedStorageStore<SearchHistoryState>,
) {
    constructor(
        storage: Storage,
        events: BrowserStateEvents,
    ) : this(
        storage.versionedStore(
            key = BrowserStateKeys.SEARCH_HISTORY,
            codec = BrowserStateCodecs.searchHistory,
            emptyValue = SearchHistoryState(),
            events = events,
        ),
    )

    fun recent(): Flow<List<SearchHistory>> = store.values().map { it.normalized().entries }

    suspend fun add(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        store.update { state ->
            val normalized = state.normalized()
            val existing = normalized.entries.firstOrNull { it.query == trimmed }
            val rest = normalized.entries.filterNot { it.query == trimmed }
            normalized.copy(
                nextId = if (existing == null) normalized.nextId + 1L else normalized.nextId,
                // Re-searching a term moves it back to the top rather than adding a duplicate.
                entries = (listOf(existing ?: SearchHistory(normalized.nextId, trimmed)) + rest)
                    .take(SearchHistoryState.MAX_ENTRIES),
            )
        }
    }

    suspend fun remove(id: Long) {
        store.update { state ->
            val normalized = state.normalized()
            normalized.copy(entries = normalized.entries.filterNot { it.id == id })
        }
    }

    suspend fun clear() {
        store.update { state -> state.normalized().copy(entries = emptyList()) }
    }
}
