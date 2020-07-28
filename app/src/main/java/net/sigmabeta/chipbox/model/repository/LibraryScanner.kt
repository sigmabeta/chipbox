package net.sigmabeta.chipbox.model.repository

import android.content.Context
import android.util.Log
import dagger.Lazy
import io.reactivex.BackpressureStrategy
import io.reactivex.Emitter
import io.reactivex.Flowable
import io.reactivex.FlowableEmitter
import net.sigmabeta.chipbox.backend.Scanner
import net.sigmabeta.chipbox.dagger.module.AppModule
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.events.FileScanEvent
import net.sigmabeta.chipbox.model.repository.RealmRepository.Companion.GAME_UNKNOWN
import net.sigmabeta.chipbox.util.EXTENSIONS_IMAGES
import net.sigmabeta.chipbox.util.EXTENSIONS_MULTI_TRACK
import net.sigmabeta.chipbox.util.readMultipleTrackFile
import net.sigmabeta.chipbox.util.readSingleTrackFile
import timber.log.Timber
import java.io.File
import java.util.*
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class LibraryScanner @Inject constructor(val repositoryLazy: Lazy<Repository>,
                                         val context: Context,
                                         @Named(AppModule.DEP_NAME_APP_STORAGE_DIR) val appStorageDir: String?) {
    lateinit var repository: Repository

    var state = STATE_NOT_SCANNING

    fun scanLibrary(): Flowable<FileScanEvent> {
        return Flowable.create (
                { emitter: FlowableEmitter<FileScanEvent> ->
                    if (findOldDbPath()) {
                        clearOldDb()
                        clearOldImages()
                    }

                    state = STATE_SCANNING
                    // OnSubscribe.call. it: String
                    repository = repositoryLazy.get()
                    repository.reopen()

                    Timber.i("Scanning library...")

                    val startTime = System.currentTimeMillis()

                    val folders = getFolders()

                    folders.forEach { folder ->
                        folder.let {
                            scanFolder(it, emitter )
                        }
                    }

                    repository.getTracksManaged().forEach {
                        checkForDeletion(it, emitter)
                    }

                    repository.getGamesManaged().forEach {
                        checkForDeletion(it)
                    }

                    repository.getArtistsManaged().forEach {
                        checkForDeletion(it)
                    }

                    val endTime = System.currentTimeMillis()
                    val scanDuration = (endTime - startTime) / 1000.0f

                    Timber.i("Scanned library in %s seconds.", scanDuration)

                    repository.close()
                    state = STATE_NOT_SCANNING
                    emitter.onComplete()
                }, BackpressureStrategy.LATEST
        )
    }

    /**
     * Private Methods
     */

    private fun getFolders(): List<File> {
        val storageFolderFiles = File("/storage").listFiles()
        val selfPrimaryFiles = File("/storage/self/primary").listFiles()
        val emulatedLegacyFiles = File("/storage/emulated/legacy").listFiles()

        val mergedArray = storageFolderFiles + selfPrimaryFiles.orEmpty() + emulatedLegacyFiles.orEmpty()

        return mergedArray.toList()
    }

    private fun scanFolder(folder: File, emitter: Emitter<FileScanEvent>) {
        // Without this, folder events get backpressure-dropped.
        Thread.sleep(10)

        val folderPath = folder.absolutePath
        Timber.i("Reading files from library folder: %s", folderPath)

        emitter.onNext(FileScanEvent(FileScanEvent.TYPE_FOLDER, folder.name))

        var folderGame: Game? = null

        // Iterate through every file in the folder.
        val children = folder.listFiles()

        if (children != null) {
            Arrays.sort(children)

            var trackCount = 1

            for (file in children) {
                if (!file.isHidden) {
                    if (file.isDirectory) {
                        scanFolder(file, emitter)
                    } else {
                        val filePath = file.absolutePath
                        val fileExtension = file.extension

                        if (fileExtension.isNotEmpty()) {
                            // Check that the file has an extension we care about before trying to read out of it.
                            if (Scanner.EXTENSIONS_MUSIC.contains(fileExtension)) {
                                if (EXTENSIONS_MULTI_TRACK.contains(fileExtension)) {
                                    folderGame = readMultipleTracks(file, filePath, emitter)
                                    if (folderGame == null) {
                                        emitter.onNext(FileScanEvent(FileScanEvent.TYPE_BAD_TRACK, file.name))
                                    }
                                } else {
                                    folderGame = readSingleTrack(file, filePath, emitter, trackCount)

                                    if (folderGame != null) {
                                        trackCount += 1
                                    }
                                }
                            } else if (EXTENSIONS_IMAGES.contains(fileExtension)) {
                                if (folderGame != null) {
                                    addImageToGame(folderGame, file)
                                } else {
                                    Timber.e("Found image, but game ID unknown: %s", filePath)
                                }
                            }
                        }
                    }
                }
            }

        } else if (!folder.exists()) {
            Timber.e("Folder no longer exists: %s", folderPath)
        } else {
            Timber.e("Folder contains no tracks: %s", folderPath)
        }
    }

    private fun readSingleTrack(file: File, filePath: String, emitter: Emitter<FileScanEvent>, trackNumber: Int): Game? {
        val track = readSingleTrackFile(file, trackNumber)

        if (track != null) {
            if (track.title.isNullOrEmpty()) {
                track.title = "${track.gameTitle ?: GAME_UNKNOWN} Track $trackNumber"
            }

            var game = checkForExistingTrack(filePath, track, emitter)

            if (game != null) return game

            repository.addTrack(track)
                    .subscribe(
                            {
                                game = it
                            },
                            {
                                Timber.e("Couldn't add track at %s: %s", filePath, Log.getStackTraceString(it))
                                emitter.onNext(FileScanEvent(FileScanEvent.TYPE_BAD_TRACK, file.name))
                            },
                            {
                                emitter.onNext(FileScanEvent(FileScanEvent.TYPE_NEW_TRACK, track.title!!))
                            }
                    )

            return game
        } else {
            Timber.e("Couldn't read track at %s", filePath)
            emitter.onNext(FileScanEvent(FileScanEvent.TYPE_BAD_TRACK, file.name))

            return null
        }
    }

    private fun readMultipleTracks(file: File, filePath: String, emitter: Emitter<FileScanEvent>): Game? {
        var game: Game? = null
        val tracks = readMultipleTrackFile(file)

        tracks ?: return game

        val newTracks = ArrayList<Track>(tracks.size)

        tracks.forEach { track ->
            val existingTrackGame = checkForExistingTrack(filePath, track, emitter, track.trackNumber)

            if (existingTrackGame == null) {
                newTracks.add(track)
            } else {
                game = existingTrackGame
            }
        }

        Flowable.fromIterable(newTracks)
                .flatMap { return@flatMap repository.addTrack(it) }
                .subscribe(
                        {
                            game = it
                        },
                        {
                            Timber.e("Couldn't read multi track file at %s: %s", filePath, Log.getStackTraceString(it))
                            emitter.onNext(FileScanEvent(FileScanEvent.TYPE_BAD_TRACK, file.name))
                        },
                        {
                            emitter.onNext(FileScanEvent(FileScanEvent.TYPE_NEW_MULTI_TRACK, file.name, newTracks.size))
                        }
                )

        return game
    }

    private fun checkForExistingTrack(filePath: String, track: Track, emitter: Emitter<FileScanEvent>, trackNumber: Int? = null): Game? {
        // Check if this track modifies one we already had.
        val existingTrack = if (trackNumber == null) {
            repository.getTrackFromPath(filePath)
        } else {
            repository.getTrackFromPath(filePath, trackNumber)
        }

        if (existingTrack != null) {
            // Modify any of the existing track's values we care about, then save.
            if (repository.updateTrack(existingTrack, track)) {
                emitter.onNext(FileScanEvent(FileScanEvent.TYPE_UPDATED_TRACK, existingTrack.title!!))
            }

            return existingTrack.game
        }

        // Check if this track is one we already had, but moved.
        val movedTrack = repository.getTrack(track.title!!,
                track.gameTitle ?: RealmRepository.GAME_UNKNOWN,
                track.platformName ?: RealmRepository.PLATFORM_UNKNOWN)

        if (movedTrack != null) {
            repository.updateTrack(movedTrack, track)
            emitter.onNext(FileScanEvent(FileScanEvent.TYPE_UPDATED_TRACK, movedTrack.title!!))
            return movedTrack.game
        }

        return null
    }

    private fun checkForDeletion(track: Track, emitter: Emitter<FileScanEvent>) {
        if (!File(track.path).exists()) {
            Timber.i("Track not found on storage, deleting: %s", track.title)
            emitter.onNext(FileScanEvent(FileScanEvent.TYPE_DELETED_TRACK, track.title!!))
            repository.deleteTrack(track)
        }
    }

    private fun checkForDeletion(game: Game) {
        if (game.tracks?.size ?: 0 <= 0) {
            Timber.i("No tracks found for game, deleting: %s", game.title)

            game.artLocal?.let {
                val artLocalFile = File(it.substringAfter("file://"))

                if (artLocalFile.exists()) {
                    val parentFile = artLocalFile.parentFile

                    Timber.i("Deleting art for game at: %s", it)
                    artLocalFile.delete()

                    if (parentFile.listFiles()?.isEmpty() ?: false) {
                        parentFile.delete()
                    }
                }
            }
            repository.deleteGame(game)
        }
    }

    private fun checkForDeletion(artist: Artist) {
        if (artist.tracks?.size ?: 0 <= 0) {
            Timber.i("No tracks found for artist, deleting: %s", artist.name)
            repository.deleteArtist(artist)
        }
    }

    private fun addImageToGame(game: Game, sourceFile: File) {
        val sourcePath = sourceFile.path

        val artLocal = "file://$sourcePath"
        repository.updateGameArt(game, artLocal)
    }

    private fun findOldDbPath(): Boolean {
        val dbPath = context.getDatabasePath("chipbox.db")

        Timber.w("Directory path %s exists %b", dbPath.path, dbPath.exists())

        return dbPath.exists()
    }

    private fun clearOldDb() {
        val dbPath = context.getDatabasePath("chipbox.db")
        dbPath.deleteRecursively()
    }

    private fun clearOldImages() {
        val directory = context.getExternalFilesDir(null)
        val imagesFolder = File(directory, "/images")

        imagesFolder.deleteRecursively()
    }

    companion object {
        val STATE_NOT_SCANNING = 0
        val STATE_SCANNING = 1
    }
}