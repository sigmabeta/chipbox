package net.sigmabeta.chipbox.browserstate

import kotlinx.serialization.Serializable
import net.sigmabeta.chipbox.player.persistence.SessionSnapshot

/**
 * The playback-session payload: at most one saved [SessionSnapshot] (the last-played queue and
 * position), or null when nothing is resumable. The envelope (not the payload) carries the schema
 * version; a raw snapshot from a build that predates the envelope is schema version 0.
 */
@Serializable
data class PlaybackSessionState(
    val snapshot: SessionSnapshot? = null,
)
