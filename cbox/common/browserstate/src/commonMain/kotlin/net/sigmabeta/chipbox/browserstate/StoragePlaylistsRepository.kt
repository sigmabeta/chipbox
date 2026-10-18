package net.sigmabeta.chipbox.browserstate

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.sigmabeta.chipbox.models.Playlist
import net.sigmabeta.chipbox.playlists.PlaylistsRepository
import net.sigmabeta.sage.storage.common.Storage

/**
 * [PlaylistsRepository] over browser `localStorage` (through [VersionedStorageStore]).
 *
 * Metadata ids are persisted and monotonic ([PlaylistsState.nextId]) so rename/delete/reorder keep
 * stable references across reloads; [trackIds] is the authoritative user order. Reads are derived
 * from the storage flow, so another tab's changes arrive as a normal flow emission. Writes are
 * non-throwing: quota failures become a [BrowserStateEvent].
 */
@OptIn(ExperimentalTime::class)
class StoragePlaylistsRepository(
    private val store: VersionedStorageStore<PlaylistsState>,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : PlaylistsRepository {

    constructor(
        storage: Storage,
        events: BrowserStateEvents,
        now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    ) : this(
        storage.versionedStore(
            key = BrowserStateKeys.PLAYLISTS,
            codec = BrowserStateCodecs.playlists,
            emptyValue = PlaylistsState(),
            events = events,
        ),
        now,
    )

    override fun playlists(): Flow<List<Playlist>> = store.values().map { state ->
        state.normalized().newestFirst().map { it.toPlaylist() }
    }

    override fun playlist(id: Long): Flow<Playlist?> = store.values().map { state ->
        state.normalized().playlists.firstOrNull { it.id == id }?.toPlaylist()
    }

    override fun trackIds(playlistId: Long): Flow<List<Long>> = store.values().map { state ->
        state.normalized().playlists.firstOrNull { it.id == playlistId }?.trackIds ?: emptyList()
    }

    override suspend fun createPlaylist(name: String): Long? {
        var createdId = 0L
        val written = store.update { state ->
            val normalized = state.normalized()
            createdId = normalized.nextId
            normalized.copy(
                nextId = normalized.nextId + 1L,
                playlists = normalized.playlists + PlaylistEntry(createdId, name, now()),
            )
        }
        // `update` returns null when the write was refused (quota/full storage). Never report an id
        // for a playlist that is not in storage: the caller would navigate to a phantom playlist.
        return createdId.takeIf { written != null }
    }

    override suspend fun renamePlaylist(id: Long, name: String) {
        store.update { state ->
            state.updatePlaylist(id) { it.copy(name = name) }
        }
    }

    override suspend fun deletePlaylist(id: Long) {
        store.update { state ->
            val normalized = state.normalized()
            normalized.copy(playlists = normalized.playlists.filterNot { it.id == id })
        }
    }

    override suspend fun addTracks(playlistId: Long, trackIds: List<Long>) {
        if (trackIds.isEmpty()) return
        store.update { state ->
            state.updatePlaylist(playlistId) { playlist ->
                val existing = playlist.trackIds.toSet()
                val appended = trackIds.filterNot { it in existing }.distinct()
                playlist.copy(trackIds = playlist.trackIds + appended)
            }
        }
    }

    override suspend fun removeTrack(playlistId: Long, trackId: Long) {
        store.update { state ->
            state.updatePlaylist(playlistId) { it.copy(trackIds = it.trackIds.filterNot { id -> id == trackId }) }
        }
    }

    override suspend fun setTrackOrder(playlistId: Long, orderedTrackIds: List<Long>) {
        store.update { state ->
            state.updatePlaylist(playlistId) { it.copy(trackIds = orderedTrackIds.distinct()) }
        }
    }

    override suspend fun clearAll() {
        // Keep nextId: ids stay monotonic even after "clear all", so no stale deep link can alias a
        // new playlist.
        store.update { state -> state.normalized().copy(playlists = emptyList()) }
    }

    private fun PlaylistsState.updatePlaylist(
        id: Long,
        transform: (PlaylistEntry) -> PlaylistEntry,
    ): PlaylistsState {
        val normalized = normalized()
        if (normalized.playlists.none { it.id == id }) return normalized
        return normalized.copy(
            playlists = normalized.playlists.map { if (it.id == id) transform(it) else it },
        )
    }

    private fun PlaylistEntry.toPlaylist() = Playlist(
        id = id,
        name = name,
        trackCount = trackIds.size,
        createdAtMs = createdAtMs,
    )
}
