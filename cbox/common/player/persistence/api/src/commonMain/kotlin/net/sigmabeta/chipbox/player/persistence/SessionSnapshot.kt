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
 * @property resolvedSetlist The exact play order (track ids) at save time, captured from the
 *           director's live setlist. When present the director replays it verbatim on restore
 *           instead of re-resolving from [contentId] — so a shuffled or user-edited order resumes
 *           faithfully. Null in legacy snapshots, which fall back to re-resolution.
 * @property modified Whether the user had reordered/removed tracks in this session (mirrors
 *           `Session.modified`); drives the "(Modified)" label after a resume.
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
    val resolvedSetlist: List<Long>? = null,
    val modified: Boolean = false,
)
