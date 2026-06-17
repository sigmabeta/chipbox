package net.sigmabeta.chipbox.history

/**
 * A distinct recently-played track: the [trackId] and the time of its most recent play. Ordered
 * newest-first by the queries that produce it. Projection type for the `song_play` table.
 */
data class RecentPlay(
    val trackId: Long,
    val timeMs: Long,
)

/**
 * A play-count row for a song, game, or artist. [id] is the relevant library id (trackId / gameId /
 * artistId depending on which list it came from). Projection type shared by the three counter
 * tables — the queries alias each table's primary key to `id`.
 */
data class PlayCount(
    val id: Long,
    val playCount: Int,
    val lastPlayedMs: Long,
)
