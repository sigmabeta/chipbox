package net.sigmabeta.chipbox.model.repository

import android.util.Log
import dagger.Lazy
import net.sigmabeta.chipbox.dagger.module.AppModule
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.events.FileScanEvent
import net.sigmabeta.chipbox.util.*
import org.apache.commons.io.FileUtils
import rx.Observable
import rx.Subscriber
import java.io.File
import java.util.*
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class LibraryScanner @Inject constructor(val repositoryLazy: Lazy<Repository>,
                                         @Named(AppModule.DEP_NAME_APP_STORAGE_DIR) val appStorageDir: String?) {
    lateinit var repository: Repository

    var state = STATE_NOT_SCANNING

    fun scanLibrary(): Observable<FileScanEvent> {
        return Observable.create(
                { sub ->
                    state = STATE_SCANNING
                    // OnSubscribe.call. it: String
                    repository = repositoryLazy.get()
                    repository.reopen()

                    logInfo("[Library] Scanning library...")

                    val startTime = System.currentTimeMillis()

                    val folders = getFolders()

                    folders.forEach { folder ->
                        folder.let {
                            scanFolder(it, sub as Subscriber<FileScanEvent>)
                        }
                    }

                    repository.getTracksManaged().forEach {
                        checkForDeletion(it, sub as Subscriber<FileScanEvent>)
                    }

                    repository.getGamesManaged().forEach {
                        checkForDeletion(it)
                    }

                    repository.getArtistsManaged().forEach {
                        checkForDeletion(it)
                    }

                    val endTime = System.currentTimeMillis()
                    val scanDuration = (endTime - startTime) / 1000.0f

                    logInfo("[Library] Scanned library in ${scanDuration} seconds.")

                    repository.close()
                    state = STATE_NOT_SCANNING
                    sub.onCompleted()
                }
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

    private fun scanFolder(folder: File, sub: Subscriber<FileScanEvent>) {
        // Without this, folder events get backpressure-dropped.
        Thread.sleep(10)

        val folderPath = folder.absolutePath
        logInfo("[Library] Reading files from library folder: ${folderPath}")

        sub.onNext(FileScanEvent(FileScanEvent.TYPE_FOLDER, folder.name))

        var folderGame: Game? = null

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
                                    folderGame = readMultipleTracks(file, filePath, sub)
                                    if (folderGame == null) {
                                        sub.onNext(FileScanEvent(FileScanEvent.TYPE_BAD_TRACK, file.name))
                                    }
                                } else {
                                    folderGame = readSingleTrack(file, filePath, sub, trackCount)

                                    if (folderGame != null) {
                                        trackCount += 1
                                    }
                                }
                            } else if (EXTENSIONS_IMAGES.contains(fileExtension)) {
                                if (folderGame != null) {
                                    copyImageToInternal(folderGame, file)
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

    private fun readSingleTrack(file: File, filePath: String, sub: Subscriber<FileScanEvent>, trackNumber: Int): Game? {
        val track = readSingleTrackFile(filePath, trackNumber)

        if (track != null) {
            var game = checkForExistingTrack(filePath, track, sub)

            if (game != null) return game

            repository.addTrack(track)
                    .toBlocking()
                    .subscribe(
                            {
                                game = it
                            },
                            {
                                logError("[Library] Couldn't add track at ${filePath}: ${Log.getStackTraceString(it)}")
                                sub.onNext(FileScanEvent(FileScanEvent.TYPE_BAD_TRACK, file.name))
                            },
                            {
                                sub.onNext(FileScanEvent(FileScanEvent.TYPE_NEW_TRACK, track.title!!))
                            }
                    )

            return game
        } else {
            logError("[Library] Couldn't read track at ${filePath}")
            sub.onNext(FileScanEvent(FileScanEvent.TYPE_BAD_TRACK, file.name))

            return null
        }
    }

    private fun readMultipleTracks(file: File, filePath: String, sub: Subscriber<FileScanEvent>): Game? {
        var game: Game? = null
        val tracks = readMultipleTrackFile(filePath)

        tracks ?: return game

        val newTracks = ArrayList<Track>(tracks.size)

        tracks.forEach { track ->
            val existingTrackGame = checkForExistingTrack(filePath, track, sub, track.trackNumber)

            if (existingTrackGame == null) {
                newTracks.add(track)
            } else {
                game = existingTrackGame
            }
        }

        Observable.from(newTracks)
                .flatMap { return@flatMap repository.addTrack(it) }
                .toBlocking()
                .subscribe(
                        {
                            game = it
                        },
                        {
                            logError("[Library] Couldn't read multi track file at ${filePath}: ${Log.getStackTraceString(it)}")
                            sub.onNext(FileScanEvent(FileScanEvent.TYPE_BAD_TRACK, file.name))
                        },
                        {
                            sub.onNext(FileScanEvent(FileScanEvent.TYPE_NEW_MULTI_TRACK, file.name, newTracks.size))
                        }
                )

        return game
    }

    private fun checkForExistingTrack(filePath: String, track: Track, sub: Subscriber<FileScanEvent>, trackNumber: Int? = null): Game? {
        // Check if this track modifies one we already had.
        val existingTrack = if (trackNumber == null) {
            repository.getTrackFromPath(filePath)
        } else {
            repository.getTrackFromPath(filePath, trackNumber)
        }

        if (existingTrack != null) {
            // Modify any of the existing track's values we care about, then save.
            if (repository.updateTrack(existingTrack, track)) {
                sub.onNext(FileScanEvent(FileScanEvent.TYPE_UPDATED_TRACK, existingTrack.title!!))
            }

            return existingTrack.game
        }

        // Check if this track is one we already had, but moved.
        val movedTrack = repository.getTrack(track.title!!,
                track.gameTitle ?: RealmRepository.GAME_UNKNOWN,
                track.platform)

        if (movedTrack != null) {
            repository.updateTrack(movedTrack, track)
            sub.onNext(FileScanEvent(FileScanEvent.TYPE_UPDATED_TRACK, movedTrack.title!!))
            return movedTrack.game
        }

        return null
    }

    private fun checkForDeletion(track: Track, sub: Subscriber<FileScanEvent>) {
        if (!File(track.path).exists()) {
            logInfo("Track not found on storage, deleting: ${track.title}")
            sub.onNext(FileScanEvent(FileScanEvent.TYPE_DELETED_TRACK, track.title!!))
            repository.deleteTrack(track)
        }
    }

    private fun checkForDeletion(game: Game) {
        if (game.tracks?.size ?: 0 <= 0) {
            logInfo("No tracks found for game, deleting: ${game.title}")

            game.artLocal?.let {
                val artLocalFile = File(it.substringAfter("file://"))

                if (artLocalFile.exists()) {
                    val parentFile = artLocalFile.parentFile

                    logInfo("Deleting art for game at: $it")
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
            logInfo("No tracks found for artist, deleting: ${artist.name}")
            repository.deleteArtist(artist)
        }
    }

    private fun copyImageToInternal(game: Game, sourceFile: File) {
        val sourcePath = sourceFile.path
        val fileExtension = sourceFile.extension

        val targetFile = getTargetImageFilePath(game.id!!, fileExtension)

        if (targetFile.exists()) {
            if (FileUtils.sizeOf(targetFile) == FileUtils.sizeOf(sourceFile)) {
                logInfo("File ${targetFile.name} has same size as internally stored file. Skipping copy.")
                return
            }
        }

        FileUtils.copyFile(sourceFile, targetFile)

        logInfo("[Library] Copied image: ${sourcePath} to ${targetFile.path}")

        val artLocal = "file://" + targetFile.path
        repository.updateGameArt(game, artLocal)
    }

    private fun getTargetImageFilePath(gameId: String, fileExtension: String): File {
        val targetDirPath = appStorageDir + "/images/" + gameId
        val targetDir = File(targetDirPath)
        targetDir.mkdirs()

        val targetFilePath = targetDirPath + "/local" + fileExtension
        return File(targetFilePath)
    }

    companion object {
        val STATE_NOT_SCANNING = 0
        val STATE_SCANNING = 1
    }
}