package net.sigmabeta.chipbox.model.repository

import io.reactivex.Flowable
import io.reactivex.Observable
import io.realm.RealmResults
import io.realm.rx.CollectionChange
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Platform
import net.sigmabeta.chipbox.model.domain.Track

interface Repository {
    fun reopen()
    fun close()

    /**
     * Create
     */

    fun addTrack(track: Track): Flowable<Game>

    fun addGame(platformName: String, title: String?): Flowable<Game>
    fun addArtist(name: String?): Flowable<Artist>

    /**
     * Read
     */

    fun getTrackSync(id: String): Track?
    fun getTracks(): Observable<CollectionChange<RealmResults<Track>>>
    fun getTracksForArtist(artistId: String): Observable<CollectionChange<RealmResults<Track>>>
    fun getTracksManaged(): List<Track>
    fun getTracksFromIds(trackIdsList: MutableList<String?>): Observable<CollectionChange<RealmResults<Track>>>
    fun getTrackFromPath(path: String): Track?
    fun getTrackFromPath(path: String, trackNumber: Int): Track?
    fun getTrack(title: String, gameTitle: String, platformName: String): Track?

    fun getGame(id: String): Flowable<Game>
    fun getGameSync(id: String): Game?
    fun getGames(): Observable<CollectionChange<RealmResults<Game>>>
    fun getGamesManaged(): List<Game>
    fun getGamesForPlatform(platformName: String): Observable<CollectionChange<RealmResults<Game>>>
    fun getGame(platformName: String?, title: String?): Game

    fun getArtist(id: String): Flowable<Artist>
    fun getArtistByName(name: String?): Artist
    fun getArtists(): Observable<CollectionChange<RealmResults<Artist>>>
    fun getArtistsManaged(): List<Artist>

    fun getPlatform(name: String?): Platform
    fun getPlatforms(): Observable<CollectionChange<RealmResults<Platform>>>

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