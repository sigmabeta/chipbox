package net.sigmabeta.chipbox.player.director.real.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.SearchHistory
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.FolderSnapshot
import net.sigmabeta.chipbox.repository.GameWriteOutcome
import net.sigmabeta.chipbox.repository.GameWriteResult
import net.sigmabeta.chipbox.repository.RawGame
import net.sigmabeta.chipbox.repository.Repository

/**
 * Minimal [Repository] for Director tests. Only [getTrack] is implemented — the Director uses it
 * to look up the [Track] for each [GeneratorEvent.Loading] / [SpeakerEvent.TrackChange] it
 * processes. Setlist resolution is bypassed by sticking to [SessionType.SETLIST] sessions, which
 * carry their setlist explicitly.
 *
 * Everything else throws [NotImplementedError]; if a test path needs a method we haven't covered,
 * the failure points straight at the missing fake instead of silently returning an empty Flow.
 */
class FakeRepository(private val tracksById: Map<Long, Track>) : Repository {

    override suspend fun getTrack(id: Long, withGame: Boolean, withArtists: Boolean): Track? =
        tracksById[id]

    override suspend fun getTracksForGame(id: Long, withGame: Boolean, withArtists: Boolean): List<Track> =
        TODO("FakeRepository.getTracksForGame not implemented — use a SETLIST session in tests.")

    override suspend fun getTracksForArtist(id: Long, withGame: Boolean, withArtists: Boolean): List<Track> =
        TODO("FakeRepository.getTracksForArtist not implemented — use a SETLIST session in tests.")

    override suspend fun getTracksForPlatform(platform: Platform, withGame: Boolean, withArtists: Boolean): List<Track> =
        TODO("FakeRepository.getTracksForPlatform not implemented — use a SETLIST session in tests.")

    override fun getAllArtists(withTracks: Boolean, withGames: Boolean): Flow<Data<List<Artist>>> = flowOf(Data.Empty)
    override fun getAllGames(withTracks: Boolean, withArtists: Boolean): Flow<Data<List<Game>>> = flowOf(Data.Empty)
    override fun getAllTracks(withGame: Boolean, withArtists: Boolean): Flow<Data<List<Track>>> = flowOf(Data.Empty)
    override fun getGamesForPlatform(platform: Platform): Flow<Data<List<Game>>> = flowOf(Data.Empty)
    override fun getAvailablePlatforms(): Flow<Data<List<Platform>>> = flowOf(Data.Empty)
    override fun getGame(id: Long, withTracks: Boolean, withArtists: Boolean): Flow<Data<Game?>> = flowOf(Data.Empty)
    override fun getArtist(id: Long, withTracks: Boolean, withGames: Boolean): Flow<Data<Artist?>> = flowOf(Data.Empty)
    override suspend fun folderSnapshots(): Map<String, FolderSnapshot> = emptyMap()
    override suspend fun upsertGame(rawGame: RawGame): GameWriteOutcome =
        GameWriteOutcome(0L, GameWriteResult.UNCHANGED)
    override suspend fun pruneGames(keptFolderKeys: Set<String>): List<String> = emptyList()
    override suspend fun clearLibrary() = Unit
    override fun searchGames(query: String): Flow<Data<List<Game>>> = flowOf(Data.Empty)
    override fun searchSongs(query: String): Flow<Data<List<Track>>> = flowOf(Data.Empty)
    override fun searchArtists(query: String): Flow<Data<List<Artist>>> = flowOf(Data.Empty)
    override fun getSearchHistory(): Flow<Data<List<SearchHistory>>> = flowOf(Data.Empty)
    override suspend fun addSearchHistory(query: String) = Unit
    override suspend fun removeSearchHistory(id: Long) = Unit
}
