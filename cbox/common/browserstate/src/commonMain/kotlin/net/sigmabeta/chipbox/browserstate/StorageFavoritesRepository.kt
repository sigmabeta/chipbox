package net.sigmabeta.chipbox.browserstate

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.sigmabeta.chipbox.favorites.FavoritesRepository
import net.sigmabeta.sage.storage.common.Storage

/**
 * [FavoritesRepository] over browser `localStorage` (through [VersionedStorageStore]).
 *
 * The store is versioned, cross-tab-live (reads derive from the storage flow), and non-throwing:
 * corrupt/newer data falls back to empty, quota failures surface as a [BrowserStateEvent]. Ids are
 * persisted only; ordering is newest favorite first and re-favoriting refreshes the timestamp.
 */
@OptIn(ExperimentalTime::class)
class StorageFavoritesRepository(
    private val store: VersionedStorageStore<FavoritesState>,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : FavoritesRepository {

    constructor(
        storage: Storage,
        events: BrowserStateEvents,
        now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    ) : this(
        VersionedStorageStore(
            storage = storage,
            key = BrowserStateKeys.FAVORITES,
            codec = BrowserStateCodecs.favorites,
            emptyValue = FavoritesState(),
            events = events,
        ),
        now,
    )

    override suspend fun setTrackFavorite(trackId: Long, favorite: Boolean) =
        setFavorite(Kind.TRACKS, trackId, favorite)

    override suspend fun setGameFavorite(gameId: Long, favorite: Boolean) =
        setFavorite(Kind.GAMES, gameId, favorite)

    override suspend fun setArtistFavorite(artistId: Long, favorite: Boolean) =
        setFavorite(Kind.ARTISTS, artistId, favorite)

    override fun isTrackFavorite(trackId: Long): Flow<Boolean> = favoriteTrackIds().map { trackId in it }

    override fun isGameFavorite(gameId: Long): Flow<Boolean> = favoriteGameIds().map { gameId in it }

    override fun isArtistFavorite(artistId: Long): Flow<Boolean> = favoriteArtistIds().map { artistId in it }

    override fun favoriteTrackIds(): Flow<List<Long>> = idsOf(Kind.TRACKS)

    override fun favoriteGameIds(): Flow<List<Long>> = idsOf(Kind.GAMES)

    override fun favoriteArtistIds(): Flow<List<Long>> = idsOf(Kind.ARTISTS)

    override suspend fun clearFavorites() {
        store.update { FavoritesState() }
    }

    private fun idsOf(kind: Kind): Flow<List<Long>> = store.values().map { state ->
        val normalized = state.normalized()
        normalized.entries(kind).map { it.id }
    }

    private suspend fun setFavorite(kind: Kind, id: Long, favorite: Boolean) {
        store.update { state ->
            val normalized = state.normalized()
            val remaining = normalized.entries(kind).filterNot { it.id == id }
            val next = if (favorite) remaining + FavoriteEntry(id, now()) else remaining
            normalized.with(kind, next.normalizedEntries())
        }
    }

    private enum class Kind {
        TRACKS,
        GAMES,
        ARTISTS,
    }

    private fun FavoritesState.entries(kind: Kind): List<FavoriteEntry> = when (kind) {
        Kind.TRACKS -> tracks
        Kind.GAMES -> games
        Kind.ARTISTS -> artists
    }

    private fun FavoritesState.with(kind: Kind, entries: List<FavoriteEntry>): FavoritesState = when (kind) {
        Kind.TRACKS -> copy(tracks = entries)
        Kind.GAMES -> copy(games = entries)
        Kind.ARTISTS -> copy(artists = entries)
    }
}
