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
                        checkForDeletion(it)
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
        val folderPath = folder.absolutePath
        logInfo("[Library] Reading files from library folder: ${folderPath}")

        sub.onNext(FileScanEvent(FileScanEvent.TYPE_FOLDER, folderPath))

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
            var game = checkForExistingTrack(filePath, track)

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
                                sub.onNext(FileScanEvent(FileScanEvent.TYPE_TRACK, file.name))
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
            val existingTrackGame = checkForExistingTrack(filePath, track, track.trackNumber)

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
                            // TODO TYPE_MULTI_TRACK
                            sub.onNext(FileScanEvent(FileScanEvent.TYPE_TRACK, file.name))
                        }
                )

        return game
    }

    private fun checkForExistingTrack(filePath: String, track: Track, trackNumber: Int? = null): Game? {
        // Check if this track modifies one we already had.
        val existingTrack = repository.getTrackFromPath(filePath)

        if (existingTrack != null) {
            // Modify any of the existing track's values we care about, then save.
            repository.updateTrack(existingTrack, track)
            return existingTrack.game
        }

        // Check if this track is one we already had, but moved.
        val movedTrack = repository.getTrack(track.title!!,
                track.gameTitle ?: RealmRepository.GAME_UNKNOWN,
                track.platform)

        if (movedTrack != null) {
            repository.updateTrack(movedTrack, track)
            return movedTrack.game
        }

        return null
    }

    private fun checkForDeletion(track: Track) {
        if (!File(track.path).exists()) {
            repository.deleteTrack(track)
        }
    }

    private fun checkForDeletion(game: Game) {
        if (game.tracks?.size ?: 0 <= 0) {
            repository.deleteGame(game)
        }
    }

    private fun checkForDeletion(artist: Artist) {
        if (artist.tracks?.size ?: 0 <= 0) {
            repository.deleteArtist(artist)
        }
    }

    private fun copyImageToInternal(game: Game, sourceFile: File) {
        val targetDirPath = appStorageDir + "/images/" + game.id
        val targetDir = File(targetDirPath)

        targetDir.mkdirs()

        val sourcePath = sourceFile.path
        val extensionStart = sourcePath.lastIndexOf('.')
        val fileExtension = sourcePath.substring(extensionStart)

        val targetFilePath = targetDirPath + "/local" + fileExtension
        val targetFile = File(targetFilePath)

        if (targetFile.exists()) {
            if (FileUtils.sizeOf(targetFile) == FileUtils.sizeOf(sourceFile)) {
                logInfo("File ${targetFile.name} has same size as internally stored file. Skipping copy.")
                return
            }
        }

        FileUtils.copyFile(sourceFile, targetFile)

        logInfo("[Library] Copied image: ${sourcePath} to ${targetFilePath}")

        val artLocal = "file://" + targetFilePath
        repository.updateGameArt(game, artLocal)
    }

    companion object {
        val STATE_NOT_SCANNING = 0
        val STATE_SCANNING = 1
    }
}