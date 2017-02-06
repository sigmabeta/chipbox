package net.sigmabeta.chipbox.model.repository

import io.realm.Realm
import net.sigmabeta.chipbox.model.database.closeAndReport
import net.sigmabeta.chipbox.model.database.getRealmInstance
import net.sigmabeta.chipbox.model.database.inTransaction
import net.sigmabeta.chipbox.model.database.save
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.file.Folder
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.util.logInfo
import net.sigmabeta.chipbox.util.logVerbose
import rx.Observable
import java.util.concurrent.TimeUnit

class RealmRepository(var realm: Realm) : Repository {
    override fun reopen() {
        if (realm.isClosed) {
            realm = getRealmInstance()
        }
    }

    override fun close() {
        realm.closeAndReport()
    }

    /**
     * Create
     */

    override fun addTrack(track: Track): Observable<Game> {
        val artists = track.artistText?.split(", ")
        val gameObservable = getGame(track.platform, track.gameTitle)
                .map {
                    track.game = it
                    track.save(realm)

                    realm.inTransaction {
                        it.tracks?.add(track)
                    }

                    return@map it
                }

        val artistObservable = Observable.from(artists)
                .flatMap { name ->
                    return@flatMap getArtistByName(name)
                }

        // Combine operator happens once per Artist, once we have a Game.
        return Observable.combineLatest(gameObservable, artistObservable,
                { game: Game, artist: Artist ->
                    val gameArtist = game.artist
                    val gameHadMultipleArtists = game.multipleArtists ?: false

                    realm.inTransaction {
                        artist.tracks?.add(track)
                        track.artists?.add(artist)

                        // If this game has just one artist...
                        if (gameArtist != null && !gameHadMultipleArtists) {
                            // And the one we just got is different
                            if (artist.id != gameArtist.id) {
                                // We'll save this later.
                                game.multipleArtists = true
                            }
                        } else if (gameArtist == null) {
                            game.artist = artist
                        }
                    }

                    return@combineLatest game
                })
    }

    override fun addGame(platformId: Long, title: String?): Observable<Game> {
        return Observable.create {
            val game = Game(title ?: "Unknown Game", platformId)
            game.save(realm)

            it.onNext(game)
            it.onCompleted()
        }
    }

    override fun addArtist(name: String?): Observable<Artist> {
        return Observable.create {
            val artist = Artist(name ?: "Unknown Artist")
            artist.save(realm)

            it.onNext(artist)
            it.onCompleted()
        }
    }

    override fun addFolder(path: String): Observable<Int> {
        return Observable.create {
            if (checkIfContained(path)) {
                it.onNext(ADD_STATUS_EXISTS)
                it.onCompleted()
                return@create
            }

            removeContainedEntries(path)
            Folder(path).save(realm)

            logInfo("[Folder] Successfully added folder to database.")

            it.onNext(ADD_STATUS_GOOD)
            it.onCompleted()
        }
    }

    /**
     * Read
     */


    override fun getTracks(): Observable<out List<Track>> {
        return realm
                .where(Track::class.java)
                .findAllSortedAsync("title")
                .asObservable()
                .filter { it.isLoaded }
                .throttleFirst(5000, TimeUnit.MILLISECONDS)
    }

    override fun getTracksFromIds(trackIdsList: MutableList<String?>): Observable<out List<Track>> {
        return realm
                .where(Track::class.java)
                .`in`("id", trackIdsList.toTypedArray())
                .findAllAsync()
                .asObservable()
                .filter { it.isLoaded }
    }

    override fun getTrackSync(id: String): Track? {
        return realm.where(Track::class.java)
                .equalTo("id", id)
                .findFirst()
    }

    override fun getGame(id: String): Observable<Game> {

        return realm.where(Game::class.java)
                .equalTo("id", id)
                .findFirstAsync()
                .asObservable<Game>()
                .filter { it.isLoaded }
    }

    override fun getGameSync(id: String): Game? {

        return realm.where(Game::class.java)
                .equalTo("id", id)
                .findFirst()
    }

    override fun getGames(): Observable<out List<Game>> {

        return realm
                .where(Game::class.java)
                .findAllSortedAsync("title")
                .asObservable()
                .filter { it.isLoaded }
                .throttleFirst(5000, TimeUnit.MILLISECONDS)
    }

    override fun getGamesForPlatform(platformId: Long): Observable<out List<Game>> {
        return realm
                .where(Game::class.java)
                .equalTo("platform", platformId)
                .findAllSortedAsync("title")
                .asObservable()
                .filter { it.isLoaded }
                .throttleFirst(5000, TimeUnit.MILLISECONDS)
    }

    override fun getGame(platformId: Long, title: String?): Observable<Game> {
        var game = realm
                .where(Game::class.java)
                .equalTo("platform", platformId)
                .equalTo("title", title)
                .findFirst()

        val newGame: Game
        if (game == null || !game.isValid) {
            newGame = Game(title ?: "Unknown Game", platformId)
            logVerbose("Created game: ${newGame.title}")
            game = newGame.save(realm)
        }

        return Observable.just(game)
    }

    override fun getArtist(id: String): Observable<Artist> {

        return realm.where(Artist::class.java)
                .equalTo("id", id)
                .findFirstAsync()
                .asObservable<Artist>()
                .filter { it.isLoaded }
    }

    override fun getArtistByName(name: String?): Observable<Artist> {
        var artist = realm.where(Artist::class.java)
                .equalTo("name", name)
                .findFirst()

        val newArtist: Artist
        if (artist == null || !artist.isValid) {
            newArtist = Artist(name ?: "Unknown Game")
            logVerbose("Created artist: ${newArtist.name}")
            artist = newArtist.save(realm)
        }

        return Observable.just(artist)
    }

    override fun getArtists(): Observable<out List<Artist>> {
        return realm
                .where(Artist::class.java)
                .findAllSortedAsync("name")
                .asObservable()
                .filter { it.isLoaded }
                .throttleFirst(5000, TimeUnit.MILLISECONDS)
    }

    override fun getFoldersSync(): List<Folder> {
        return realm.where(Folder::class.java)
                .findAll()
    }

    /**
     * Update
     */

    override fun updateGameArt(game: Game, artLocal: String) {
        realm.inTransaction {
            game.artLocal = artLocal
        }
    }

    /**
     * Delete
     */
    override fun clearAll() {
        logInfo("[Library] Clearing library...")

        realm.inTransaction {
            delete(Track::class.java)
            delete(Artist::class.java)
            delete(Game::class.java)
        }
    }

    /**
     * Private Methods
     */

    private fun checkIfContained(newPath: String): Boolean {
        val folders = getFoldersSync()

        folders.forEach {
            it.path?.let { oldPath ->
                if (newPath.contains(oldPath)) {
                    logError("[Folder] New folder $newPath is contained by a previously added folder: $oldPath")
                    return true
                }
            }
        }

        return false
    }

    private fun removeContainedEntries(newPath: String) {
        val folders = getFoldersSync()

        // Remove any folders from the DB that are contained by the new folder.
        val foldersToRemove = mutableListOf<Folder>()
        folders.forEach { oldFolder ->
            oldFolder.path?.let { oldPath ->
                if (oldPath.contains(newPath)) {
                    logInfo("[Folder] New folder contains a previously added folder: $oldPath")

                    foldersToRemove.add(oldFolder)
                }
            }
        }

        if (foldersToRemove.isNotEmpty()) {
            val idsString = foldersToRemove.joinToString()
            logInfo("[Folder] Deleting folders with ids: $idsString")

            realm.inTransaction {
                foldersToRemove.forEach(Folder::deleteFromRealm)
            }
        }
    }
    
    companion object {
        val ADD_STATUS_GOOD = 0
        val ADD_STATUS_EXISTS = 1
        val ADD_STATUS_DB_ERROR = 2
    }
}
