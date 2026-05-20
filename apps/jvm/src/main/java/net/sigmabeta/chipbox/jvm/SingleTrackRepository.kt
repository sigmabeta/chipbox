package net.sigmabeta.chipbox.jvm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.SearchHistory
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.RawGame
import net.sigmabeta.chipbox.repository.Repository

/**
 * The headless harness plays exactly one track, so the [Repository] contract collapses to
 * "return this [Track] by id." Everything the player pipeline doesn't touch
 * (artists/games/search/history) is stubbed empty rather than dragging in `MemoryRepository`,
 * which discards `RawTrack.source`/`extension` — the two fields the real emulator path needs.
 */
class SingleTrackRepository(private val track: Track) : Repository {

    override suspend fun getTrack(id: Long, withGame: Boolean, withArtists: Boolean): Track? =
        track.takeIf { it.id == id }

    override fun getAllTracks(withGame: Boolean, withArtists: Boolean): Flow<Data<List<Track>>> =
        flowOf(Data.Succeeded(listOf(track)))

    override fun getAllArtists(withTracks: Boolean, withGames: Boolean): Flow<Data<List<Artist>>> =
        flowOf(Data.Empty)

    override fun getAllGames(withTracks: Boolean, withArtists: Boolean): Flow<Data<List<Game>>> =
        flowOf(Data.Empty)

    override suspend fun getTracksForGame(id: Long, withGame: Boolean, withArtists: Boolean): List<Track> =
        emptyList()

    override suspend fun getTracksForArtist(id: Long, withGame: Boolean, withArtists: Boolean): List<Track> =
        emptyList()

    override suspend fun getTracksForPlatform(
        platform: Platform,
        withGame: Boolean,
        withArtists: Boolean
    ): List<Track> = emptyList()

    override fun getGamesForPlatform(platform: Platform): Flow<Data<List<Game>>> =
        flowOf(Data.Empty)

    override fun getAvailablePlatforms(): Flow<Data<List<Platform>>> = flowOf(Data.Empty)

    override fun getGame(id: Long, withTracks: Boolean, withArtists: Boolean): Flow<Data<Game?>> =
        flowOf(Data.Empty)

    override fun getArtist(id: Long, withTracks: Boolean, withGames: Boolean): Flow<Data<Artist?>> =
        flowOf(Data.Empty)

    override suspend fun addGame(rawGame: RawGame) = Unit

    override suspend fun clearLibrary() = Unit

    override fun searchGames(query: String): Flow<Data<List<Game>>> = flowOf(Data.Empty)

    override fun searchSongs(query: String): Flow<Data<List<Track>>> = flowOf(Data.Empty)

    override fun searchArtists(query: String): Flow<Data<List<Artist>>> = flowOf(Data.Empty)

    override fun getSearchHistory(): Flow<Data<List<SearchHistory>>> = flowOf(Data.Empty)

    override suspend fun addSearchHistory(query: String) = Unit

    override suspend fun removeSearchHistory(id: Long) = Unit
}
