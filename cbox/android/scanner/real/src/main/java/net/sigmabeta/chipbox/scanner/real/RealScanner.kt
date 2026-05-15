package net.sigmabeta.chipbox.scanner.real

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import net.sigmabeta.chipbox.contentsource.AndroidFileContentSource
import net.sigmabeta.chipbox.contentsource.LibraryFile
import net.sigmabeta.chipbox.models.ChainFile
import net.sigmabeta.chipbox.scanner.state.ScannerEvent
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.chipbox.readers.EXTENSION_M3U
import net.sigmabeta.chipbox.readers.LENGTH_UNKNOWN_MS
import net.sigmabeta.chipbox.readers.PsfTagInfo
import net.sigmabeta.chipbox.readers.Readers
import net.sigmabeta.chipbox.readers.isPsfFamily
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
    private val readers: Readers,
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

        val byFilename: Map<String, LibraryFile> = files.associateBy { it.name.lowercase() }
        // plain HashMap: scanGroup is sequential suspend, no concurrent access
        val tagInfoCache = HashMap<String, PsfTagInfo?>()

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

            if (isPsfFamily(ext)) {
                hatchet.d("Reading ${file.name} (PSF family).")
                val track = readWithErrorHandling(file) {
                    val bytes = contentSource.openInputStream(file.uri)?.use { it.readBytes() }
                    if (bytes == null) {
                        hatchet.w("Failed to read ${file.name}: could not open input stream for ${file.uri}.")
                        return@readWithErrorHandling null
                    }
                    val tagInfo = readers.psf.readTagInfo(bytes)
                        ?: return@readWithErrorHandling null
                    tagInfoCache[file.name.lowercase()] = tagInfo
                    val chain = mutableListOf<ChainFile>()
                    val chainTags = resolvePsfChain(
                        tagInfo, byFilename, tagInfoCache,
                        mutableSetOf(file.name.lowercase()), 0, chain,
                    )
                    val mergedTags = chainTags + tagInfo.tags
                    readers.psf.buildRawTrack(mergedTags, file.uri.toString())
                        .copy(source = contentSource.sourceId, chainFiles = chain, extension = ext)
                }
                when (track) {
                    null -> failed++
                    else -> {
                        hatchet.d("${file.name} yielded 1 track.")
                        tracksByFilename[file.name] = mutableListOf(track)
                    }
                }
                continue
            }

            val reader = readers.forExtension(ext) ?: run {
                hatchet.v("No reader for extension '$ext' — skipping ${file.name}.")
                continue
            }

            hatchet.d("Reading ${file.name}.")
            val tracks = readWithErrorHandling(file) {
                val bytes = contentSource.openInputStream(file.uri)?.use { it.readBytes() }
                    ?: return@readWithErrorHandling null
                reader.readTracksFromFile(bytes, file.uri.toString())
                    ?.map { it.copy(source = contentSource.sourceId, extension = ext) }
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
            for (entry in readers.m3u.parse(bytes)) {
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
                    fadeLengthMs = entry.fadeLengthMs,
                )
            }
        }

        val rawTracks = tracksByFilename.values.flatten()

        if (rawTracks.isEmpty()) {
            return if (failed > 0) Progress(0, 0, failed) else Progress.EMPTY
        }

        var unknown = 0
        val checked = rawTracks.map {
            val titled = if (it.title == TAG_UNKNOWN) {
                unknown++
                it.copy(title = "Unknown Track $unknown")
            } else {
                it
            }
            // Reader (or m3u overlay) couldn't determine a length — fall back to a sensible
            // default so the track is still seekable and the now-playing UI can render a
            // progress bar. Accept any non-positive value to absorb reader bugs that emit 0.
            if (titled.length <= 0L) {
                titled.copy(length = DEFAULT_LENGTH_MS)
            } else {
                titled
            }
        }

        val gameName = rawTracks.first().game
        hatchet.i("Adding game \"$gameName\" with ${checked.size} track(s).")
        repository.addGame(RawGame(gameName, imagePath, checked))
        emitEvent(ScannerEvent.GameFoundEvent(gameName, rawTracks.size, imagePath.orUnknown()))
        return Progress(1, rawTracks.size, failed)
    }

    private suspend fun resolvePsfChain(
        tagInfo: PsfTagInfo,
        byFilename: Map<String, LibraryFile>,
        tagInfoCache: HashMap<String, PsfTagInfo?>,
        visited: MutableSet<String>,
        depth: Int,
        chainOut: MutableList<ChainFile>,
    ): Map<String, String> {
        val merged = mutableMapOf<String, String>()
        for (libRef in tagInfo.libReferences) {
            val refLower = libRef.lowercase()
            if (refLower in visited) {
                hatchet.w("PSF _lib cycle at '$libRef' — skipping.")
                continue
            }
            if (depth >= MAX_LIB_DEPTH) {
                hatchet.w("PSF _lib chain depth limit exceeded at '$libRef' — skipping.")
                continue
            }
            val libFile = byFilename[refLower] ?: run {
                hatchet.v("PSF _lib '$libRef' not found in folder — skipping.")
                continue
            }
            val libTagInfo = if (tagInfoCache.containsKey(refLower)) {
                tagInfoCache[refLower]
            } else {
                val parsed = contentSource.openInputStream(libFile.uri)?.use { it.readBytes() }
                    ?.let { readers.psf.readTagInfo(it) }
                tagInfoCache[refLower] = parsed
                parsed
            }
            if (chainOut.none { it.filename.equals(libFile.name, ignoreCase = true) }) {
                chainOut += ChainFile(libFile.name, libFile.uri.toString())
            }
            if (libTagInfo != null) {
                visited.add(refLower)
                val libChain = resolvePsfChain(libTagInfo, byFilename, tagInfoCache, visited, depth + 1, chainOut)
                visited.remove(refLower)
                merged.putAll(libChain)
                merged.putAll(libTagInfo.tags)
            }
        }
        return merged
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
        private const val MAX_LIB_DEPTH = 8
        private const val DEFAULT_LENGTH_MS = 2L * 60 * 1000 + 30 * 1000
    }
}
