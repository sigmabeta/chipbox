package net.sigmabeta.chipbox.model.repository

import android.content.Context
import com.raizlabs.android.dbflow.sql.language.SQLite
import net.sigmabeta.chipbox.model.domain.*
import net.sigmabeta.chipbox.model.events.FileScanEvent
import net.sigmabeta.chipbox.model.file.Folder
import net.sigmabeta.chipbox.util.*
import org.apache.commons.io.FileUtils
import rx.Observable
import rx.Subscriber
import java.io.File
import java.util.*

class DbFlowRepository(val context: Context) : Repository {
    override fun scanLibrary(): Observable<FileScanEvent> {
        return Observable.create(
                { sub ->
                    // OnSubscribe.call. it: String
                    clearAll()

                    logInfo("[Library] Scanning library...")

                    val startTime = System.currentTimeMillis()

                    val folders = Folder.getAll()

                    val gameMap = HashMap<Long, HashMap<String, Game>>()
                    val artistMap = HashMap<String, Artist>()

                    folders.forEach { folder ->
                        folder.path?.let {
                            scanFolder(File(it), gameMap, artistMap, sub as Subscriber<FileScanEvent>)
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
                    track.associateGame(it)
                    track.insert()
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

                    val relation = Artist_Track()
                    relation.artist = artist
                    relation.track = track
                    relation.insert()

                    // If this game has just one artist...
                    if (gameArtist != null && !gameHadMultipleArtists) {
                        // And the one we just got is different
                        if (artist.id != gameArtist.id) {
                            // We'll save this later.
                            game.multipleArtists = true
                            game.save()
                        }
                    } else if (gameArtist == null) {
                        game.artist = artist
                        game.save()
                    }

                    return@combineLatest game.id
                })
    }

    override fun addGame(platformId: Long, title: String?): Observable<Game> {
        return Observable.create {
            val game = Game(title ?: "Unknown Game", platformId)
            game.insert()

            it.onNext(game)
            it.onCompleted()
        }
    }

    override fun addArtist(name: String?): Observable<Artist> {
        return Observable.create {
            val artist = Artist(name ?: "Unknown Artist")
            artist.insert()

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
            Folder(path).insert()

            logInfo("[Folder] Successfully added folder to database.")

            it.onNext(ADD_STATUS_GOOD)
            it.onCompleted()
        }
    }

    /**
     * Read
     */

    override fun getTracks(): Observable<List<Track>> {
        return Observable.create {
            logInfo("[Track] Reading song list...")

            val tracks = SQLite.select().from(Track::class.java)
                    .where()
                    .orderBy(Track_Table.title, true)
                    .queryList()

            logVerbose("[Track] Found ${tracks.size} tracks.")

            it.onNext(tracks)
            it.onCompleted()
        }
    }

    override fun getGame(id: Long): Observable<Game?> {
        return Observable.create {
            logInfo("[Game] Getting game #${id}...")

            if (id > 0) {
                val game = SQLite.select()
                        .from(Game::class.java)
                        .where(Game_Table.id.eq(id))
                        .querySingle()

                if (game != null) {
                    it.onNext(game)
                    it.onCompleted()
                } else {
                    it.onError(Exception("Couldn't find game."))
                }
            } else {
                it.onError(Exception("Bad game ID."))
            }
        }
    }

    override fun getGamesForPlatform(platformId: Long): Observable<List<Game>> {
        return Observable.create {
            logInfo("[Game] Reading games list...")

            var games: List<Game>
            val query = SQLite.select().from(Game::class.java)

            // If -2 passed in, return all games. Else, return games for one platform only.
            if (platformId != Track.PLATFORM_ALL) {
                games = query
                        .where(Game_Table.platform.eq(platformId))
                        .orderBy(Game_Table.title, true)
                        .queryList()
            } else {
                games = query
                        .orderBy(Game_Table.title, true)
                        .queryList()
            }

            logVerbose("[Game] Found ${games.size} games.")

            it.onNext(games)
            it.onCompleted()
        }
    }

    override fun getGame(platformId: Long, title: String?): Observable<Game> {
        return Observable.create<Game?> {
            val game: Game?

            if (title == null) {
                game = null
            } else {
                game = SQLite.select()
                        .from(Game::class.java)
                        .where(Game_Table.title.eq(title))
                        .and(Game_Table.platform.eq(platformId))
                        .querySingle()
            }

            it.onNext(game)
            it.onCompleted()
            // TODO Add somewhere: return Game.addToDatabase(title ?: "Unknown Game", platformId ?: -Track.PLATFORM_UNSUPPORTED)
        }.flatMap { game ->
            if (game != null) {
                return@flatMap Observable.just(game)
            } else {
                return@flatMap addGame(platformId, title)
            }
        }
    }

    override fun getArtist(id: Long): Observable<Artist> {
        return Observable.create {
            logInfo("[Artist] Getting artist #${id}...")

            if (id > 0) {
                val artist = SQLite.select()
                        .from(Artist::class.java)
                        .where(Artist_Table.id.eq(id))
                        .querySingle()

                if (artist != null) {
                    it.onNext(artist)
                    it.onCompleted()
                } else {
                    it.onError(Exception("Couldn't find game."))
                }
            } else {
                it.onError(Exception("Bad game ID."))
            }
        }
    }

    override fun getArtist(name: String?): Observable<Artist> {
        return Observable.create<Artist?> {
            val artist: Artist?

            if (name == null) {
                artist = null
            } else {
                artist = SQLite.select()
                        .from(Artist::class.java)
                        .where(Artist_Table.name.eq(name))
                        .querySingle()
            }

            it.onNext(artist)
            it.onCompleted()
        }.flatMap { artist: Artist? ->
            if (artist != null) {
                return@flatMap Observable.just(artist)
            } else {
                return@flatMap addArtist(name)
            }
        }
    }

    override fun getArtists(): Observable<List<Artist>> {
        return Observable.create {
            logInfo("[Artist] Reading artist list...")

            val artists = SQLite.select().from(Artist::class.java)
                    .where()
                    .orderBy(Artist_Table.name, true)
                    .queryList()

            logVerbose("[Artist] Found ${artists.size} artists.")

            it.onNext(artists)
            it.onCompleted()
        }
    }

    override fun getArtistsForTrack(id: Long): Observable<List<Artist>> {
        return Observable.create {
            val relations = SQLite.select()
                    .from(Artist_Track::class.java)
                    .where(Artist_Track_Table.track_id.eq(id))
                    .queryList()

            val artists = ArrayList<Artist>(relations.size)

            relations.forEach {
                artists.add(it.artist)
            }

            it.onNext(artists)
            it.onCompleted()
        }
    }

    override fun getFolders(): Observable<List<Folder>> {
        return Observable.create {
            logInfo("[Folder] Reading folder list...")

            val folders = SQLite.select()
                    .from(Folder::class.java)
                    .queryList()

            logVerbose("[Folder] Found ${folders.size} folders.")

            it.onNext(folders)
            it.onCompleted()
        }
    }

    /**
     * Update
     */

    /**
     * Delete
     */
    override fun clearAll() {
        logInfo("[Library] Clearing library...")

        SQLite.delete(Artist::class.java).query()
        SQLite.delete(Game::class.java).query()
        SQLite.delete(Track::class.java).query()
    }

    /**
     * Private Methods
     */

    private fun scanFolder(folder: File, gameMap: HashMap<Long, HashMap<String, Game>>, artistMap: HashMap<String, Artist>, sub: Subscriber<FileScanEvent>) {
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
                        scanFolder(file, gameMap, artistMap, sub)
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
                                sub.onNext(FileScanEvent(FileScanEvent.TYPE_TRACK, file.name))
                            },
                            {
                                logError("[Library] Couldn't add track at ${filePath}")
                                sub.onNext(FileScanEvent(FileScanEvent.TYPE_BAD_TRACK, file.name))
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
                            sub.onNext(FileScanEvent(FileScanEvent.TYPE_TRACK, file.name))
                        },
                        {
                            logError("[Library] Couldn't read multi track file at ${filePath}")
                            sub.onNext(FileScanEvent(FileScanEvent.TYPE_BAD_TRACK, file.name))
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
