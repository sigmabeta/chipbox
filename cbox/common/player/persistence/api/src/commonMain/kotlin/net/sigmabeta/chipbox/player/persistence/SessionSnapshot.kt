package net.sigmabeta.chipbox.player.persistence

import kotlinx.serialization.Serializable
import net.sigmabeta.chipbox.player.common.RepeatMode
import net.sigmabeta.chipbox.player.common.SessionType

/**
 * Persisted "what was playing, and where" — the minimal tuple needed to rebuild a
 * [net.sigmabeta.chipbox.player.common.Session] and resume it on the next launch. Serialized to
 * JSON and stored in DataStore by [PlaybackSessionStore].
 *
 * Only the fields a session is *resolved from* are stored ([type] + [contentId], plus the ad-hoc
 * [explicitSetlist]/[sourceName] for `SETLIST` sessions); the director re-resolves the actual
 * setlist on restore. The active track is captured as a concrete [currentTrackId] rather than a
 * setlist index, because a shuffled session's order isn't reproducible — re-resolving would
 * reshuffle into a different sequence, so a saved index would point at the wrong track. On restore
 * the id is handed back as the session's `startingTrackId`, which the director prioritises and
 * locates in whatever order it resolves.
 *
 * @property positionMs Playback offset within [currentTrackId] to seek to on resume.
 */
@Serializable
data class SessionSnapshot(
    val type: SessionType,
    val contentId: Long,
    val explicitSetlist: List<Long>? = null,
    val sourceName: String? = null,
    val currentTrackId: Long? = null,
    val shuffled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val positionMs: Long = 0L,
)
