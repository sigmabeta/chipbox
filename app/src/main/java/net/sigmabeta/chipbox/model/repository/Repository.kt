package net.sigmabeta.chipbox.model.repository

import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import rx.Observable

interface Repository {
    fun reopen()
    fun close()

    /**
     * Create
     */

    fun addTrack(track: Track): Observable<Game>

    fun addGame(platformId: Long, title: String?): Observable<Game>
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
    fun getTrack(title: String, gameTitle: String, platform: Long): Track?

    fun getGame(id: String): Observable<Game>
    fun getGameSync(id: String): Game?
    fun getGames(): Observable<out List<Game>>
    fun getGamesManaged(): List<Game>
    fun getGamesForPlatform(platformId: Long): Observable<out List<Game>>
    fun getGame(platformId: Long, title: String?): Observable<Game>

    fun getArtist(id: String): Observable<Artist>
    fun getArtistByName(name: String?): Observable<Artist>
    fun getArtists(): Observable<out List<Artist>>
    fun getArtistsManaged(): List<Artist>

    /**
     * Update
     */

    fun updateTrack(oldTrack: Track, newTrack: Track)
    fun updateGameArt(game: Game, artLocal: String)

    /**
     * Delete
     */

    fun clearAll()
}