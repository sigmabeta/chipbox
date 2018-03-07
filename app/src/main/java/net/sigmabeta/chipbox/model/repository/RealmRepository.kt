package net.sigmabeta.chipbox.model.repository

import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.realm.Realm
import io.realm.RealmResults
import net.sigmabeta.chipbox.model.database.closeAndReport
import net.sigmabeta.chipbox.model.database.getRealmInstance
import net.sigmabeta.chipbox.model.database.inTransaction
import net.sigmabeta.chipbox.model.database.save
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Platform
import net.sigmabeta.chipbox.model.domain.Track
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit

class RealmRepository(var realm: Realm) : Repository {
    var id: UUID = UUID.randomUUID()

    var isClosed = false

    override fun reopen() {
        try {
            if (isClosed) {
                Timber.v("Reopening repository: %s", id)
                realm = getRealmInstance()
                isClosed = false
            } else {
                Timber.v("Already opened repository: %s", id)
            }
        } catch (error: IllegalStateException) {
            Timber.e("Illegal Realm instance access on thread ${Thread.currentThread().name}")
            realm = getRealmInstance()
            isClosed = false
        }
    }

    override fun close() {
        if (!isClosed) {
            Timber.v("Closing repository: %s", id)
            realm.closeAndReport()
            isClosed = true
        } else {
            Timber.e("Already closed repository: %s", id)
        }
    }

    /**
     * Create
     */

    override fun addTrack(track: Track): Flowable<Game> {
        track.save(realm)

        val game = getGame(track.platformName, track.gameTitle)
        val platform = getPlatform(track.platformName)

        realm.inTransaction {
            track.game = game
            game.tracks?.add(track)

            track.platform = platform
            game.platformName = track.platformName
        }

        val artistNames = track.artistText?.split(*DELIMITERS_ARTISTS)
                ?.map(String::trim)

        val artists = artistNames?.map { name ->
            return@map getArtistByName(name)
        }

        val gameArtist = game.artist
        val gameHadMultipleArtists = game.multipleArtists ?: false

        artists?.forEach { artist ->
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
            }
        }

        return Flowable.just(game)
    }

    override fun addGame(platformName: String, title: String?): Flowable<Game> {
        return Flowable.create({
            val game = Game(title ?: GAME_UNKNOWN, platformName)
            game.save(realm)

            it.onNext(game)
            it.onComplete()
        }, BackpressureStrategy.LATEST)
    }

    override fun addArtist(name: String?): Flowable<Artist> {
        return Flowable.create({
            val artist = Artist(name ?: ARTIST_UNKNOWN)
            artist.save(realm)

            it.onNext(artist)
            it.onComplete()
        }, BackpressureStrategy.LATEST)
    }

    /**
     * Read
     */

    override fun getTracks() = realm.where(Track::class.java)
            .sort("title")
            .findAllAsync()
            .asChangesetObservable()
            .sample(INTERVAL, TimeUnit.MILLISECONDS)
            .filter { it.collection.isLoaded }

    override fun getTracksManaged(): RealmResults<Track> = realm
            .where(Track::class.java)
            .findAll()

    override fun getTracksFromIds(trackIdsList: MutableList<String?>): Flowable<RealmResults<Track>> = realm
            .where(Track::class.java)
            .`in`("id", trackIdsList.toTypedArray())
            .findAllAsync()
            .asFlowable()
            .filter { it.isLoaded }

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

    override fun getGame(id: String) = realm.where(Game::class.java)
            .equalTo("id", id)
            .findFirstAsync()
            .asFlowable<Game>()
            .filter { it.isLoaded }

    override fun getGameSync(id: String) = realm.where(Game::class.java)
            .equalTo("id", id)
            .findFirst()

    override fun getGames() = realm.where(Game::class.java)
            .sort("title")
            .findAllAsync()
            .asChangesetObservable()
            .sample(INTERVAL, TimeUnit.MILLISECONDS)
            .filter { it.collection.isLoaded }

    override fun getGamesManaged(): RealmResults<Game> = realm
            .where(Game::class.java)
            .findAll()

    override fun getGamesForPlatform(platformName: String) = realm.where(Game::class.java)
            .equalTo("platformName", platformName)
            .sort("title")
            .findAllAsync()
            .asChangesetObservable()
            .sample(INTERVAL, TimeUnit.MILLISECONDS)
            .filter { it.collection.isLoaded }

    override fun getGame(platformName: String?, title: String?): Game {
        var game = realm
                .where(Game::class.java)
                .equalTo("platformName", platformName)
                .equalTo("title", title)
                .findFirst()

        val newGame: Game
        if (game == null || !game.isValid) {
            newGame = Game(title ?: GAME_UNKNOWN, platformName ?: PLATFORM_UNKNOWN)
            Timber.v("Created game: ${newGame.title}")
            game = newGame.save(realm)
        }

        return game
    }

    override fun getArtist(id: String) = realm.where(Artist::class.java)
            .equalTo("id", id)
            .findFirstAsync()
            .asFlowable<Artist>()
            .filter { it.isLoaded }

    override fun getArtistByName(name: String?): Artist {
        var artist = realm.where(Artist::class.java)
                .equalTo("name", name)
                .findFirst()

        val newArtist: Artist
        if (artist == null || !artist.isValid) {
            newArtist = Artist(name ?: GAME_UNKNOWN)
            Timber.v("Created artist: ${newArtist.name}")
            artist = newArtist.save(realm)
        }

        return artist
    }

    override fun getArtists() = realm.where(Artist::class.java)
            .sort("name")
            .findAllAsync()
            .asChangesetObservable()
            .sample(INTERVAL, TimeUnit.MILLISECONDS)
            .filter { it.collection.isLoaded }


    override fun getArtistsManaged(): List<Artist> = realm
            .where(Artist::class.java)
            .findAll()

    override fun getPlatform(name: String?): Platform {
        var platform = realm
                .where(Platform::class.java)
                .equalTo("name", name)
                .findFirst()

        val newPlatform: Platform
        if (platform == null || !platform.isValid) {
            Timber.v("Creating platform: ${name}")
            newPlatform = Platform(name ?: PLATFORM_UNKNOWN)
            platform = newPlatform.save(realm)
        }

        return platform
    }

    override fun getPlatforms() = realm.where(Platform::class.java)
            .sort("name")
            .findAllAsync()
            .asChangesetObservable()
            .sample(INTERVAL, TimeUnit.MILLISECONDS)
            .filter { it.collection.isLoaded }

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
            Timber.v("Updated track: ${oldTrack.title}")
        }

        return actuallyChanged
    }

    /**
     * Delete
     */

    override fun clearAll() {
        Timber.i("[Library] Clearing library...")

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
            val newArtists = newTrack.artistText?.split(*DELIMITERS_ARTISTS)
                    ?.map(String::trim)

            // Remove any artists that no longer pertain to this track.
            oldArtists?.forEach { oldArtist ->
                val matchingArtist = newArtists?.first { newArtistName ->
                    newArtistName == oldArtist.name
                }

                if (matchingArtist == null) {
                    Timber.w("New track missing artist: ${oldArtist.name}")
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
                    Timber.v("Adding artist: $newArtist")

                    val artist = getArtistByName(newArtist)

                    artist.tracks?.add(oldTrack)
                    oldTrack.artists?.add(artist)

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
            Timber.w("New track doesn't match old track game: ${oldTrack.gameTitle}")
            oldGame?.tracks?.remove(oldTrack)

            val game = getGame(newTrack.platformName, newTrack.gameTitle)

            game.tracks?.add(oldTrack)
            oldTrack.game = game

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

        val DELIMITERS_ARTISTS = arrayOf(", &", ",", " or ", " and ", "&")

        const val INTERVAL = 50L
    }
}
