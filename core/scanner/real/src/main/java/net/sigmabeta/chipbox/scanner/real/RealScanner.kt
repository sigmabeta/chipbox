package net.sigmabeta.chipbox.scanner.real

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import net.sigmabeta.chipbox.contentsource.AndroidFileContentSource
import net.sigmabeta.chipbox.contentsource.LibraryFile
import net.sigmabeta.chipbox.models.state.ScannerEvent
import net.sigmabeta.chipbox.models.state.ScannerState
import net.sigmabeta.chipbox.readers.EXTENSION_M3U
import net.sigmabeta.chipbox.readers.LENGTH_UNKNOWN_MS
import net.sigmabeta.chipbox.readers.M3uReader
import net.sigmabeta.chipbox.readers.getReaderForExtension
import net.sigmabeta.chipbox.readers.orUnknown
import net.sigmabeta.chipbox.repository.RawGame
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.sage.logging.Hatchet
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class RealScanner(
    private val repository: Repository,
    private val contentSource: AndroidFileContentSource,
    private val hatchet: Hatchet,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Scanner(dispatcher) {

    @OptIn(ExperimentalTime::class)
    override suspend fun CoroutineScope.scan() {
        hatchet.i("Starting library scan.")
        emitState(ScannerState.Scanning)

        val locations = contentSource.libraryLocations.value
        if (locations.isEmpty()) {
            hatchet.w("No library locations configured — aborting scan.")
            emitState(ScannerState.Complete(0, 0, 0, 0))
            emitEvent(ScannerEvent.Unknown)
            return
        }

        hatchet.d("Scanning ${locations.size} library location(s): ${locations.map { it.uri }}")

        var total = Progress.EMPTY
        val duration = measureTime {
            val files = contentSource.scanLibraryFiles().toList()
            val groups = files.groupBy { it.parentDocumentId }
            hatchet.d("Found ${files.size} file(s) across ${groups.size} folder(s).")
            for ((folderId, group) in groups) {
                hatchet.d("Scanning folder $folderId (${group.size} file(s)).")
                total += scanGroup(group.sortedBy { it.name })
            }
        }

        hatchet.i(
            "Scan complete in ${duration.inWholeSeconds}s — " +
                "${total.gamesFound} game(s), ${total.tracksFound} track(s), ${total.tracksFailed} failure(s)."
        )
        emitState(
            ScannerState.Complete(
                duration.inWholeSeconds.toInt(),
                total.gamesFound,
                total.tracksFound,
                total.tracksFailed,
            )
        )
        emitEvent(ScannerEvent.Unknown)
    }

    private suspend fun scanGroup(files: List<LibraryFile>): Progress {
        var imagePath: String? = null
        val tracksByFilename = LinkedHashMap<String, MutableList<RawTrack>>()
        val m3uFiles = mutableListOf<LibraryFile>()
        var failed = 0

        for (file in files) {
            val ext = file.extension
            if (ext.isEmpty()) continue

            if (EXTENSIONS_IMAGES.contains(ext)) {
                if (imagePath == null) imagePath = file.uri.toString()
                continue
            }

            if (ext == EXTENSION_M3U) {
                m3uFiles += file
                continue
            }

            val reader = getReaderForExtension(ext) ?: run {
                hatchet.v("No reader for extension '$ext' — skipping ${file.name}.")
                continue
            }

            hatchet.d("Reading ${file.name}.")
            val tracks = readWithErrorHandling(file) {
                val bytes = contentSource.openInputStream(file.uri)?.use { it.readBytes() }
                    ?: return@readWithErrorHandling null
                reader.readTracksFromFile(bytes, file.uri.toString())
                    ?.map { it.copy(source = contentSource.sourceId) }
            }

            when {
                tracks == null -> {
                    hatchet.w("Failed to read ${file.name}.")
                    failed++
                }
                tracks.isEmpty() -> hatchet.d("${file.name} yielded no tracks.")
                else -> {
                    hatchet.d("${file.name} yielded ${tracks.size} track(s).")
                    tracksByFilename[file.name] = tracks.toMutableList()
                }
            }
        }

        for (m3uFile in m3uFiles) {
            hatchet.d("Applying m3u overlay from ${m3uFile.name}.")
            val bytes = contentSource.openInputStream(m3uFile.uri)?.use { it.readBytes() }
            if (bytes == null) {
                hatchet.w("Failed to open ${m3uFile.name}.")
                continue
            }
            for (entry in M3uReader.parse(bytes)) {
                val siblings = tracksByFilename[entry.filename] ?: run {
                    hatchet.v("m3u references '${entry.filename}' which was not scanned — skipping.")
                    continue
                }
                val index = siblings.indexOfFirst { it.trackNumber == entry.trackNumber }
                if (index < 0) {
                    hatchet.v("m3u references track ${entry.trackNumber} in '${entry.filename}' which doesn't exist — skipping.")
                    continue
                }
                siblings[index] = siblings[index].copy(
                    title = entry.title,
                    artist = entry.artist ?: siblings[index].artist,
                    game = entry.game ?: siblings[index].game,
                    length = if (entry.lengthMs != LENGTH_UNKNOWN_MS) entry.lengthMs else siblings[index].length,
                    fade = entry.hasFade,
                )
            }
        }

        val rawTracks = tracksByFilename.values.flatten()

        if (rawTracks.isEmpty()) {
            return if (failed > 0) Progress(0, 0, failed) else Progress.EMPTY
        }

        var unknown = 0
        val checked = rawTracks.map {
            if (it.title == TAG_UNKNOWN) {
                unknown++
                it.copy(title = "Unknown Track $unknown")
            } else {
                it
            }
        }

        val gameName = rawTracks.first().game
        hatchet.i("Adding game \"$gameName\" with ${checked.size} track(s).")
        checked.forEach { hatchet.v("  Track ${it.trackNumber}: \"${it.title}\" (${it.length}ms)") }
        repository.addGame(RawGame(gameName, imagePath, checked))
        emitEvent(ScannerEvent.GameFoundEvent(gameName, rawTracks.size, imagePath.orUnknown()))
        return Progress(1, rawTracks.size, failed)
    }

    private suspend inline fun <T> readWithErrorHandling(
        file: LibraryFile,
        op: () -> T?,
    ): T? = try {
        op()
    } catch (ex: Exception) {
        if (!isFailedAlready()) {
            hatchet.e("Error reading ${file.name}: ${ex.stackTraceToString()}")
            emitEvent(ScannerEvent.Unknown)
            emitState(ScannerState.Failed(file.name))
        }
        null
    }

    data class Progress(
        val gamesFound: Int,
        val tracksFound: Int,
        val tracksFailed: Int,
    ) {
        operator fun plus(other: Progress) = Progress(
            gamesFound + other.gamesFound,
            tracksFound + other.tracksFound,
            tracksFailed + other.tracksFailed,
        )

        companion object {
            val EMPTY = Progress(0, 0, 0)
        }
    }

    companion object {
        val EXTENSIONS_IMAGES = setOf("jpg", "png")
        private const val TAG_UNKNOWN = "Unknown"
    }
}
