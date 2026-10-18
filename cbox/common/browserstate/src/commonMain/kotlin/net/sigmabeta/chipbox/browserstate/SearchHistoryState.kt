package net.sigmabeta.chipbox.browserstate

import kotlinx.serialization.Serializable
import net.sigmabeta.chipbox.models.SearchHistory

/**
 * Payload for the browser-local recent-search list. Ids are persisted and monotonic so removing a
 * row keeps stable references; entries are newest-first and de-duplicated by query.
 */
@Serializable
data class SearchHistoryState(
    val nextId: Long = 1L,
    val entries: List<SearchHistory> = emptyList(),
) {
    fun normalized(): SearchHistoryState {
        val seen = mutableSetOf<String>()
        val deduped = entries
            .filter { entry -> entry.query.isNotBlank() && seen.add(entry.query) }
            .take(MAX_ENTRIES)
        val highestId = deduped.maxOfOrNull { it.id } ?: 0L
        return copy(nextId = maxOf(nextId, highestId + 1L), entries = deduped)
    }

    companion object {
        const val MAX_ENTRIES = 10
    }
}
