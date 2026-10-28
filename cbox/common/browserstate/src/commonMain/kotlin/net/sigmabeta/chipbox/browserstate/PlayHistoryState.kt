package net.sigmabeta.chipbox.browserstate

import kotlinx.serialization.Serializable

/**
 * One qualifying play in the browser play log. Each entry denormalises the facts the Home
 * recently-/most-played counters need — the track, its game, and its credited artists — so the
 * counts are folds over the log with no separate counter tables (the Room schema's three counter
 * tables would be redundant here). Field names match the original web-only `WebPlay` shape so the
 * unversioned array at `chipbox.playback_history` is readable as schema version 0.
 */
@Serializable
data class PlayHistoryEntry(
    val trackId: Long,
    val gameId: Long,
    val artistIds: List<Long> = emptyList(),
    val timeMs: Long,
)

/**
 * The play-history payload: qualifying plays in chronological order.
 *
 * The log is bounded to [MAX_ENTRIES] plays. A play is only recorded after the shared recorder's
 * threshold (10s listened or played to the end), so 500 entries is a generous history in a small
 * amount of storage; trimming happens on write and keeps the newest entries by timestamp, which
 * also repairs an out-of-order or oversized blob read from older versions.
 */
@Serializable
data class PlayHistoryState(
    val plays: List<PlayHistoryEntry> = emptyList(),
) {
    /** Append one play and enforce the bound. */
    fun withPlay(entry: PlayHistoryEntry, maxEntries: Int = MAX_ENTRIES): PlayHistoryState =
        copy(plays = plays + entry).bounded(maxEntries)

    /**
     * Keep the newest [maxEntries] plays by [PlayHistoryEntry.timeMs]. The log is returned in
     * chronological order; a stable sort preserves insertion order for equal timestamps.
     */
    fun bounded(maxEntries: Int = MAX_ENTRIES): PlayHistoryState {
        if (plays.size <= maxEntries) return this
        return copy(plays = plays.sortedBy { it.timeMs }.takeLast(maxEntries))
    }

    companion object {
        /** The documented history bound: the newest 500 qualifying plays are retained. */
        const val MAX_ENTRIES: Int = 500
    }
}
