package net.sigmabeta.chipbox.playlists

import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.models.Playlist

/**
 * Facade over the playlists database. Creates, renames, and deletes user playlists, manages their
 * track membership and ordering, and exposes the streams the playlists list and detail screens
 * observe.
 *
 * Track reads return bare library ids ([trackIds]) — callers hydrate them into full [Track] models
 * via the library `Repository` (the playlists database holds no model data, only ids, since the
 * library is a separately-rebuilt cache). [playlists]/[playlist] return [Playlist] metadata with a
 * denormalized track count.
 */
interface PlaylistsRepository {
    /** Every playlist with its track count, newest playlist first. */
    fun playlists(): Flow<List<Playlist>>

    /** A single playlist's metadata, or null once it has been deleted. */
    fun playlist(id: Long): Flow<Playlist?>

    /** The playlist's member track ids, in user order. */
    fun trackIds(playlistId: Long): Flow<List<Long>>

    /** Create an empty playlist named [name]; returns its new id. */
    suspend fun createPlaylist(name: String): Long

    suspend fun renamePlaylist(id: Long, name: String)

    /** Delete the playlist and (via cascade) all its memberships. */
    suspend fun deletePlaylist(id: Long)

    /** Append [trackIds] to the end of the playlist, skipping any already present. */
    suspend fun addTracks(playlistId: Long, trackIds: List<Long>)

    /** Replace the playlist's membership with exactly [orderedTrackIds], in that order (reorder + remove). */
    suspend fun setTrackOrder(playlistId: Long, orderedTrackIds: List<Long>)

    /** Wipe every playlist. */
    suspend fun clearAll()
}
