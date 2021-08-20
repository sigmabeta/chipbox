package net.sigmabeta.chipbox.scanner.real

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.sigmabeta.chipbox.models.state.ScannerEvent
import net.sigmabeta.chipbox.models.state.ScannerState
import net.sigmabeta.chipbox.readers.LENGTH_UNKNOWN_MS
import net.sigmabeta.chipbox.readers.M3uReader
import net.sigmabeta.chipbox.readers.TAG_UNKNOWN
import net.sigmabeta.chipbox.readers.getReaderForExtension
import net.sigmabeta.chipbox.repository.RawGame
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.Scanner
import timber.log.Timber
import java.io.File
import java.util.*
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class RealScanner(
    private val repository: Repository,
    private val context: Context,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Scanner(dispatcher) {
    @OptIn(ExperimentalTime::class)
    override suspend fun CoroutineScope.scan() {
        emitState(ScannerState.Scanning)

        // Delete Chipbox 1.x database and copied images.
        if (findOldDbPath()) {
            clearOldDb()
            clearOldImages()
        }

        var folderProgressUpdates = Progress.EMPTY

        val duration = measureTime {
            val folders = getFolders()

            folderProgressUpdates = folders
                .map { scanFolder(it) }
                .reduce { x, y -> x + y }

            // TODO delete missing tracks, orphaned artists/games

        }
        emitState(
            ScannerState.Complete(
                duration.inWholeSeconds.toInt(),
                folderProgressUpdates.gamesFound,
                folderProgressUpdates.tracksFound,
                folderProgressUpdates.tracksFailed
            )
        )
        emitEvent(ScannerEvent.Unknown)
    }

    private fun getFolders() = listOf(
        File(Environment.getExternalStorageDirectory().path)
    )

    private suspend fun scanFolder(folder: File): Progress {
        val folderPath = folder.absolutePath
        val progress = readWithErrorHandling(folderPath) {
            Timber.i("Reading files from library folder: %s", folderPath)

            // Iterate through every file in the folder.
            val children = folder.listFiles()

            if (children != null) {
                children.sort()

                val folderProgresses = children
                    .filter { it.isDirectory }
                    .filter { !it.isHidden }
                    .filter { it?.listFiles()?.isNotEmpty() ?: false }
                    .map { scanFolder(it) }
                    .fold(Progress.EMPTY) { x, y -> x + y }

                val childrenProgress = children
                    .filter { !it.isDirectory }
                    .filter { !it.isHidden }
                    .let { scanFiles(it) }

                return@readWithErrorHandling folderProgresses + childrenProgress
            } else if (!folder.exists()) {
                Timber.e("Folder does not exist: %s", folderPath)
                return@readWithErrorHandling Progress.ERROR
            }

            return@readWithErrorHandling null
        }

        if (progress != null) {
            return progress
        }

        return Progress.EMPTY
    }

    private suspend fun scanFiles(files: List<File>): Progress {
        if (files.isEmpty()) {
            return Progress.EMPTY
        }

        var imagePath: String? = null
        val rawFileTracks = mutableListOf<RawTrack>()
        val m3uTracks = mutableListOf<RawTrack>()
        for (file in files) {
            val fileExtension = file.extension
            if (fileExtension.isEmpty()) {
                continue
            }

            val progress = readWithErrorHandling(file.path) {
                // Check that the file has an extension we care about before trying to read out of it.
                val reader = getReaderForExtension(fileExtension)
                if (reader != null) {
                    val tracks = reader.readTracksFromFile(it)

                    // TODO These shouldn't return
                    if (tracks == null) {
                        return@readWithErrorHandling Progress(0, 0, 1)
                    }

                    if (tracks.isEmpty()) {
                        return@readWithErrorHandling Progress.EMPTY
                    }

                    rawFileTracks += tracks
                } else if (EXTENSIONS_IMAGES.contains(fileExtension)) {
                    imagePath = getImagePath(file)
                } else if (fileExtension.lowercase(Locale.getDefault()) == "m3u") {
                    val tracks = M3uReader.readTracksFromFile(it)
                    if (tracks != null) {
                        m3uTracks += tracks
                    }
                }
                return@readWithErrorHandling null
            }

            if (progress != null) {
                return progress
            }
        }

        if (rawFileTracks.isNotEmpty()) {
            val reconciledTracks = if (m3uTracks.isNotEmpty()) {
                reconcile(rawFileTracks, m3uTracks)
            } else {
                rawFileTracks
            }

            val gameName = rawFileTracks.first().game

            var unknownTracks = 0
            val checkedTracks = reconciledTracks.map {
                if (it.title == TAG_UNKNOWN) {
                    unknownTracks++
                    it.copy(title = "Unknown Track $unknownTracks")
                } else {
                    it
                }
            }

            val game = RawGame(
                gameName,
                imagePath,
                checkedTracks
            )

            repository.addGame(game)

            // TODO Don't assume every file in this folder is from the same game
            emitEvent(
                ScannerEvent.GameFoundEvent(
                    gameName,
                    rawFileTracks.size,
                    imagePath ?: TAG_UNKNOWN
                )
            )

            return Progress(
                1,
                rawFileTracks.size,
                0
            )
        }

        return Progress.EMPTY
    }

    private suspend inline fun readWithErrorHandling(
        path: String,
        operation: (String) -> Progress?
    ): Progress? {
        return try {
            operation(path)
        } catch (ex: Exception) {
            if (!isFailedAlready()) {
                Timber.e("Error reading $path: ${ex.stackTraceToString()}")
                emitEvent(ScannerEvent.Unknown)
                emitState(ScannerState.Failed(path.substringAfterLast("/")))
            }
            throw ex
        }
    }

    private fun reconcile(rawFileTracks: List<RawTrack>, m3uTracks: List<RawTrack>) =
        rawFileTracks.zip(m3uTracks) { rawTrack, m3uTrack ->
            if (rawTrack.path == m3uTrack.path) {
                RawTrack(
                    rawTrack.path,
                    reconcile(rawTrack.title, m3uTrack.title),
                    reconcile(rawTrack.artist, m3uTrack.artist),
                    reconcile(rawTrack.game, m3uTrack.game),
                    reconcile(m3uTrack.length, rawTrack.length),
                    reconcile(m3uTrack.trackNumber, rawTrack.trackNumber),
                    reconcile(m3uTrack.fade, rawTrack.fade),
                )
            } else {
                rawTrack
            }
        }

    private fun reconcile(priority: String, backup: String): String {
        if (priority != TAG_UNKNOWN) {
            return priority
        }
        return backup
    }

    private fun reconcile(priority: Long, backup: Long): Long {
        if (priority != LENGTH_UNKNOWN_MS) {
            return priority
        }
        return backup
    }

    private fun reconcile(priority: Int, backup: Int): Int {
        if (priority != -1) {
            return priority
        }
        return backup
    }

    private fun reconcile(priority: Boolean, backup: Boolean): Boolean {
        if (!priority) {
            return priority
        }
        return backup
    }

    private fun getImagePath(file: File) = "file://${file.path}"

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

    data class Progress(
        val gamesFound: Int,
        val tracksFound: Int,
        val tracksFailed: Int
    ) {
        infix operator fun plus(other: Progress): Progress {
            return Progress(
                gamesFound + other.gamesFound,
                tracksFound + other.tracksFound,
                tracksFailed + other.tracksFailed,
            )
        }

        companion object {
            val EMPTY = Progress(0, 0, 0)
            val ERROR = Progress(0, 0, 1)
        }
    }

    companion object {
        val EXTENSIONS_IMAGES = setOf("jpg", "png")
    }
}