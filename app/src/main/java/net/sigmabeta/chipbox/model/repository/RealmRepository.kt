package net.sigmabeta.chipbox.model.repository

import io.realm.Realm
import net.sigmabeta.chipbox.model.database.closeAndReport
import net.sigmabeta.chipbox.model.database.getRealmInstance
import net.sigmabeta.chipbox.model.database.inTransaction
import net.sigmabeta.chipbox.model.database.save
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Platform
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.util.logInfo
import net.sigmabeta.chipbox.util.logVerbose
import net.sigmabeta.chipbox.util.logWarning
import rx.Observable
import rx.schedulers.Schedulers

class RealmRepository(var realm: Realm) : Repository {
    override fun reopen() {
        try {
            if (realm.isClosed) {
                realm = getRealmInstance()
            }
        } catch (error: IllegalStateException) {
            logError("Illegal Realm instance access on thread ${Thread.currentThread().name}")
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

        val gameObservable = getGame(track.platformName, track.gameTitle)
                .map {
                    track.game = it
                    track.save(realm)

                    realm.inTransaction {
                        it.tracks?.add(track)
                    }

                    return@map it
                }

        val platformObservable = getPlatform(track.platformName)
                .map {
                    track.platform = it
                    return@map it
                }

        val artistObservable = Observable.from(artists)
                .flatMap { name ->
                    return@flatMap getArtistByName(name)
                }

        // Combine operator happens once per Artist, once we have a Game.
        return Observable.combineLatest(gameObservable, artistObservable, platformObservable,
                { game: Game, artist: Artist, platform: Platform ->
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
                                game.artist = null
                            }
                        } else if (gameArtist == null) {
                            game.artist = artist
                        }

                        game.platformName = platform.name
                    }

                    return@combineLatest game
                })
    }

    override fun addGame(platformName: String, title: String?): Observable<Game> {
        return Observable.create {
            val game = Game(title ?: GAME_UNKNOWN, platformName)
            game.save(realm)

            it.onNext(game)
            it.onCompleted()
        }
    }

    override fun addArtist(name: String?): Observable<Artist> {
        return Observable.create {
            val artist = Artist(name ?: ARTIST_UNKNOWN)
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

    override fun getTracksManaged(): List<Track> {
        return realm
                .where(Track::class.java)
                .findAll()
    }

    override fun getTracksFromIds(trackIdsList: MutableList<String?>): Observable<out List<Track>> {
        return realm
                .where(Track::class.java)
                .`in`("id", trackIdsList.toTypedArray())
                .findAllAsync()
                .asObservable()
                .filter { it.isLoaded }
    }

    override fun getTrackFromPath(path: String): Track? {
        return realm.where(Track::class.java)
                .equalTo("path", path)
                .findFirst()
    }

    override fun getTrackFromPath(path: String, trackNumber: Int): Track? {
        return realm.where(Track::class.java)
                .equalTo("path", path)
                .equalTo("trackNumber", trackNumber)
                .findFirst()
    }

    override fun getTrack(title: String, gameTitle: String, platformName: String): Track? {
        return realm.where(Track::class.java)
                .equalTo("title", title)
                .equalTo("gameTitle", gameTitle)
                .equalTo("platformName", platformName)
                .findFirst()
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

    override fun getGamesManaged(): List<Game> {
        return realm
                .where(Game::class.java)
                .findAll()
    }

    override fun getGamesForPlatform(platformName: String): Observable<out List<Game>> {
        val observable = Observable.create<List<Game>> {
            val localRealm = getRealmInstance()

            val gamesManaged = localRealm.where(Game::class.java)
                    .equalTo("platformName", platformName)
                    .findAllSorted("title")

            val gamesUnmanaged = localRealm.copyFromRealm(gamesManaged)

            localRealm.closeAndReport()

            it.onNext(gamesUnmanaged)
            it.onCompleted()
        }

        return observable.subscribeOn(Schedulers.io())
    }

    override fun getGame(platformName: String?, title: String?): Observable<Game> {
        var game = realm
                .where(Game::class.java)
                .equalTo("platformName", platformName)
                .equalTo("title", title)
                .findFirst()

        val newGame: Game
        if (game == null || !game.isValid) {
            newGame = Game(title ?: GAME_UNKNOWN, platformName ?: PLATFORM_UNKNOWN)
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
            newArtist = Artist(name ?: GAME_UNKNOWN)
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

    override fun getArtistsManaged(): List<Artist> {
        return realm
                .where(Artist::class.java)
                .findAll()
    }

    override fun getPlatform(name: String?): Observable<Platform> {
        var platform = realm
                .where(Platform::class.java)
                .equalTo("name", name)
                .findFirst()

        val newPlatform: Platform
        if (platform == null || !platform.isValid) {
            logVerbose("Creating platform: ${name}")
            newPlatform = Platform(name ?: PLATFORM_UNKNOWN)
            platform = newPlatform.save(realm)
        }

        return Observable.just(platform)
    }

    override fun getPlatforms(): Observable<out List<Platform>> {
        val observable = Observable.create<List<Platform>> {
            val localRealm = getRealmInstance()

            val platformsManaged = localRealm.where(Platform::class.java)
                    .findAllSorted("name")

            val platformsUnmanaged = localRealm.copyFromRealm(platformsManaged)

            localRealm.closeAndReport()

            it.onNext(platformsUnmanaged)
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

    override fun updateTrack(oldTrack: Track, newTrack: Track): Boolean {
        var actuallyChanged = false

        realm.inTransaction {
            if (oldTrack.title != newTrack.title) {
                oldTrack.title = newTrack.title
                actuallyChanged = true
            }

            if (updateArtists(newTrack, oldTrack)) {
                actuallyChanged = true
            }


            if (updateGame(newTrack, oldTrack)) {
                actuallyChanged = true
            }

            if (oldTrack.path != newTrack.path) {
                oldTrack.path = newTrack.path
                actuallyChanged = true
            }

            if (oldTrack.trackNumber != newTrack.trackNumber) {
                oldTrack.trackNumber = newTrack.trackNumber
                actuallyChanged = true
            }

            if (oldTrack.trackLength != newTrack.trackLength) {
                oldTrack.trackLength = newTrack.trackLength
                actuallyChanged = true
            }

            if (oldTrack.introLength != newTrack.introLength) {
                oldTrack.introLength = newTrack.introLength
                actuallyChanged = true
            }

            if (oldTrack.loopLength != newTrack.loopLength) {
                oldTrack.loopLength = newTrack.loopLength
                actuallyChanged = true
            }
        }

        if (actuallyChanged) {
            logVerbose("Updated track: ${oldTrack.title}")
        }

        return actuallyChanged
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

    override fun deleteTrack(track: Track) {
        realm.inTransaction {
            track.deleteFromRealm()
        }
    }

    override fun deleteGame(game: Game) {
        realm.inTransaction {
            game.deleteFromRealm()
        }
    }

    override fun deleteArtist(artist: Artist) {
        realm.inTransaction {
            artist.deleteFromRealm()
        }
    }

    /**
     * Private Methods
     */

    private fun updateArtists(newTrack: Track, oldTrack: Track): Boolean {
        var actuallyChanged = false

        if (oldTrack.artistText != newTrack.artistText) {
            oldTrack.artistText = newTrack.artistText

            val oldArtists = oldTrack.artists
            val newArtists = newTrack.artistText?.split(", ")

            // Remove any artists that no longer pertain to this track.
            oldArtists?.forEach { oldArtist ->
                val matchingArtist = newArtists?.first { newArtistName ->
                    newArtistName == oldArtist.name
                }

                if (matchingArtist == null) {
                    logWarning("New track missing artist: ${oldArtist.name}")
                    oldArtist.tracks?.remove(oldTrack)
                    oldTrack.artists?.remove(oldArtist)

                    actuallyChanged = true
                }
            }

            // Add any new artists to this track.
            newArtists?.forEach { newArtist ->
                val matchingArtist = oldArtists?.first { oldArtist ->
                    newArtist == oldArtist.name
                }

                if (matchingArtist == null) {
                    logVerbose("Adding artist: $newArtist")

                    getArtistByName(newArtist)
                            .subscribe {
                                it.tracks?.add(oldTrack)
                                oldTrack.artists?.add(it)
                            }

                    actuallyChanged = true
                }
            }
        }

        return actuallyChanged
    }

    private fun updateGame(newTrack: Track, oldTrack: Track): Boolean {
        var actuallyChanged = false

        val oldGame = oldTrack.game
        if (oldTrack.gameTitle != newTrack.gameTitle) {
            logWarning("New track doesn't match old track game: ${oldTrack.gameTitle}")
            oldGame?.tracks?.remove(oldTrack)

            getGame(newTrack.platformName, newTrack.gameTitle)
                    .subscribe {
                        it.tracks?.add(oldTrack)
                        oldTrack.game = it
                    }

            actuallyChanged = true
        }

        val game = oldTrack.game
        if (game != null) {
            if (!(game.multipleArtists ?: true)) {
                if (oldTrack.artists?.size ?: 0 > 1) {
                    actuallyChanged = true

                    game.multipleArtists = true
                    game.artist = null

                } else if (oldTrack.artists?.getOrNull(0)?.name != game.artist?.name) {
                    actuallyChanged = true

                    game.multipleArtists = true
                    game.artist = null
                }
            }
        }

        return actuallyChanged
    }

    companion object {
        val ADD_STATUS_GOOD = 0
        val ADD_STATUS_EXISTS = 1
        val ADD_STATUS_DB_ERROR = 2

        val TITLE_UNKNOWN = "Unknown Track"
        val GAME_UNKNOWN = "Unknown Game"
        val ARTIST_UNKNOWN = "Unknown Artist"
        val PLATFORM_UNKNOWN = "Unknown Platform"
    }
}
