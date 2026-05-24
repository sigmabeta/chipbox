package net.sigmabeta.chipbox.scanner.real

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import net.sigmabeta.chipbox.contentsource.LibraryFileInfo
import net.sigmabeta.chipbox.contentsource.LibrarySource
import net.sigmabeta.chipbox.models.ChainFile
import net.sigmabeta.chipbox.perf.trace
import net.sigmabeta.chipbox.perf.traceAsync
import net.sigmabeta.chipbox.readers.EXTENSION_M3U
import net.sigmabeta.chipbox.readers.LENGTH_UNKNOWN_MS
import net.sigmabeta.chipbox.readers.PsfTagInfo
import net.sigmabeta.chipbox.readers.Readers
import net.sigmabeta.chipbox.readers.isPsfFamily
import net.sigmabeta.chipbox.repository.FolderSnapshot
import net.sigmabeta.chipbox.repository.GameWriteResult
import net.sigmabeta.chipbox.repository.RawGame
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.chipbox.scanner.state.ScannerEvent
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.sage.logging.Hatchet
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

/**
 * The production scanner — drives [readers] over every file [librarySource] exposes,
 * applies PSF `_lib` chain resolution and m3u overlays, and persists one `RawGame` per
 * source folder to [repository]. Talks to the library through the platform-neutral
 * [LibrarySource] / [LibraryFileInfo] interfaces so both the Android (SAF) and JVM
 * (`java.io.File`) targets share this same code.
 */
class RealScanner(
    private val repository: Repository,
    private val librarySource: LibrarySource,
    private val readers: Readers,
    private val hatchet: Hatchet,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Scanner(dispatcher) {

    // Scan work spans coroutine suspension points, so a trace section's begin and end can land on
    // different IO threads — that rules out the thread-bound synchronous `trace` for those spans.
    // We use `traceAsync` instead, which needs a cookie that's unique across any same-named
    // sections open at once; this monotonic counter supplies one. The purely in-memory parse calls
    // (no suspension) stay on `trace`.
    private val traceCookies = AtomicInteger(0)

    private fun nextCookie() = traceCookies.incrementAndGet()

    // How many folders to scan concurrently. Concurrency is a big win — the work is dominated by
    // SAF binder-IPC latency — but it's bounded above by the externalstorage provider's binder pool
    // (IO) and by core count (parsing). A little headroom over the core count covers the latency-
    // bound reads; the clamp keeps weak devices from thrashing and many-core devices from
    // oversubscribing the SAF provider, which is the real ceiling regardless of cores. Tracing on a
    // 6-core device put the sweet spot at 8 (= 6 + 2); 16 oversubscribed both and ran ~11% slower.
    private val scanParallelism = (Runtime.getRuntime().availableProcessors() + CORE_HEADROOM)
        .coerceIn(MIN_PARALLELISM, MAX_PARALLELISM)

    @OptIn(ExperimentalTime::class)
    override suspend fun CoroutineScope.scan() = traceAsync(TRACE_SCAN, nextCookie()) {
        hatchet.i("Starting library scan.")
        val scanStart = TimeSource.Monotonic.markNow()
        emitState(ScannerState.Scanning())

        val locations = librarySource.locations.value
        if (locations.isEmpty()) {
            hatchet.w("No library locations configured — aborting scan.")
            emitState(ScannerState.Complete(0, 0, 0, 0))
            emitEvent(ScannerEvent.Unknown)
            return@traceAsync
        }

        hatchet.d(
            "Scanning ${locations.size} library location(s): " +
                locations.joinToString { it.identifier }
        )

        val files = traceAsync(TRACE_SCAN_FILES, nextCookie()) {
            librarySource.scanFiles().toList()
        }
        val groups = files.groupBy { it.parentFolderId }
        hatchet.d("Found ${files.size} file(s) across ${groups.size} folder(s).")
        // Pre-scan snapshot of stored folder signatures, so a folder whose files are unchanged
        // (same paths, sizes, mtimes) is skipped without any reads or parsing.
        val snapshot = repository.folderSnapshots()
        // Folder ids that produced a game this scan — the "kept" set for the prune sweep below.
        val seenFolderKeys = ConcurrentHashMap.newKeySet<String>()
        // Running totals, so we emit live Scanning progress as each folder completes.
        val gamesFound = AtomicInteger(0)
        val tracksFound = AtomicInteger(0)
        val tracksFailed = AtomicInteger(0)
        // Folders are independent (each scanGroup has its own metadata + tag caches), and most
        // of a scan is spent blocked on SAF binder IPC for file reads. Process several folders
        // at once so those waits overlap and parsing spreads across cores — bounded so we don't
        // swamp the disk dispatcher or hold too many file buffers in memory at once.
        val semaphore = Semaphore(scanParallelism)
        val total = coroutineScope {
            groups.map { (folderId, group) ->
                async {
                    semaphore.withPermit { scanFolder(folderId, group, snapshot, seenFolderKeys) }
                        .also { progress ->
                            emitState(
                                ScannerState.Scanning(
                                    scanStart.elapsedNow().inWholeSeconds.toInt(),
                                    gamesFound.addAndGet(progress.gamesFound),
                                    tracksFound.addAndGet(progress.tracksFound),
                                    tracksFailed.addAndGet(progress.tracksFailed),
                                )
                            )
                        }
                }
            }.awaitAll().fold(Progress.EMPTY, Progress::plus)
        }
        // Reconcile deletions: drop games whose folder yielded nothing this scan (cascading
        // their tracks/joins) and any artists left without tracks. Each removed game becomes an event.
        val removed = traceAsync(TRACE_PRUNE, nextCookie()) { repository.pruneGames(seenFolderKeys) }
        removed.forEach { emitEvent(ScannerEvent.GameRemoved(it)) }

        val duration = scanStart.elapsedNow()
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

    // Scans one folder — unless its file signature matches the stored one, in which case it's
    // skipped entirely (no reads, parsing, or DB writes) and just recorded as kept. Either way a
    // folder that has/keeps a game is added to [seenFolderKeys] for the prune sweep.
    private suspend fun scanFolder(
        folderId: String,
        group: List<LibraryFileInfo>,
        snapshot: Map<String, FolderSnapshot>,
        seenFolderKeys: MutableSet<String>,
    ): Progress {
        val sorted = group.sortedBy { it.name }
        val signature = folderSignature(sorted)
        val known = snapshot[folderId]
        if (known != null && known.signature == signature) {
            hatchet.d("Folder $folderId unchanged — skipping ${group.size} file(s).")
            seenFolderKeys.add(folderId)
            return Progress(1, known.trackCount, 0)
        }
        hatchet.d("Scanning folder $folderId (${group.size} file(s)).")
        return traceAsync(TRACE_SCAN_GROUP, nextCookie()) {
            scanGroup(folderId, sorted, signature)
        }.also { if (it.gamesFound > 0) seenFolderKeys.add(folderId) }
    }

    // [signature] is the folder's precomputed hash (see [folderSignature]); the caller already
    // decided this folder changed, and it gets persisted on the game for the next scan's skip check.
    private suspend fun scanGroup(
        folderKey: String,
        files: List<LibraryFileInfo>,
        signature: String,
    ): Progress {
        var imagePath: String? = null
        val tracksByFilename = LinkedHashMap<String, MutableList<RawTrack>>()
        val m3uFiles = mutableListOf<LibraryFileInfo>()
        var failed = 0

        val byFilename: Map<String, LibraryFileInfo> = files.associateBy { it.name.lowercase() }
        // plain HashMap: scanGroup is sequential suspend, no concurrent access
        val tagInfoCache = HashMap<String, PsfTagInfo?>()

        for (file in files) {
            val ext = file.extension
            if (ext.isEmpty()) continue

            if (EXTENSIONS_IMAGES.contains(ext)) {
                if (imagePath == null) imagePath = file.identifier
                continue
            }

            if (ext == EXTENSION_M3U) {
                m3uFiles += file
                continue
            }

            if (isPsfFamily(ext)) {
//                hatchet.d("Reading ${file.name} (PSF family).")
                val track = readWithErrorHandling(file) {
                    val bytes = traceAsync(TRACE_OPEN_BYTES, nextCookie()) {
                        librarySource.openBytes(file.identifier)
                    }
                    if (bytes == null) {
                        hatchet.w("Failed to read ${file.name}: could not open ${file.identifier}.")
                        return@readWithErrorHandling null
                    }
                    val tagInfo = trace(TRACE_READ_PSF) { readers.psf.readTagInfo(bytes) }
                        ?: return@readWithErrorHandling null
                    tagInfoCache[file.name.lowercase()] = tagInfo
                    val chain = mutableListOf<ChainFile>()
                    val chainTags = traceAsync(TRACE_RESOLVE_CHAIN, nextCookie()) {
                        resolvePsfChain(
                            tagInfo,
                            byFilename,
                            tagInfoCache,
                            mutableSetOf(file.name.lowercase()),
                            0,
                            chain,
                        )
                    }
                    val mergedTags = chainTags + tagInfo.tags
                    readers.psf.buildRawTrack(mergedTags, file.identifier, tagInfo.platform)
                        .copy(source = librarySource.sourceId, chainFiles = chain, extension = ext)
                }
                when (track) {
                    null -> failed++

                    else -> {
//                        hatchet.d("${file.name} yielded 1 track.")
                        tracksByFilename[file.name] = mutableListOf(track)
                    }
                }
                continue
            }

            val reader = readers.forExtension(ext) ?: run {
                hatchet.v("No reader for extension '$ext' — skipping ${file.name}.")
                continue
            }

//            hatchet.d("Reading ${file.name}.")
            val tracks = readWithErrorHandling(file) {
                val bytes = traceAsync(TRACE_OPEN_BYTES, nextCookie()) {
                    librarySource.openBytes(file.identifier)
                } ?: return@readWithErrorHandling null
                trace("$TRACE_READ_PREFIX$ext") { reader.readTracksFromFile(bytes, file.identifier) }
                    ?.map { it.copy(source = librarySource.sourceId, extension = ext) }
            }

            when {
                tracks == null -> {
                    hatchet.w("Failed to read ${file.name}.")
                    failed++
                }

                tracks.isEmpty() -> hatchet.d("${file.name} yielded no tracks.")

                else -> {
//                    hatchet.d("${file.name} yielded ${tracks.size} track(s).")
                    tracksByFilename[file.name] = tracks.toMutableList()
                }
            }
        }

        for (m3uFile in m3uFiles) {
            hatchet.d("Applying m3u overlay from ${m3uFile.name}.")
            val bytes = traceAsync(TRACE_OPEN_BYTES, nextCookie()) {
                librarySource.openBytes(m3uFile.identifier)
            }
            if (bytes == null) {
                hatchet.w("Failed to open ${m3uFile.name}.")
                continue
            }
            for (entry in trace(TRACE_PARSE_M3U) { readers.m3u.parse(bytes) }) {
                val siblings = tracksByFilename[entry.filename] ?: run {
                    hatchet.v("m3u references '${entry.filename}' which was not scanned — skipping.")
                    continue
                }
                val index = siblings.indexOfFirst { it.trackNumber == entry.trackNumber }
                if (index < 0) {
                    hatchet.v(
                        "m3u references track ${entry.trackNumber} in " +
                            "'${entry.filename}' which doesn't exist — skipping."
                    )
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
                it.copy(title = "Track $unknown")
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
        val outcome = traceAsync(TRACE_UPSERT_GAME, nextCookie()) {
            repository.upsertGame(RawGame(gameName, imagePath, folderKey, signature, checked))
        }
        when (outcome.result) {
            GameWriteResult.ADDED ->
                emitEvent(ScannerEvent.GameFoundEvent(outcome.gameId, gameName, rawTracks.size, imagePath))

            GameWriteResult.UPDATED ->
                emitEvent(ScannerEvent.GameUpdated(outcome.gameId, gameName, rawTracks.size, imagePath))

            // Re-scanned but byte-identical (e.g. a touched mtime): no user-visible change.
            GameWriteResult.UNCHANGED -> Unit
        }
        return Progress(1, rawTracks.size, failed)
    }

    // A content-free fingerprint of a folder: every file's identifier, size and mtime, sorted and
    // hashed. Two scans yield the same value iff the folder's files are unchanged, which lets the
    // scan skip re-reading it. Computed from discovery metadata alone — no file is opened.
    private fun folderSignature(files: List<LibraryFileInfo>): String {
        val joined = files
            .sortedBy { it.identifier }
            .joinToString("\n") { "${it.identifier}|${it.sizeBytes}|${it.lastModifiedMs}" }
        return MessageDigest.getInstance("SHA-256")
            .digest(joined.encodeToByteArray())
            .joinToString("") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }
    }

    private suspend fun resolvePsfChain(
        tagInfo: PsfTagInfo,
        byFilename: Map<String, LibraryFileInfo>,
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
                val parsed = librarySource.openBytes(libFile.identifier)
                    ?.let { readers.psf.readTagInfo(it) }
                tagInfoCache[refLower] = parsed
                parsed
            }
            if (chainOut.none { it.filename.equals(libFile.name, ignoreCase = true) }) {
                chainOut += ChainFile(libFile.name, libFile.identifier)
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
        file: LibraryFileInfo,
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
        private const val BYTE_MASK = 0xFF

        // Bounds for [scanParallelism]; see the comment there. MAX is the traced ceiling (going
        // past it oversubscribes the SAF provider); MIN keeps low-core devices usefully concurrent
        // since the work is IO-latency-bound, not compute-bound.
        private const val CORE_HEADROOM = 2
        private const val MIN_PARALLELISM = 4
        private const val MAX_PARALLELISM = 8

        // Perfetto trace section labels. Spans crossing suspension (IO, DB, flow collection) use
        // `traceAsync`; the in-memory parse spans use the thread-bound `trace`. `TRACE_READ_PREFIX`
        // is completed with the file extension, e.g. "Scanner:readTracks:nsf".
        private const val TRACE_SCAN = "Scanner:scan"
        private const val TRACE_SCAN_FILES = "Scanner:scanFiles"
        private const val TRACE_SCAN_GROUP = "Scanner:scanGroup"
        private const val TRACE_OPEN_BYTES = "Scanner:openBytes"
        private const val TRACE_READ_PSF = "Scanner:readTags:psf"
        private const val TRACE_READ_PREFIX = "Scanner:readTracks:"
        private const val TRACE_RESOLVE_CHAIN = "Scanner:resolvePsfChain"
        private const val TRACE_PARSE_M3U = "Scanner:parseM3u"
        private const val TRACE_UPSERT_GAME = "Scanner:upsertGame"
        private const val TRACE_PRUNE = "Scanner:pruneGames"
    }
}
