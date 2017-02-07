package net.sigmabeta.chipbox.model.repository

import android.util.Log
import dagger.Lazy
import net.sigmabeta.chipbox.dagger.module.AppModule
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.events.FileScanEvent
import net.sigmabeta.chipbox.util.*
import org.apache.commons.io.FileUtils
import rx.Observable
import rx.Subscriber
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named

class LibraryScanner @Inject constructor(val repositoryLazy: Lazy<Repository>,
                                         @Named(AppModule.DEP_NAME_APP_STORAGE_DIR) val appStorageDir: String?) {
    lateinit var repository: Repository

    fun scanLibrary(): Observable<FileScanEvent> {
        val observable = Observable.create<FileScanEvent>(
                { sub ->
                    // OnSubscribe.call. it: String
                    repository = repositoryLazy.get()
                    repository.reopen()
                    repository.clearAll()

                    logInfo("[Library] Scanning library...")

                    val startTime = System.currentTimeMillis()

                    val folders = repository.getFoldersSync()

                    folders.forEach { folder ->
                        folder.path?.let {
                            scanFolder(File(it), sub as Subscriber<FileScanEvent>)
                        }
                    }

                    val endTime = System.currentTimeMillis()
                    val scanDuration = (endTime - startTime) / 1000.0f

                    logInfo("[Library] Scanned library in ${scanDuration} seconds.")

                    repository.close()
                    sub.onCompleted()
                }
        )

        return observable
                .throttleFirst(5000, TimeUnit.MILLISECONDS)
    }

    /**
     * Private Methods
     */

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
            var game: Game? = null

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

        Observable.from(tracks)
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
                            sub.onNext(FileScanEvent(FileScanEvent.TYPE_TRACK, file.name))
                        }
                )

        return game
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

        FileUtils.copyFile(sourceFile, targetFile)

        logInfo("[Library] Copied image: ${sourcePath} to ${targetFilePath}")

        val artLocal = "file://" + targetFilePath
        repository.updateGameArt(game, artLocal)
    }
}