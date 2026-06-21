package net.sigmabeta.chipbox.playlists.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import net.sigmabeta.chipbox.models.Playlist
import net.sigmabeta.chipbox.playlists.PlaylistsRepository

/**
 * In-memory [PlaylistsRepository] fake. Holds every playlist (metadata + ordered track ids) in a
 * single [MutableStateFlow], so all the read streams emit reactively — enough to drive the real
 * Compose UI over fakes. Tests can seed playlists up front via [seed] and inspect state directly.
 */
class FakePlaylistsRepository : PlaylistsRepository {
    private data class Entry(
        val id: Long,
        val name: String,
        val createdAtMs: Long,
        val trackIds: List<Long>,
    )

    private val entries = MutableStateFlow<List<Entry>>(emptyList())

    // Monotonic counters keep ids and createdAt deterministic without a real clock.
    private var nextId = 1L
    private var clock = 1L

    /** Seed a playlist with the given member track ids; returns its id. Later seeds sort newest-first. */
    fun seed(name: String, trackIds: List<Long> = emptyList()): Long {
        val id = nextId++
        entries.update { it + Entry(id, name, clock++, trackIds) }
        return id
    }

    override fun playlists(): Flow<List<Playlist>> =
        entries.map { list -> list.sortedByDescending { it.createdAtMs }.map { it.toPlaylist() } }

    override fun playlist(id: Long): Flow<Playlist?> =
        entries.map { list -> list.firstOrNull { it.id == id }?.toPlaylist() }

    override fun trackIds(playlistId: Long): Flow<List<Long>> =
        entries.map { list -> list.firstOrNull { it.id == playlistId }?.trackIds ?: emptyList() }

    override suspend fun createPlaylist(name: String): Long = seed(name)

    override suspend fun renamePlaylist(id: Long, name: String) =
        entries.update { list -> list.map { if (it.id == id) it.copy(name = name) else it } }

    override suspend fun deletePlaylist(id: Long) =
        entries.update { list -> list.filterNot { it.id == id } }

    override suspend fun addTracks(playlistId: Long, trackIds: List<Long>) =
        entries.update { list ->
            list.map { entry ->
                if (entry.id == playlistId) {
                    entry.copy(trackIds = entry.trackIds + trackIds.filterNot { it in entry.trackIds })
                } else {
                    entry
                }
            }
        }

    override suspend fun setTrackOrder(playlistId: Long, orderedTrackIds: List<Long>) =
        entries.update { list ->
            list.map { if (it.id == playlistId) it.copy(trackIds = orderedTrackIds) else it }
        }

    override suspend fun clearAll() {
        entries.value = emptyList()
    }

    private fun Entry.toPlaylist() = Playlist(
        id = id,
        name = name,
        trackCount = trackIds.size,
        createdAtMs = createdAtMs,
    )
}
