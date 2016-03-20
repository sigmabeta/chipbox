package net.sigmabeta.chipbox.model.database

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

val TRACK_LENGTH_DEFAULT = 150000L

class SongDatabaseHelper(val context: Context) {
    fun getGame(gameId: Long): Observable<Game> {
        return Observable.create(
                {
                    logInfo("[SongDatabaseHelper] Getting game #${gameId}...")

                    if (gameId > 0) {
                        val game = getGameFromDb(gameId)

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
        )
    }

    fun getArtist(artistId: Long): Observable<Artist> {
        return Observable.create(
                {
                    logInfo("[SongDatabaseHelper] Getting artist #${artistId}...")

                    if (artistId > 0) {
                        val artist = getArtistFromDb(artistId)

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
        )
    }

    fun getGamesList(platform: Long): Observable<List<Game>> {
        return Observable.create(
                {
                    logInfo("[SongDatabaseHelper] Reading games list...")

                    var games: List<Game>
                    val query = SQLite.select().from(Game::class.java)

                    // If -2 passed in, return all games. Else, return games for one platform only.
                    if (platform != Track.PLATFORM_ALL) {
                        games = query
                                .where(Game_Table.platform.eq(platform))
                                .orderBy(Game_Table.title, true)
                                .queryList()
                    } else {
                        games = query
                                .orderBy(Game_Table.title, true)
                                .queryList()
                    }

                    logVerbose("[SongDatabaseHelper] Found ${games.size} games.")

                    it.onNext(games)
                    it.onCompleted()
                }
        )
    }

    fun getGamesForTrackList(tracks: List<Track>): Observable<HashMap<Long, Game>> {
        return Observable.create(
                {
                    logInfo("[SongDatabaseHelper] Getting games for currently displayed tracks...")
                    val startTime = System.currentTimeMillis()

                    val games = HashMap<Long, Game>()

                    for (track in tracks) {
                        val gameId = track.gameId ?: -1

                        if (games[gameId] != null) {
                            continue
                        }

                        if (gameId > 0) {
                            val game = getGameFromDb(gameId)
                            if (game != null) {
                                games.put(gameId, game)
                            } else {
                                it.onError(Exception("Couldn't find game."))
                                return@create
                            }
                        } else {
                            it.onError(Exception("Bad game ID: $gameId"))
                            return@create
                        }
                    }

                    logVerbose("[SongDatabaseHelper] Game map size: ${games.size}")

                    val endTime = System.currentTimeMillis()
                    val scanDuration = (endTime - startTime) / 1000.0f

                    logInfo("[SongDatabaseHelper] Found games in ${scanDuration} seconds.")

                    it.onNext(games)
                    it.onCompleted()
                }
        )
    }

    fun getArtistList(): Observable<List<Artist>> {
        return Observable.create(
                {
                    logInfo("[SongDatabaseHelper] Reading artist list...")

                    val artists = SQLite.select().from(Artist::class.java)
                            .where()
                            .orderBy(Artist_Table.name, true)
                            .queryList()

                    logVerbose("[SongDatabaseHelper] Found ${artists.size} artists.")

                    it.onNext(artists)
                    it.onCompleted()
                }
        )
    }

    fun getSongList(): Observable<List<Track>> {
        return Observable.create(
                {
                    logInfo("[SongDatabaseHelper] Reading song list...")

                    val tracks = SQLite.select().from(Track::class.java)
                            .where()
                            .orderBy(Track_Table.title, true)
                            .queryList()

                    logVerbose("[SongDatabaseHelper] Found ${tracks.size} tracks.")

                    it.onNext(tracks)
                    it.onCompleted()
                }
        )
    }

    fun getSongListForArtist(artistId: Long): Observable<List<Track>> {
        return Observable.create(
                {
                    logInfo("[SongDatabaseHelper] Reading song list for artist #$artistId...")

                    val tracks = SQLite.select().from(Track::class.java)
                            .where(Track_Table.artistId.eq(artistId))
                            .orderBy(Track_Table.gameId, true)
                            .queryList()

                    logVerbose("[SongDatabaseHelper] Found ${tracks.size} tracks.")

                    it.onNext(tracks)
                    it.onCompleted()
                }
        )
    }

    fun getSongListForGame(gameId: Long): Observable<List<Track>> {
        return Observable.create(
                {
                    logInfo("[SongDatabaseHelper] Reading song list for game #$gameId...")

                    val tracks = SQLite.select().from(Track::class.java)
                            .where(Track_Table.gameId.eq(gameId))
                            .orderBy(Track_Table.trackNumber, true)
                            .queryList()

                    logVerbose("[SongDatabaseHelper] Found ${tracks.size} tracks.")

                    it.onNext(tracks)
                    it.onCompleted()
                }
        )
    }

    fun scanLibrary(): Observable<FileScanEvent> {
        return Observable.create(
                { sub ->
                    // OnSubscribe.call. it: String
                    clearTables()

                    logInfo("[SongDatabaseHelper] Scanning library...")

                    val startTime = System.currentTimeMillis()

                    val folders = Folder.getAll()

                    val gameMap = HashMap<Long, Game>()
                    val artistMap = HashMap<Long, Artist>()

                    folders.forEach { folder ->
                        folder.path?.let {
                            scanFolder(File(it), gameMap, artistMap, sub as Subscriber<FileScanEvent>)
                        }
                    }

                    val endTime = System.currentTimeMillis()
                    val scanDuration = (endTime - startTime) / 1000.0f

                    logInfo("[SongDatabaseHelper] Scanned library in ${scanDuration} seconds.")

                    sub.onCompleted()
                }
        )
    }

    private fun clearTables() {
        logInfo("[SongDatabaseHelper] Clearing library...")

        SQLite.delete(Artist::class.java)
        SQLite.delete(Game::class.java)
        SQLite.delete(Track::class.java)
    }

    private fun scanFolder(folder: File, gameMap: HashMap<Long, Game>, artistMap: HashMap<Long, Artist>, sub: Subscriber<FileScanEvent>) {
        val folderPath = folder.absolutePath
        logInfo("[SongDatabaseHelper] Reading files from library folder: ${folderPath}")

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
                                    folderGameId = readMultipleTracks(artistMap, file, filePath, gameMap, sub)
                                    if (folderGameId <= 0) {
                                        sub.onNext(FileScanEvent(FileScanEvent.TYPE_BAD_TRACK, file.name))
                                    }
                                } else {
                                    folderGameId = readSingleTrack(artistMap, file, filePath, gameMap, sub, trackCount)

                                    if (folderGameId > 0) {
                                        trackCount += 1
                                    }
                                }
                            } else if (EXTENSIONS_IMAGES.contains(fileExtension)) {
                                if (folderGameId != null) {
                                    copyImageToInternal(folderGameId, file)
                                } else {
                                    logError("[SongDatabaseHelper] Found image, but game ID unknown: ${filePath}")
                                }
                            }
                        }
                    }
                }
            }

        } else if (!folder.exists()) {
            logError("[SongDatabaseHelper] Folder no longer exists: ${folderPath}")
        } else {
            logError("[SongDatabaseHelper] Folder contains no tracks:  ${folderPath}")
        }
    }

    private fun readSingleTrack(artistMap: HashMap<Long, Artist>, file: File, filePath: String, gameMap: HashMap<Long, Game>, sub: Subscriber<FileScanEvent>, trackNumber: Int): Long {
        val track = readSingleTrackFile(filePath, trackNumber)

        if (track != null) {
            var folderGameId = addTrackToDb(artistMap, gameMap, track)

            sub.onNext(FileScanEvent(FileScanEvent.TYPE_TRACK, file.name))
            return folderGameId
        } else {
            logError("[SongDatabaseHelper] Couldn't read track at ${filePath}")
            sub.onNext(FileScanEvent(FileScanEvent.TYPE_BAD_TRACK, file.name))

            return -1
        }
    }

    private fun readMultipleTracks(artistMap: HashMap<Long, Artist>, file: File, filePath: String, gameMap: HashMap<Long, Game>, sub: Subscriber<FileScanEvent>): Long {
        val tracks = readMultipleTrackFile(filePath) ?: return -1

        var folderGameId = -1L
        tracks.forEach { track ->
            folderGameId = addTrackToDb(artistMap, gameMap, track)
            sub.onNext(FileScanEvent(FileScanEvent.TYPE_TRACK, file.name))
        }

        return folderGameId
    }

    private fun addTrackToDb(artistMap: HashMap<Long, Artist>, gameMap: HashMap<Long, Game>, track: Track): Long {
        var folderGameId = getGameId(track.gameTitle, track.platform, gameMap)
        val artistId = getArtistId(track.artist, artistMap)

        track.gameId = folderGameId
        track.artistId = artistId

        track.insert()
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

        logInfo("[SongDatabaseHelper] Copied image: ${sourcePath} to ${targetFilePath}")

        addLocalImageToGame(gameId, "file://" + targetFilePath)
    }

    companion object {
        val ADD_STATUS_GOOD = 0
        val ADD_STATUS_EXISTS = 1
        val ADD_STATUS_DB_ERROR = 2

        private fun getGameFromDb(id: Long): Game? {
            return SQLite.select()
                    .from(Game::class.java)
                    .where(Game_Table.id.eq(id))
                    .querySingle()
        }

        private fun getArtistFromDb(id: Long): Artist? {
            return SQLite.select()
                    .from(Artist::class.java)
                    .where(Artist_Table.id.eq(id))
                    .querySingle()
        }

        private fun getTrackFromDb(id: Long): Track {
            return SQLite.select()
                    .from(Track::class.java)
                    .where(Track_Table.id.eq(id))
                    .querySingle()
        }

        private fun getArtistId(name: String?, artistMap: HashMap<Long, Artist>): Long {
            // Check if this artist has already been seen during this scan.
            artistMap.keys.forEach {
                val currentArtist = artistMap.get(it)
                if (currentArtist?.name == name) {
                    currentArtist?.id?.let {
                        logVerbose("[SongDatabaseHelper] Found cached artist $name with id ${it}")
                        return it
                    }
                }
            }

            val artist = SQLite.select()
                    .from(Artist::class.java)
                    .where(Artist_Table.name.eq(name))
                    .querySingle()

            artist?.id?.let {
                artistMap.put(it, artist)
                return it
            } ?: let {
                val newArtist = addArtistToDatabase(name ?: "Unknown Artist")
                newArtist.id?.let {
                    return it
                }
            }

            logError("[SongDatabaseHelper] Unable to find artist ID.")
            return -1L
        }

        private fun addArtistToDatabase(name: String): Artist {
            val artist = Artist(name)
            artist.insert()

            return artist
        }

        private fun getGameId(gameTitle: String?, gamePlatform: Long?, gameMap: HashMap<Long, Game>): Long {
            // Check if this game has already been seen during this scan.
            gameMap.keys.forEach {
                val currentGame = gameMap.get(it)
                if (currentGame?.title == gameTitle && currentGame?.platform == gamePlatform) {
                    currentGame?.id?.let {
                        logVerbose("[SongDatabaseHelper] Found cached game $gameTitle with id ${it}")
                        return it
                    }
                }
            }

            val game = SQLite.select()
                    .from(Game::class.java)
                    .where(Game_Table.title.eq(gameTitle))
                    .and(Game_Table.platform.eq(gamePlatform))
                    .querySingle()

            game?.id?.let {
                gameMap.put(it, game)
                return it
            } ?: let {
                val newGame = addGameToDatabase(gameTitle ?: "Unknown Game", gamePlatform ?: -Track.PLATFORM_UNSUPPORTED)
                newGame.id?.let {
                    return it
                }
            }
            logError("[SongDatabaseHelper] Unable to find game ID.")
            return -1L
        }

        private fun addGameToDatabase(gameTitle: String, gamePlatform: Long): Game {
            val game = Game(gameTitle, gamePlatform)
            game.insert()

            return game
        }

        private fun addLocalImageToGame(gameId: Long, artLocal: String) {
            val game = SQLite.update(Game::class.java)
                    .set(Game_Table.artLocal.eq(artLocal))
                    .where(Game_Table.id.eq(gameId))
                    .query()

            if (game != null) {
                logVerbose("[SongDatabaseHelper] Successfully updated game #$gameId.")
            } else {
                logError("[SongDatabaseHelper] Failed to update game #$gameId.")
            }
        }
    }
}
