package net.sigmabeta.chipbox.model.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.raizlabs.android.dbflow.sql.language.SQLite
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Game_Table
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.events.FileScanEvent
import net.sigmabeta.chipbox.model.file.Folder
import net.sigmabeta.chipbox.model.file.Folder_Table
import net.sigmabeta.chipbox.util.*
import org.apache.commons.io.FileUtils
import rx.Observable
import rx.Subscriber
import java.io.File
import java.util.*

val DB_VERSION = 1
val DB_FILENAME = "songs.db"

val TRACK_LENGTH_DEFAULT = 150000L

val COLUMN_DB_ID = 0

val COLUMN_GAME_PLATFORM = 1
val COLUMN_GAME_TITLE = 2
val COLUMN_GAME_DESCRIPTION = 3
val COLUMN_GAME_COMPANY = 4
val COLUMN_GAME_ART_LOCAL = 5
val COLUMN_GAME_ART_WEB = 6

val COLUMN_ARTIST_NAME = 1

val COLUMN_FOLDER_PATH = 1

val COLUMN_TRACK_NUMBER = 1
val COLUMN_TRACK_PATH = 2
val COLUMN_TRACK_TITLE = 3
val COLUMN_TRACK_GAME_ID = 4
val COLUMN_TRACK_GAME_TITLE = 5
val COLUMN_TRACK_GAME_PLATFORM = 6
val COLUMN_TRACK_ARTIST_ID = 7
val COLUMN_TRACK_ARTIST = 8
val COLUMN_TRACK_LENGTH = 9
val COLUMN_TRACK_INTRO_LENGTH = 10
val COLUMN_TRACK_LOOP_LENGTH = 11

val KEY_DB_ID = "_id"

val KEY_GAME_PLATFORM = "platform"
val KEY_GAME_TITLE = "title"
val KEY_GAME_DESCRIPTION = "description"
val KEY_GAME_COMPANY = "company"
val KEY_GAME_ART_LOCAL = "art_local"
val KEY_GAME_ART_WEB = "art_web"

val KEY_ARTIST_NAME = "name"

val KEY_FOLDER_PATH = "path"

val KEY_TRACK_NUMBER = "number"
val KEY_TRACK_PATH = "path"
val KEY_TRACK_TITLE = "title"
val KEY_TRACK_GAME_ID = "game_id"
val KEY_TRACK_GAME_TITLE = "game_title"
val KEY_TRACK_GAME_PLATFORM = "game_platform"
val KEY_TRACK_ARTIST_ID = "artist_id"
val KEY_TRACK_ARTIST = "artist"
val KEY_TRACK_LENGTH = "length"
val KEY_TRACK_INTRO_LENGTH = "intro_length"
val KEY_TRACK_LOOP_LENGTH = "loop_length"

val TABLE_NAME_FOLDERS = "folders"
val TABLE_NAME_GAMES = "games"
val TABLE_NAME_ARTISTS = "artists"
val TABLE_NAME_TRACKS = "tracks"

private val SQL_TYPE_PRIMARY = "INTEGER PRIMARY KEY"
private val SQL_TYPE_INTEGER = "INTEGER"
private val SQL_TYPE_STRING = "TEXT"

private val SQL_CONSTRAINT_UNIQUE = "UNIQUE"

private val SQL_CREATE = "CREATE TABLE"
private val SQL_DELETE = "DROP TABLE IF EXISTS"
private val SQL_FOREIGN = "FOREIGN KEY"
private val SQL_REFERENCES = "REFERENCES"

private val SQL_CREATE_FOLDERS = "${SQL_CREATE} ${TABLE_NAME_FOLDERS} (${KEY_DB_ID} ${SQL_TYPE_PRIMARY}, " +
        "${KEY_FOLDER_PATH} ${SQL_TYPE_STRING} ${SQL_CONSTRAINT_UNIQUE})"

private val SQL_CREATE_GAMES = "${SQL_CREATE} ${TABLE_NAME_GAMES} (${KEY_DB_ID} ${SQL_TYPE_PRIMARY}, " +
        "${KEY_GAME_PLATFORM} ${SQL_TYPE_INTEGER}, " +
        "${KEY_GAME_TITLE} ${SQL_TYPE_STRING}, " +
        "${KEY_GAME_DESCRIPTION} ${SQL_TYPE_STRING}, " +
        "${KEY_GAME_COMPANY} ${SQL_TYPE_STRING}, " +
        "${KEY_GAME_ART_LOCAL} ${SQL_TYPE_STRING}, " +
        "${KEY_GAME_ART_WEB} ${SQL_TYPE_STRING})"

private val SQL_CREATE_ARTISTS = "${SQL_CREATE} ${TABLE_NAME_ARTISTS} (${KEY_DB_ID} ${SQL_TYPE_PRIMARY}, " +
        "${KEY_ARTIST_NAME} ${SQL_TYPE_STRING})"

private val SQL_CREATE_TRACKS = "${SQL_CREATE} ${TABLE_NAME_TRACKS} (${KEY_DB_ID} ${SQL_TYPE_PRIMARY}, " +
        "${KEY_TRACK_NUMBER} ${SQL_TYPE_INTEGER}, " +
        "${KEY_TRACK_PATH} ${SQL_TYPE_STRING}, " +
        "${KEY_TRACK_TITLE} ${SQL_TYPE_STRING}, " +
        "${KEY_TRACK_GAME_ID} ${SQL_TYPE_INTEGER}, " +
        "${KEY_TRACK_GAME_TITLE} ${SQL_TYPE_STRING}, " +
        "${KEY_TRACK_GAME_PLATFORM} ${SQL_TYPE_INTEGER}, " +
        "${KEY_TRACK_ARTIST_ID} ${SQL_TYPE_INTEGER}, " +
        "${KEY_TRACK_ARTIST} ${SQL_TYPE_STRING}, " +
        "${KEY_TRACK_LENGTH} ${SQL_TYPE_INTEGER}, " +
        "${KEY_TRACK_INTRO_LENGTH} ${SQL_TYPE_INTEGER}, " +
        "${KEY_TRACK_LOOP_LENGTH} ${SQL_TYPE_INTEGER}, " +
        "${SQL_FOREIGN}(${KEY_TRACK_GAME_ID}) ${SQL_REFERENCES} ${TABLE_NAME_GAMES}(${KEY_DB_ID}))"

private val SQL_DELETE_GAMES = "${SQL_DELETE} ${TABLE_NAME_GAMES}"
private val SQL_DELETE_ARTIST = "${SQL_DELETE} ${TABLE_NAME_ARTISTS}"
private val SQL_DELETE_TRACKS = "${SQL_DELETE} ${TABLE_NAME_TRACKS}"

class SongDatabaseHelper(val context: Context) : SQLiteOpenHelper(context, DB_FILENAME, null, DB_VERSION) {
    override fun onCreate(database: SQLiteDatabase) {
        logDebug("[SongDatabaseHelper] Creating database...")

        logVerbose("[SongDatabaseHelper] Executing SQL: " + SQL_CREATE_GAMES)
        database.execSQL(SQL_CREATE_GAMES)

        logVerbose("[SongDatabaseHelper] Executing SQL: " + SQL_CREATE_FOLDERS)
        database.execSQL(SQL_CREATE_FOLDERS)

        logVerbose("[SongDatabaseHelper] Executing SQL: " + SQL_CREATE_TRACKS)
        database.execSQL(SQL_CREATE_TRACKS)

        logVerbose("[SongDatabaseHelper] Executing SQL: " + SQL_CREATE_ARTISTS)
        database.execSQL(SQL_CREATE_ARTISTS)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        logInfo("[SongDatabaseHelper] Upgrading database from schema version " + oldVersion + " to " + newVersion)

        logVerbose("[SongDatabaseHelper] Executing SQL: " + SQL_DELETE_GAMES)
        database.execSQL(SQL_DELETE_GAMES)

        logVerbose("[SongDatabaseHelper] Executing SQL: " + SQL_DELETE_ARTIST)
        database.execSQL(SQL_DELETE_ARTIST)

        logVerbose("[SongDatabaseHelper] Executing SQL: " + SQL_DELETE_TRACKS)
        database.execSQL(SQL_DELETE_TRACKS)

        logVerbose("[SongDatabaseHelper] Executing SQL: " + SQL_CREATE_GAMES)
        database.execSQL(SQL_CREATE_GAMES)

        logVerbose("[SongDatabaseHelper] Executing SQL: " + SQL_CREATE_ARTISTS)
        database.execSQL(SQL_CREATE_ARTISTS)

        logVerbose("[SongDatabaseHelper] Executing SQL: " + SQL_CREATE_TRACKS)
        database.execSQL(SQL_CREATE_TRACKS)

        logVerbose("[SongDatabaseHelper] Re-scanning library with new schema.")
        scanLibrary()
    }

    fun addDirectory(path: String): Observable<Int> {
        return Observable.create {

            if (checkIfContained(path)) {
                it.onNext(ADD_STATUS_EXISTS)
                it.onCompleted()
                return@create
            }

            removeContainedEntries(path)

            Folder(path).insert()

            logInfo("[SongDatabaseHelper] Successfully added folder to database.")
            it.onNext(ADD_STATUS_GOOD)

            it.onCompleted()
        }
    }

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
                        return@create
                    }
                }
        )
    }

    fun getArtist(artistId: Long): Observable<Artist> {
        return Observable.create(
                {
                    logInfo("[SongDatabaseHelper] Getting artist #${artistId}...")

                    val whereClause: String?
                    val whereArgs: Array<String>?

                    if (artistId > 0) {
                        whereClause = "${KEY_DB_ID} = ?"
                        whereArgs = arrayOf(artistId.toString())
                    } else {
                        it.onError(Exception("Bad artist ID."))
                        return@create
                    }

                    val database = writableDatabase

                    val resultCursor = database.query(
                            TABLE_NAME_ARTISTS,
                            null,
                            whereClause,
                            whereArgs,
                            null,
                            null,
                            null
                    )

                    if (resultCursor.moveToFirst()) {
                        val artist = getArtistFromCursor(resultCursor)

                        it.onNext(artist)
                        it.onCompleted()
                    } else {
                        it.onError(Exception("Couldn't find artist."))
                    }

                    resultCursor.close()
                    database.close()
                }
        )
    }

    fun getGamesList(platform: Int): Observable<List<Game>> {
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

    fun getGamesForTrackCursor(tracks: ArrayList<Track>): Observable<HashMap<Long, Game>> {
        return Observable.create(
                {
                    logInfo("[SongDatabaseHelper] Getting games for currently displayed tracks...")
                    val startTime = System.currentTimeMillis()

                    val games = HashMap<Long, Game>()

                    for (track in tracks) {
                        val gameId = track.gameId

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

    fun getArtistList(): Observable<ArrayList<Artist>> {
        return Observable.create(
                {
                    logInfo("[SongDatabaseHelper] Reading artist list...")

                    val database = writableDatabase

                    val resultCursor = database.query(
                            TABLE_NAME_ARTISTS,
                            null,
                            null,
                            null,
                            null,
                            null,
                            "${KEY_ARTIST_NAME} ASC"
                    )


                    val artists = ArrayList<Artist>()

                    resultCursor.moveToPosition(-1)
                    while (resultCursor.moveToNext()) {
                        artists.add(getArtistFromCursor(resultCursor))
                    }

                    logVerbose("[SongDatabaseHelper] Found ${resultCursor.count} artists.")
                    it.onNext(artists)

                    resultCursor.close()
                    database.close()
                    it.onCompleted()
                }
        )
    }

    fun getSongList(): Observable<ArrayList<Track>> {
        return Observable.create(
                {
                    logInfo("[SongDatabaseHelper] Reading song list...")

                    val database = writableDatabase

                    val resultCursor = database.query(
                            TABLE_NAME_TRACKS,
                            null,
                            null,
                            null,
                            null,
                            null,
                            "${KEY_TRACK_TITLE} ASC"
                    )

                    val tracks = ArrayList<Track>()

                    resultCursor.moveToPosition(-1)
                    while (resultCursor.moveToNext()) {
                        tracks.add(getTrackFromCursor(resultCursor))
                    }

                    logVerbose("[SongDatabaseHelper] Found ${resultCursor.count} tracks.")

                    it.onNext(tracks)
                    it.onCompleted()

                    resultCursor.close()
                    database.close()
                }
        )
    }

    fun getSongListForArtist(artist: Long): Observable<ArrayList<Track>> {
        return Observable.create(
                {
                    logInfo("[SongDatabaseHelper] Reading song list...")
                    val whereClause = "${KEY_TRACK_ARTIST_ID} = ?"
                    val whereArgs = arrayOf(artist.toString())

                    val database = writableDatabase

                    val resultCursor = database.query(
                            TABLE_NAME_TRACKS,
                            null,
                            whereClause,
                            whereArgs,
                            null,
                            null,
                            "${KEY_TRACK_GAME_ID} ASC"
                    )

                    val tracks = ArrayList<Track>()

                    resultCursor.moveToPosition(-1)
                    while (resultCursor.moveToNext()) {
                        tracks.add(getTrackFromCursor(resultCursor))
                    }

                    logVerbose("[SongDatabaseHelper] Found ${resultCursor.count} tracks.")

                    it.onNext(tracks)
                    it.onCompleted()

                    resultCursor.close()
                    database.close()
                }
        )
    }

    fun getSongListForGame(game: Long): Observable<ArrayList<Track>> {
        return Observable.create(
                {
                    logInfo("[SongDatabaseHelper] Reading song list...")
                    val whereClause = "${KEY_TRACK_GAME_ID} = ?"
                    val whereArgs = arrayOf(game.toString())

                    val database = writableDatabase

                    val resultCursor = database.query(
                            TABLE_NAME_TRACKS,
                            null,
                            whereClause,
                            whereArgs,
                            null,
                            null,
                            "${KEY_TRACK_NUMBER} ASC"
                    )

                    val tracks = ArrayList<Track>()

                    resultCursor.moveToPosition(-1)
                    while (resultCursor.moveToNext()) {
                        tracks.add(getTrackFromCursor(resultCursor))
                    }

                    logVerbose("[SongDatabaseHelper] Found ${resultCursor.count} tracks.")

                    it.onNext(tracks)
                    it.onCompleted()

                    resultCursor.close()
                    database.close()
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

                    val database = writableDatabase
                    val folders = getFolders()

                    val gameMap = HashMap<Long, Game>()
                    val artistMap = HashMap<Long, Artist>()

                    folders.forEach { folder ->
                        folder.path?.let {
                            scanFolder(File(it), gameMap, artistMap, database, sub as Subscriber<FileScanEvent>)
                        }
                    }

                    database.close()

                    val endTime = System.currentTimeMillis()
                    val scanDuration = (endTime - startTime) / 1000.0f

                    logInfo("[SongDatabaseHelper] Scanned library in ${scanDuration} seconds.")

                    sub.onCompleted()
                }
        )
    }


    private fun checkIfContained(newPath: String): Boolean {
        val folders = getFolders()

        folders.forEach {
            it.path?.let { oldPath ->
                if (newPath.contains(oldPath)) {
                    logError("[SongDatabaseHelper] New folder $newPath is contained by a previously added folder: $oldPath")
                    return true
                }
            }
        }

        return false
    }

    private fun getFolders(): List<Folder> {
        return SQLite.select()
                .from(Folder::class.java)
                .queryList()
    }

    private fun removeContainedEntries(newPath: String) {
        val folders = getFolders()

        // Remove any folders from the DB that are contained by the new folder.
        val idsToRemove = mutableListOf<Long>()
        folders.forEach { oldFolder ->
            oldFolder.path?.let { oldPath ->
                if (oldPath.contains(newPath)) {
                    logInfo("[SongDatabaseHelper] New folder contains a previously added folder: $oldPath")

                    oldFolder.id?.let {
                        idsToRemove.add(it)
                    }
                }
            }
        }

        if (idsToRemove.isNotEmpty()) {
            val idsString = idsToRemove.joinToString()
            logInfo("[SongDatabaseHelper] Deleting folders with ids: $idsString")

            SQLite.delete().from(Folder::class.java)
                    .where(Folder_Table.id.`in`(idsToRemove))
                    .query()
        }
    }

    private fun clearTables() {
        logInfo("[SongDatabaseHelper] Clearing library...")

        val database = writableDatabase

        database.delete(TABLE_NAME_GAMES, null, null)
        database.delete(TABLE_NAME_ARTISTS, null, null)
        database.delete(TABLE_NAME_TRACKS, null, null)

        database.close()
    }

    private fun scanFolder(folder: File, gameMap: HashMap<Long, Game>, artistMap: HashMap<Long, Artist>, database: SQLiteDatabase, sub: Subscriber<FileScanEvent>) {
        database.beginTransaction()

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
                        scanFolder(file, gameMap, artistMap, database, sub)
                    } else {
                        val filePath = file.absolutePath
                        val fileExtension = getFileExtension(filePath)

                        if (fileExtension != null) {
                            // Check that the file has an extension we care about before trying to read out of it.
                            if (EXTENSIONS_MUSIC.contains(fileExtension)) {
                                if (EXTENSIONS_MULTI_TRACK.contains(fileExtension)) {
                                    folderGameId = readMultipleTracks(artistMap, database, file, filePath, gameMap, sub)
                                    if (folderGameId <= 0) {
                                        sub.onNext(FileScanEvent(FileScanEvent.TYPE_BAD_TRACK, file.name))
                                    }
                                } else {
                                    folderGameId = readSingleTrack(artistMap, database, file, filePath, gameMap, sub, trackCount)

                                    if (folderGameId > 0) {
                                        trackCount += 1
                                    }
                                }
                            } else if (EXTENSIONS_IMAGES.contains(fileExtension)) {
                                if (folderGameId != null) {
                                    copyImageToInternal(folderGameId, file, database)
                                } else {
                                    logError("[SongDatabaseHelper] Found image, but game ID unknown: ${filePath}")
                                }
                            }
                        }
                    }
                }
            }

            database.setTransactionSuccessful()

        } else if (!folder.exists()) {
            logError("[SongDatabaseHelper] Folder no longer exists: ${folderPath}")
        } else {
            logError("[SongDatabaseHelper] Folder contains no tracks:  ${folderPath}")
        }

        database.endTransaction()
    }

    private fun readSingleTrack(artistMap: HashMap<Long, Artist>, database: SQLiteDatabase, file: File, filePath: String, gameMap: HashMap<Long, Game>, sub: Subscriber<FileScanEvent>, trackNumber: Int): Long {
        val track = readSingleTrackFile(filePath, trackNumber)

        if (track != null) {
            var folderGameId = getGameId(track.gameTitle, track.platform, gameMap, database)
            val artistId = getArtistId(track.artist, artistMap, database)
            val values = getContentValuesFromTrack(track, folderGameId, artistId)

            addTrackToDatabase(values, database)

            sub.onNext(FileScanEvent(FileScanEvent.TYPE_TRACK, file.name))

            return folderGameId
        } else {
            logError("[SongDatabaseHelper] Couldn't read track at ${filePath}")
            sub.onNext(FileScanEvent(FileScanEvent.TYPE_BAD_TRACK, file.name))

            return -1
        }
    }

    private fun readMultipleTracks(artistMap: HashMap<Long, Artist>, database: SQLiteDatabase, file: File, filePath: String, gameMap: HashMap<Long, Game>, sub: Subscriber<FileScanEvent>): Long {
        val tracks = readMultipleTrackFile(filePath) ?: return -1

        var folderGameId = -1L
        tracks.forEach { track ->
            folderGameId = getGameId(track.gameTitle, track.platform, gameMap, database)
            val artistId = getArtistId(track.artist, artistMap, database)
            val values = getContentValuesFromTrack(track, folderGameId, artistId)

            addTrackToDatabase(values, database)

            sub.onNext(FileScanEvent(FileScanEvent.TYPE_TRACK, file.name))
        }

        return folderGameId
    }

    private fun addTrackToDatabase(values: ContentValues, database: SQLiteDatabase) {
        // Try to update an existing track first.
        val rowsMatched = database.update(TABLE_NAME_TRACKS, // Which table to update.
                values, // The values to fill the row with.
                "${KEY_DB_ID} = ?", // The WHERE clause used to find the right row.
                arrayOf(values.getAsString(KEY_DB_ID)))

        // TODO Does the above call make any sense? How would it know what ID to use?

        // If update fails, insert a new game instead.
        if (rowsMatched == 0) {
            database.insert(TABLE_NAME_TRACKS,
                    null,
                    values)

            logInfo("[SongDatabaseHelper] Added track: " + values.getAsString(KEY_TRACK_TITLE))
        } else {
            logInfo("[SongDatabaseHelper] Updated track: " + values.getAsString(KEY_TRACK_TITLE))
        }
    }

    private fun copyImageToInternal(gameId: Long, sourceFile: File, database: SQLiteDatabase) {
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

        fun getPlaybackQueueFromCursor(cursor: Cursor): ArrayList<Track> {
            val queue = ArrayList<Track>()

            cursor.moveToPosition(-1)
            while (cursor.moveToNext()) {
                queue.add(getTrackFromCursor(cursor))
            }

            return queue
        }

        private fun getGameFromDb(id: Long): Game? {
            return SQLite.select()
                    .from(Game::class.java)
                    .where(Game_Table.id.eq(id))
                    .querySingle()
        }

        private fun getTrackFromCursor(cursor: Cursor): Track {
            return Track(
                    cursor.getLong(COLUMN_DB_ID),
                    cursor.getInt(COLUMN_TRACK_NUMBER),
                    cursor.getString(COLUMN_TRACK_PATH),
                    cursor.getString(COLUMN_TRACK_TITLE),
                    cursor.getLong(COLUMN_TRACK_GAME_ID),
                    cursor.getString(COLUMN_TRACK_GAME_TITLE),
                    cursor.getInt(COLUMN_TRACK_GAME_PLATFORM),
                    cursor.getString(COLUMN_TRACK_ARTIST),
                    cursor.getLong(COLUMN_TRACK_LENGTH),
                    cursor.getLong(COLUMN_TRACK_INTRO_LENGTH),
                    cursor.getLong(COLUMN_TRACK_LOOP_LENGTH)
            )
        }

        private fun getArtistFromCursor(cursor: Cursor): Artist {
            return Artist(
                    cursor.getLong(COLUMN_DB_ID),
                    cursor.getString(COLUMN_ARTIST_NAME)
            )
        }

        private fun getContentValuesFromTrack(track: Track, gameId: Long, artistId: Long): ContentValues {
            val values = ContentValues()

            var trackLength = 0L

            if (track.trackLength > 0) {
                trackLength = track.trackLength
            }

            if (track.loopLength > 0) {
                trackLength = track.introLength + (track.loopLength * 2)
            }

            if (trackLength == 0L) {
                trackLength = TRACK_LENGTH_DEFAULT
            }

            values.put(KEY_TRACK_NUMBER, track.trackNumber)
            values.put(KEY_TRACK_PATH, track.path)
            values.put(KEY_TRACK_TITLE, track.title)
            values.put(KEY_TRACK_GAME_ID, gameId)
            values.put(KEY_TRACK_GAME_TITLE, track.gameTitle)
            values.put(KEY_TRACK_GAME_PLATFORM, track.platform)
            values.put(KEY_TRACK_ARTIST_ID, artistId)
            values.put(KEY_TRACK_ARTIST, track.artist)
            values.put(KEY_TRACK_LENGTH, trackLength)
            values.put(KEY_TRACK_INTRO_LENGTH, track.introLength)
            values.put(KEY_TRACK_LOOP_LENGTH, track.loopLength)

            return values
        }

        private fun getArtistId(name: String, artistMap: HashMap<Long, Artist>, database: SQLiteDatabase): Long {
            var artist: Artist? = null

            // Check if this artist has already been seen during this scan.
            artistMap.keys.forEach {
                val currentArtist = artistMap.get(it)
                if (currentArtist?.name == name) {
                    logVerbose("[SongDatabaseHelper] Found cached artist $name with id ${currentArtist?.id}")
                    artist = currentArtist
                    return@forEach
                }
            }

            // If it has, we already know its ID.
            if (artist != null) {
                return artist!!.id
            }

            val resultCursor = database.query(TABLE_NAME_ARTISTS,
                    null, // Get all columns.
                    "${KEY_ARTIST_NAME} = ?", // Get only the artist matching this name.
                    arrayOf(name), // The name to match.
                    null, // No grouping.
                    null, // No havingBy.
                    null) // Should only be one result, so order is irrelevant.

            val artistToReturn = when (resultCursor.count) {
                0 -> {
                    resultCursor.close()
                    val newArtist = addArtistToDatabase(name, database)

                    // Assign to artistToReturn
                    newArtist
                }
                1 -> {
                    logDebug("[SongDatabaseHelper] Found database entry for artist ${artist}.")
                    resultCursor.moveToFirst()
                    val id = resultCursor.getLong(COLUMN_DB_ID)

                    resultCursor.close()

                    val artistFromDatabase = Artist(id, name)

                    // Assign to artistToReturn
                    artistFromDatabase
                }
                else -> {
                    resultCursor.close()
                    logError("[SongDatabaseHelper] Found multiple database entries with artist ${artist}")
                    return -1
                }
            }

            artistMap.put(artistToReturn.id, artistToReturn)
            return artistToReturn.id
        }

        private fun addArtistToDatabase(name: String, database: SQLiteDatabase): Artist {
            val values = ContentValues()

            values.put(KEY_ARTIST_NAME, name)

            val artistId = database.insert(TABLE_NAME_ARTISTS,
                    null,
                    values)

            if (artistId < 0) {
                // TODO Do more than just report an error.
                logError("[SongDatabaseHelper] Unable to add artist ${name} to database.")
                throw UnsupportedOperationException("Unable to add artist ${name} to database.")
            } else {
                logInfo("[SongDatabaseHelper] Added artist #${artistId}: ${name} to database.")
            }

            return Artist(artistId, name)
        }

        private fun getGameId(gameTitle: String, gamePlatform: Int, gameMap: HashMap<Long, Game>, database: SQLiteDatabase): Long {
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
                val newGame = addGameToDatabase(gameTitle, gamePlatform)
                newGame.id?.let {
                    return it
                }
            }
            logError("[SongDatabaseHelper] Unable to find game ID.")
            return -1L
        }

        private fun addGameToDatabase(gameTitle: String, gamePlatform: Int): Game {
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
