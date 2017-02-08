package net.sigmabeta.chipbox.model.repository

import io.realm.Realm
import net.sigmabeta.chipbox.model.database.closeAndReport
import net.sigmabeta.chipbox.model.database.getRealmInstance
import net.sigmabeta.chipbox.model.database.inTransaction
import net.sigmabeta.chipbox.model.database.save
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.util.logInfo
import net.sigmabeta.chipbox.util.logVerbose
import rx.Observable
import rx.schedulers.Schedulers

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

    /**
     * Read
     */


    override fun getTracks(): Observable<out List<Track>> {
        val observable = Observable.create<List<Track>> {
            val localRealm = getRealmInstance()

            val tracksManaged = localRealm.where(Track::class.java)
                    .findAllSorted("title")

            val tracksUnmanaged = localRealm.copyFromRealm(tracksManaged)

            localRealm.closeAndReport()

            it.onNext(tracksUnmanaged)
            it.onCompleted()
        }

        return observable.subscribeOn(Schedulers.io())
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
        val observable = Observable.create<List<Game>> {
            val localRealm = getRealmInstance()

            val gamesManaged = localRealm.where(Game::class.java)
                    .findAllSorted("title")

            val gamesUnmanaged = localRealm.copyFromRealm(gamesManaged)

            localRealm.closeAndReport()

            it.onNext(gamesUnmanaged)
            it.onCompleted()
        }

        return observable.subscribeOn(Schedulers.io())
    }

    override fun getGamesForPlatform(platformId: Long): Observable<out List<Game>> {
        val observable = Observable.create<List<Game>> {
            val localRealm = getRealmInstance()

            val gamesManaged = localRealm.where(Game::class.java)
                    .equalTo("platform", platformId)
                    .findAllSorted("title")

            val gamesUnmanaged = localRealm.copyFromRealm(gamesManaged)

            localRealm.closeAndReport()

            it.onNext(gamesUnmanaged)
            it.onCompleted()
        }

        return observable.subscribeOn(Schedulers.io())
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
        val observable = Observable.create<List<Artist>> {
            val localRealm = getRealmInstance()

            val artistsManaged = localRealm.where(Artist::class.java)
                    .findAllSorted("name")

            val artistsUnmanaged = localRealm.copyFromRealm(artistsManaged)

            localRealm.closeAndReport()

            it.onNext(artistsUnmanaged)
            it.onCompleted()
        }

        return observable.subscribeOn(Schedulers.io())
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
    
    companion object {
        val ADD_STATUS_GOOD = 0
        val ADD_STATUS_EXISTS = 1
        val ADD_STATUS_DB_ERROR = 2
    }
}
