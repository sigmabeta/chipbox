package net.sigmabeta.chipbox.model.repository

import android.content.Context
import io.realm.Realm
import net.sigmabeta.chipbox.model.database.findAll
import net.sigmabeta.chipbox.model.database.findFirst
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

    override fun addTrack(track: Track): Observable<Long> {
        val artists = track.artistText?.split(", ")

        val gameObservable = getGame(track.platform, track.gameTitle)
                .map {
                    track.game = it
                    track.save()
                    return@map it
                }

        val artistObservable = Observable.from(artists)
                .flatMap { name ->
                    return@flatMap getArtist(name)
                }

        // Combine operator happens once per Artist, once we have a Game.
        return Observable.combineLatest(gameObservable, artistObservable,
                { game: Game, artist: Artist ->
                    val gameArtist = game.artist
                    val gameHadMultipleArtists = game.multipleArtists ?: false

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
        return realm.findAll(Track::class.java)
    }

    override fun getGame(id: Long): Observable<Game> {
        val realm = Realm.getDefaultInstance()
        return realm.findFirst(Game::class.java, id)
    }

    override fun getGamesForPlatform(platformId: Long): Observable<out List<Game>> {
        val realm = Realm.getDefaultInstance()
        return realm
                .where(Game::class.java)
                .equalTo("platform", platformId)
                .findAllAsync()
                .asObservable()
    }

    override fun getGame(platformId: Long, title: String?): Observable<Game> {
        val realm = Realm.getDefaultInstance()
        return realm
                .where(Game::class.java)
                .equalTo("platform", platformId)
                .equalTo("title", title)
                .findFirstAsync()
                .asObservable<Game>()
                .map {
                    var game = it
                    if (!game.isValid) {
                        game = realm.copyToRealm(it)
                    }
                    return@map game
                }
    }

    override fun getArtist(id: Long): Observable<Artist> {
        val realm = Realm.getDefaultInstance()
        return realm.findFirst(Artist::class.java, id)
    }

    override fun getArtist(name: String?): Observable<Artist> {
        val realm = Realm.getDefaultInstance()
        return realm.where(Artist::class.java)
                .equalTo("name", name)
                .findFirstAsync()
                .asObservable<Artist>()
                .filter { it.isLoaded }
    }

    override fun getArtists(): Observable<out List<Artist>> {
        val realm = Realm.getDefaultInstance()
        return realm.findAll(Artist::class.java)
    }

    override fun getFolders(): Observable<out List<Folder>> {
        val realm = Realm.getDefaultInstance()
        return realm.findAll(Folder::class.java)
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
        realm.deleteAll()
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

        var folderGameId: Long? = null

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
                                    if (folderGameId <= 0) {
                                        sub.onNext(FileScanEvent(FileScanEvent.TYPE_BAD_TRACK, file.name))
                                    }
                                } else {
                                    folderGameId = readSingleTrack(file, filePath, sub, trackCount)

                                    if (folderGameId > 0) {
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

    private fun readSingleTrack(file: File, filePath: String, sub: Subscriber<FileScanEvent>, trackNumber: Int): Long {
        val track = readSingleTrackFile(filePath, trackNumber)

        if (track != null) {
            var folderGameId: Long = -1L

            addTrack(track)
                    .toBlocking()
                    .subscribe(
                            {
                                folderGameId = it
                            },
                            {
                                logError("[Library] Couldn't add track at ${filePath}")
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

            return -1
        }
    }

    private fun readMultipleTracks(file: File, filePath: String, sub: Subscriber<FileScanEvent>): Long {
        var folderGameId = -1L
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
                            logError("[Library] Couldn't read multi track file at ${filePath}")
                            sub.onNext(FileScanEvent(FileScanEvent.TYPE_BAD_TRACK, file.name))
                        },
                        {
                            sub.onNext(FileScanEvent(FileScanEvent.TYPE_TRACK, file.name))
                        }
                )

        return folderGameId
    }

    private fun copyImageToInternal(gameId: Long, sourceFile: File) {
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
