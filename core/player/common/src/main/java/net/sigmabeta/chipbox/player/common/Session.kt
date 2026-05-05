package net.sigmabeta.chipbox.player.common

import kotlin.random.Random

/**
 * Description of "what to play and where to start" passed to
 * [net.sigmabeta.chipbox.player.director.Director.start]. The director resolves the actual
 * setlist (an ordered list of track ids) from [type] + [contentId].
 *
 * Three optional starting hints are tried in order: explicit [startingTrackId] first, then
 * [currentPosition] (used when restoring an existing session), then [startingPosition].
 *
 * @property type What kind of content [contentId] refers to (a game's tracks vs. an artist's).
 * @property contentId Foreign-key id of the game/artist whose tracks make up the setlist.
 * @property startingTrackId Specific track to start with; takes precedence over the position
 *           hints when set.
 * @property startingPosition Index into the resolved setlist to start at. Used when no
 *           specific track id is given.
 * @property currentPosition Index of the currently-playing track within the setlist. Updated
 *           by the director as playback advances; supplied externally only when restoring.
 * @property id Random session identifier; lets observers tell two unrelated sessions apart.
 */
data class Session(
    val type: SessionType,
    val contentId: Long,
    val startingTrackId: Long? = null,
    val startingPosition: Int? = null,
    val currentPosition: Int? = null,
    val id: Long = Random.nextLong()
)
