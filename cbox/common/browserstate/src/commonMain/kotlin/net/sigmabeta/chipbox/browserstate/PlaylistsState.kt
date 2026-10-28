package net.sigmabeta.chipbox.browserstate

import kotlinx.serialization.Serializable

/** One playlist's metadata plus its ordered track ids. */
@Serializable
data class PlaylistEntry(
    val id: Long,
    val name: String,
    val createdAtMs: Long,
    val trackIds: List<Long> = emptyList(),
)

/**
 * The playlists payload. [nextId] is persisted so ids stay stable and monotonic across reloads and
 * clears (a deleted playlist's id is never reused). [playlists] order is not authoritative — the
 * list screen sorts newest-first by [PlaylistEntry.createdAtMs] — but membership order is.
 */
@Serializable
data class PlaylistsState(
    val nextId: Long = 1L,
    val playlists: List<PlaylistEntry> = emptyList(),
) {
    /** Enforce the invariants a corrupt/older blob might violate: unique ids and unique members. */
    fun normalized(): PlaylistsState {
        val clean = playlists
            .distinctBy { it.id }
            .map { it.copy(trackIds = it.trackIds.distinct()) }
        val nextAfterExisting = (clean.maxOfOrNull { it.id } ?: 0L) + 1L
        return copy(
            nextId = maxOf(nextId, nextAfterExisting, 1L),
            playlists = clean,
        )
    }

    /** Newest playlist first; id breaks timestamp ties deterministically. */
    fun newestFirst(): List<PlaylistEntry> = playlists.sortedWith(
        compareByDescending<PlaylistEntry> { it.createdAtMs }.thenByDescending { it.id },
    )
}
