package net.sigmabeta.chipbox.model.repository

import android.content.Context
import android.util.Log
import io.realm.Realm
import net.sigmabeta.chipbox.model.database.save
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.events.FileScanEvent
import net.sigmabeta.chipbox.model.file.Folder
import net.sigmabeta.chipbox.util.*
import org.apache.commons.io.FileUtils
import rx.Observable
import rx.Subscriber
import java.io.File
import java.util.*

class RealmRepository(val context: Context) : Repository {
    override fun scanLibrary(): Observable<FileScanEvent> {
        return Observable.create(
                { sub ->
                    // OnSubscribe.call. it: String
                    clearAll()

                    logInfo("[Library] Scanning library...")

                    val startTime = System.currentTimeMillis()

                    val folders = Folder.getAll()

                    folders.forEach { folder ->
                        folder.path?.let {
                            scanFolder(File(it), sub as Subscriber<FileScanEvent>)
                        }
                    }

                    val endTime = System.currentTimeMillis()
                    val scanDuration = (endTime - startTime) / 1000.0f

                    logInfo("[Library] Scanned library in ${scanDuration} seconds.")

                    sub.onCompleted()
                }
        )
    }

    /**
     * Create
     */

    override fun addTrack(track: Track): Observable<String> {
        val artists = track.artistText?.split(", ")

        val gameObservable = getGame(track.platform, track.gameTitle)
                .map {
                    track.game = it
                    track.save()

                    val realm = Realm.getDefaultInstance()
                    val wasInTransactionBefore: Boolean
                    if (realm.isInTransaction) {
                        wasInTransactionBefore = true
                    } else {
                        wasInTransactionBefore = false
                        realm.beginTransaction()
                    }

                    it.tracks?.add(track)

                    if (wasInTransactionBefore) {
                        realm.commitTransaction()
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

                    val realm = Realm.getDefaultInstance()

                    val wasInTransactionBefore: Boolean
                    if (realm.isInTransaction) {
                        wasInTransactionBefore = true
                    } else {
                        wasInTransactionBefore = false
                        realm.beginTransaction()
                    }

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

                    if (wasInTransactionBefore) {
                        realm.commitTransaction()
                    }

                    return@combineLatest game.id
                })
    }

    override fun addGame(platformId: Long, title: String?): Observable<Game> {
        return Observable.create {
            val game = Game(title ?: "Unknown Game", platformId)
            game.save()

            it.onNext(game)
            it.onCompleted()
        }
    }

    override fun addArtist(name: String?): Observable<Artist> {
        return Observable.create {
            val artist = Artist(name ?: "Unknown Artist")
            artist.save()

            it.onNext(artist)
            it.onCompleted()
        }
    }

    override fun addFolder(path: String): Observable<Int> {
        return Observable.create {
            if (Folder.checkIfContained(path)) {
                it.onNext(ADD_STATUS_EXISTS)
                it.onCompleted()
                return@create
            }

            Folder.removeContainedEntries(path)
            Folder(path).save()

            logInfo("[Folder] Successfully added folder to database.")

            it.onNext(ADD_STATUS_GOOD)
            it.onCompleted()
        }
    }

    /**
     * Read
     */


    override fun getTracks(): Observable<out List<Track>> {
        val realm = Realm.getDefaultInstance()
        return realm
                .where(Track::class.java)
                .findAllSortedAsync("title")
                .asObservable()
                .filter { it.isLoaded }
    }

    override fun getTrackSync(id: String): Track? {
        val realm = Realm.getDefaultInstance()
        return realm.where(Track::class.java)
                .equalTo("id", id)
                .findFirst()
    }

    override fun getGame(id: String): Observable<Game> {
        val realm = Realm.getDefaultInstance()
        return realm.where(Game::class.java)
                .equalTo("id", id)
                .findFirstAsync()
                .asObservable<Game>()
                .filter { it.isLoaded }
    }

    override fun getGameSync(id: String): Game? {
        val realm = Realm.getDefaultInstance()
        return realm.where(Game::class.java)
                .equalTo("id", id)
                .findFirst()
    }

    override fun getGames(): Observable<out List<Game>> {
        val realm = Realm.getDefaultInstance()
        return realm
                .where(Game::class.java)
                .findAllSortedAsync("title")
                .asObservable()
                .filter { it.isLoaded }
    }

    override fun getGamesForPlatform(platformId: Long): Observable<out List<Game>> {
        val realm = Realm.getDefaultInstance()
        return realm
                .where(Game::class.java)
                .equalTo("platform", platformId)
                .findAllAsync()
                .asObservable()
                .filter { it.isLoaded }
    }

    override fun getGame(platformId: Long, title: String?): Observable<Game> {
        val realm = Realm.getDefaultInstance()
        var game = realm
                .where(Game::class.java)
                .equalTo("platform", platformId)
                .equalTo("title", title)
                .findFirst()
        realm.close()

        val newGame: Game;
        if (game == null || !game.isValid) {
            newGame = Game(title ?: "Unknown Game", platformId)
            logVerbose("Created game: ${newGame.title}")
            game = newGame.save()
        }

        return Observable.just(game)
    }

    override fun getArtist(id: String): Observable<Artist> {
        val realm = Realm.getDefaultInstance()
        return realm.where(Artist::class.java)
                .equalTo("id", id)
                .findFirstAsync()
                .asObservable<Artist>()
                .filter { it.isLoaded }
    }

    override fun getArtistByName(name: String?): Observable<Artist> {
        val realm = Realm.getDefaultInstance()
        var artist = realm.where(Artist::class.java)
                .equalTo("name", name)
                .findFirst()

        realm.close()

        val newArtist: Artist
        if (artist == null || !artist.isValid) {
            newArtist = Artist(name ?: "Unknown Game")
            logVerbose("Created artist: ${newArtist.name}")
            artist = newArtist.save()
        }

        return Observable.just(artist)
    }

    override fun getArtists(): Observable<out List<Artist>> {
        val realm = Realm.getDefaultInstance()
        return realm
                .where(Artist::class.java)
                .findAllSortedAsync("name")
                .asObservable()
                .filter { it.isLoaded }
    }

    override fun getFolders(): Observable<out List<Folder>> {
        val realm = Realm.getDefaultInstance()
        return realm.where(Folder::class.java)
                .findAllAsync()
                .asObservable()
                .filter { it.isLoaded }
    }

    /**
     * Update
     */

    /**
     * Delete
     */
    override fun clearAll() {
        logInfo("[Library] Clearing library...")
        val realm = Realm.getDefaultInstance()
        realm.beginTransaction()

        realm.delete(Track::class.java)
        realm.delete(Artist::class.java)
        realm.delete(Game::class.java)

        realm.commitTransaction()
        realm.close()
    }

    /**
     * Private Methods
     */

    private fun scanFolder(folder: File, sub: Subscriber<FileScanEvent>) {
        val folderPath = folder.absolutePath
        logInfo("[Library] Reading files from library folder: ${folderPath}")

        sub.onNext(FileScanEvent(FileScanEvent.TYPE_FOLDER, folderPath))

        var folderGameId: String? = null

        // Iterate through every file in the folder.
        val children = folder.listFiles()

        if (children != null) {
            Arrays.sort(children)

            var trackCount = 1

            for (file in children) {
                if (!file.isHidden) {
                    if (file.isDirectory) {
                        scanFolder(file, sub)
                    } else {
                        val filePath = file.absolutePath
                        val fileExtension = getFileExtension(filePath)

                        if (fileExtension != null) {
                            // Check that the file has an extension we care about before trying to read out of it.
                            if (EXTENSIONS_MUSIC.contains(fileExtension)) {
                                if (EXTENSIONS_MULTI_TRACK.contains(fileExtension)) {
                                    folderGameId = readMultipleTracks(file, filePath, sub)
                                    if (folderGameId == null) {
                                        sub.onNext(FileScanEvent(FileScanEvent.TYPE_BAD_TRACK, file.name))
                                    }
                                } else {
                                    folderGameId = readSingleTrack(file, filePath, sub, trackCount)

                                    if (folderGameId != null) {
                                        trackCount += 1
                                    }
                                }
                            } else if (EXTENSIONS_IMAGES.contains(fileExtension)) {
                                if (folderGameId != null) {
                                    copyImageToInternal(folderGameId, file)
                                } else {
                                    logError("[Library] Found image, but game ID unknown: ${filePath}")
                                }
                            }
                        }
                    }
                }
            }

        } else if (!folder.exists()) {
            logError("[Library] Folder no longer exists: ${folderPath}")
        } else {
            logError("[Library] Folder contains no tracks:  ${folderPath}")
        }
    }

    private fun readSingleTrack(file: File, filePath: String, sub: Subscriber<FileScanEvent>, trackNumber: Int): String? {
        val track = readSingleTrackFile(filePath, trackNumber)

        if (track != null) {
            var folderGameId: String? = null

            addTrack(track)
                    .toBlocking()
                    .subscribe(
                            {
                                folderGameId = it
                            },
                            {
                                logError("[Library] Couldn't add track at ${filePath}: ${Log.getStackTraceString(it)}")
                                sub.onNext(FileScanEvent(FileScanEvent.TYPE_BAD_TRACK, file.name))
                            },
                            {
                                sub.onNext(FileScanEvent(FileScanEvent.TYPE_TRACK, file.name))
                            }
                    )

            return folderGameId
        } else {
            logError("[Library] Couldn't read track at ${filePath}")
            sub.onNext(FileScanEvent(FileScanEvent.TYPE_BAD_TRACK, file.name))

            return null
        }
    }

    private fun readMultipleTracks(file: File, filePath: String, sub: Subscriber<FileScanEvent>): String? {
        var folderGameId: String? = null
        val tracks = readMultipleTrackFile(filePath)

        tracks ?: return folderGameId

        Observable.from(tracks)
                .flatMap { return@flatMap addTrack(it) }
                .toBlocking()
                .subscribe(
                        {
                            folderGameId = it
                        },
                        {
                            logError("[Library] Couldn't read multi track file at ${filePath}: ${Log.getStackTraceString(it)}")
                            sub.onNext(FileScanEvent(FileScanEvent.TYPE_BAD_TRACK, file.name))
                        },
                        {
                            sub.onNext(FileScanEvent(FileScanEvent.TYPE_TRACK, file.name))
                        }
                )

        return folderGameId
    }

    private fun copyImageToInternal(gameId: String, sourceFile: File) {
        val storageDir = context.getExternalFilesDir(null)

        val targetDirPath = storageDir.absolutePath + "/images/" + gameId.toString()
        val targetDir = File(targetDirPath)

        targetDir.mkdirs()

        val sourcePath = sourceFile.path
        val extensionStart = sourcePath.lastIndexOf('.')
        val fileExtension = sourcePath.substring(extensionStart)

        val targetFilePath = targetDirPath + "/local" + fileExtension
        val targetFile = File(targetFilePath)

        FileUtils.copyFile(sourceFile, targetFile)

        logInfo("[Library] Copied image: ${sourcePath} to ${targetFilePath}")

        Game.addLocalImage(gameId, "file://" + targetFilePath)
    }

    companion object {
        val ADD_STATUS_GOOD = 0
        val ADD_STATUS_EXISTS = 1
        val ADD_STATUS_DB_ERROR = 2
    }
}
