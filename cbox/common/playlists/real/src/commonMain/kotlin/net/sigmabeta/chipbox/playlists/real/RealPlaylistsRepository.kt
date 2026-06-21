package net.sigmabeta.chipbox.playlists.real

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.sigmabeta.chipbox.entities.PlaylistEntity
import net.sigmabeta.chipbox.entities.PlaylistTrackEntity
import net.sigmabeta.chipbox.models.Playlist
import net.sigmabeta.chipbox.playlists.PlaylistsRepository
import net.sigmabeta.chipbox.playlists.dao.PlaylistDao
import net.sigmabeta.chipbox.playlists.dao.PlaylistTrackDao
import net.sigmabeta.chipbox.playlists.dao.PlaylistWithCount
import net.sigmabeta.sage.logging.Hatchet

/**
 * Production [PlaylistsRepository]. Metadata reads project through [PlaylistWithCount] (a playlist
 * plus its denormalized track count); membership reads return bare ids the detail screen hydrates via
 * the library `Repository`. Room dispatches the suspend DAO calls off the caller thread, so no
 * explicit dispatcher hop is needed here.
 */
@OptIn(ExperimentalTime::class)
class RealPlaylistsRepository(
    private val playlistDao: PlaylistDao,
    private val playlistTrackDao: PlaylistTrackDao,
    private val hatchet: Hatchet,
) : PlaylistsRepository {

    override fun playlists(): Flow<List<Playlist>> =
        playlistDao.getAllWithCounts().map { rows -> rows.map { it.toPlaylist() } }

    override fun playlist(id: Long): Flow<Playlist?> =
        playlistDao.getByIdWithCount(id).map { it?.toPlaylist() }

    override fun trackIds(playlistId: Long): Flow<List<Long>> = playlistTrackDao.trackIds(playlistId)

    override suspend fun createPlaylist(name: String): Long {
        val id = playlistDao.insert(PlaylistEntity(name = name, createdAtMs = now()))
        hatchet.d("Created playlist $id \"$name\"")
        return id
    }

    override suspend fun renamePlaylist(id: Long, name: String) {
        playlistDao.rename(id, name)
        hatchet.d("Renamed playlist $id to \"$name\"")
    }

    override suspend fun deletePlaylist(id: Long) {
        // Memberships cascade away via the playlist_track FK.
        playlistDao.delete(id)
        hatchet.d("Deleted playlist $id")
    }

    override suspend fun addTracks(playlistId: Long, trackIds: List<Long>) {
        if (trackIds.isEmpty()) return
        // Append after the current last position; IGNORE drops ids already in the playlist.
        val start = playlistTrackDao.maxPosition(playlistId) + 1
        playlistTrackDao.insertAll(
            trackIds.mapIndexed { index, trackId ->
                PlaylistTrackEntity(playlistId, trackId, start + index)
            },
        )
        hatchet.d("Added ${trackIds.size} track(s) to playlist $playlistId")
    }

    override suspend fun setTrackOrder(playlistId: Long, orderedTrackIds: List<Long>) {
        // Rewrite the whole membership: clear, then reinsert at contiguous positions. Covers both
        // reordering and removal. Sequential writes (no explicit transaction) match the rest of the
        // codebase — Room serializes writes, and the trackIds Flow re-emits only once both complete.
        playlistTrackDao.clear(playlistId)
        playlistTrackDao.insertAll(
            orderedTrackIds.mapIndexed { index, trackId ->
                PlaylistTrackEntity(playlistId, trackId, index)
            },
        )
        hatchet.d("Set playlist $playlistId order to ${orderedTrackIds.size} track(s)")
    }

    override suspend fun clearAll() {
        playlistTrackDao.nukeTable()
        playlistDao.nukeTable()
        hatchet.i("Cleared playlists.")
    }

    private fun PlaylistWithCount.toPlaylist() = Playlist(
        id = playlist.id,
        name = playlist.name,
        trackCount = trackCount,
        createdAtMs = playlist.createdAtMs,
    )

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()
}
