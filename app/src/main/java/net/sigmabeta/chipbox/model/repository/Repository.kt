package net.sigmabeta.chipbox.model.repository

import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Platform
import net.sigmabeta.chipbox.model.domain.Track
import rx.Observable

interface Repository {
    fun reopen()
    fun close()

    /**
     * Create
     */

    fun addTrack(track: Track): Observable<Game>

    fun addGame(platformName: String, title: String?): Observable<Game>
    fun addArtist(name: String?): Observable<Artist>

    /**
     * Read
     */

    fun getTrackSync(id: String): Track?
    fun getTracks(): Observable<out List<Track>>
    fun getTracksManaged(): List<Track>
    fun getTracksFromIds(trackIdsList: MutableList<String?>): Observable<out List<Track>>
    fun getTrackFromPath(path: String): Track?
    fun getTrackFromPath(path: String, trackNumber: Int): Track?
    fun getTrack(title: String, gameTitle: String, platformName: String): Track?

    fun getGame(id: String): Observable<Game>
    fun getGameSync(id: String): Game?
    fun getGames(): Observable<out List<Game>>
    fun getGamesManaged(): List<Game>
    fun getGamesForPlatform(platformName: String): Observable<out List<Game>>
    fun getGame(platformName: String?, title: String?): Observable<Game>

    fun getArtist(id: String): Observable<Artist>
    fun getArtistByName(name: String?): Observable<Artist>
    fun getArtists(): Observable<out List<Artist>>
    fun getArtistsManaged(): List<Artist>

    fun getPlatform(name: String?): Observable<Platform>
    fun getPlatforms(): Observable<out List<Platform>>

    /**
     * Update
     */

    fun updateTrack(oldTrack: Track, newTrack: Track): Boolean
    fun updateGameArt(game: Game, artLocal: String)

    /**
     * Delete
     */

    fun clearAll()

    fun deleteTrack(track: Track)
    fun deleteGame(game: Game)
    fun deleteArtist(artist: Artist)
}