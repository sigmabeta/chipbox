package net.sigmabeta.chipbox.model.repository

import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.events.FileScanEvent
import net.sigmabeta.chipbox.model.file.Folder
import rx.Observable

interface Repository {
    fun scanLibrary(): Observable<FileScanEvent>

    /**
     * Create
     */

    fun addTrack(track: Track): Observable<String>

    fun addGame(platformId: Long, title: String?): Observable<Game>
    fun addArtist(name: String?): Observable<Artist>
    fun addFolder(path: String): Observable<Int>

    /**
     * Read
     */

    fun getTracks(): Observable<out List<Track>>

    fun getGame(id: String): Observable<Game>
    fun getGames(): Observable<out List<Game>>
    fun getGamesForPlatform(platformId: Long): Observable<out List<Game>>
    fun getGame(platformId: Long, title: String?): Observable<Game>

    fun getArtist(id: String): Observable<Artist>
    fun getArtistByName(name: String?): Observable<Artist>
    fun getArtists(): Observable<out List<Artist>>

    fun getFolders(): Observable<out List<Folder>>

    /**
     * Update
     */


    /**
     * Delete
     */

    fun clearAll()
}