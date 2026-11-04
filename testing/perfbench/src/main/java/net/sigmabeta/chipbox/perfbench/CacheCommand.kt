package net.sigmabeta.chipbox.perfbench

import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import net.sigmabeta.chipbox.abrender.EmulatorBank
import net.sigmabeta.chipbox.abrender.SelectedTrack
import net.sigmabeta.chipbox.abrender.walkCorpus
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.contentsource.LocalFileContentSource
import net.sigmabeta.chipbox.models.ChainFile
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.cache.PcmTrackSource
import net.sigmabeta.chipbox.player.cache.real.RealPcmTrackSourceFactory
import net.sigmabeta.chipbox.player.emulators.Emulator
import net.sigmabeta.chipbox.player.emulators.twosf.TwosfEmulator
import net.sigmabeta.chipbox.readers.PsfReader
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.logging.Hatchet
import okio.FileSystem
import okio.Path.Companion.toOkioPath

private const val NANOS_PER_SECOND = 1_000_000_000.0
private const val MILLIS_PER_SECOND = 1_000L
private const val MAX_PHASE_WALL_SECONDS = 180L
private const val READ_BUFFER_FRAMES = 4096
private const val SHORTS_PER_FRAME = 2
private const val CACHE_HIT_CLASS = "CachedFilePcmSource"
private const val CACHE_MISS_CLASS = "CachingPcmSource"
private const val TRACK_COLUMN_WIDTH = 64

/**
 * The production-cache axis. Unlike `audio`, which drives the raw emulator loop, this command opens
 * every selected track through the real [RealPcmTrackSourceFactory] and measures both halves of
 * playback:
 *
 * - **cold** — a fresh cache directory: staging + `EmulatorPcmSource` construction (native track
 *   load) + render-through into the cache file, read to end-of-track;
 * - **warm** — the same directory after the cold pass sealed it: `Factory.open` must return a
 *   `CachedFilePcmSource`, and repeated opens are pooled to a median wall time and
 *   time-to-first-frame.
 *
 * A counting decorator around the real [TwosfEmulator] records native track loads and teardowns per
 * phase, so the report shows whether a warm hit still pays for emulator construction. That is the
 * measurement ticket 05 (PCM cache) must move; nothing here is a fake backend.
 */
fun runCache(args: CacheArgs) {
    args.outDir.mkdirs()
    val root = args.corpusDir.absoluteFile
    val bank = EmulatorBank()
    val matchesFilter = corpusPathFilter(args.filter)
    val tracks = walkCorpus(
        root = root,
        supported = bank.supportedExtensions,
        extensionFilter = null,
        gameFilter = null,
        everySong = true,
        subsong = 0,
    ).filter { matchesFilter(corpusRelativePath(root, it.path)) }

    if (tracks.isEmpty()) {
        println("No renderable tracks under $root${args.filter?.let { " matching '$it'" } ?: ""}")
        return
    }

    val hatchet = BluntHatchet()
    val librarySource = LocalFileContentSource(
        fileSystem = FileSystem.SYSTEM,
        locationsFile = File(args.outDir, "locations.txt").toOkioPath(),
    )
    val registry = ContentSourceRegistry(setOf(librarySource))
    val counting = CountingEmulator(TwosfEmulator)
    val buffer = ShortArray(READ_BUFFER_FRAMES * SHORTS_PER_FRAME)

    println(
        "cache: corpus=$root seconds=${args.sampleSeconds} warm_repeats=${args.repeat} " +
            "tracks=${tracks.size}",
    )

    val outcomes = tracks.mapIndexed { index, selected ->
        measureTrack(index, selected, root, args, registry, counting, buffer, hatchet)
    }

    val outFile = File(args.outDir, "cache.tsv")
    outFile.writeText(cacheTsv(outcomes))
    printCacheTable(outcomes)
    println("Wrote $outFile")
}

private const val TWOSF_SAMPLE_RATE = 44_100

/** One track's result: a row on success, or the failure that stopped it (never silently dropped). */
private data class CacheOutcome(val path: String, val row: CacheRow?, val error: String?)

/**
 * Cold + warm measurement for one track. A fresh per-track work directory guarantees the cold open
 * is a miss; the sealed `.pcm` then makes every warm open a complete hit.
 */
@Suppress("LongParameterList")
private fun measureTrack(
    index: Int,
    selected: SelectedTrack,
    root: File,
    args: CacheArgs,
    registry: ContentSourceRegistry,
    counting: CountingEmulator,
    buffer: ShortArray,
    hatchet: Hatchet,
): CacheOutcome {
    val relative = corpusRelativePath(root, selected.path)
    val workDir = File(args.outDir, "work/$index").apply { deleteRecursively() }
    val corpusFile = File(selected.path)
    return try {
        val bytes = corpusFile.readBytes()
        val track = buildTrack((index + 1).toLong(), selected, corpusFile, bytes, args.sampleSeconds, hatchet)
        val factory = RealPcmTrackSourceFactory(
            emulators = listOf(counting),
            stagingDir = File(workDir, "staging").toOkioPath(),
            pcmCacheDir = File(workDir, "pcm").toOkioPath(),
            fileSystem = FileSystem.SYSTEM,
            contentSourceRegistry = registry,
            hatchet = hatchet,
        )
        val (cold, coldFrames) = runBlocking { measurePhase(factory, counting, track, bytes, buffer) }
        require(cold.sourceClass == CACHE_MISS_CLASS) {
            "cold open unexpectedly used ${cold.sourceClass} (fresh cache was not fresh)"
        }
        val warm = runBlocking { List(args.repeat) { measurePhase(factory, counting, track, bytes, buffer) } }
        warm.forEach { (phase, _) ->
            require(phase.sourceClass == CACHE_HIT_CLASS) {
                "warm open used ${phase.sourceClass} instead of $CACHE_HIT_CLASS"
            }
        }
        CacheOutcome(
            path = relative,
            row = cacheRow(
                CacheMeasurement(
                    path = relative,
                    frames = coldFrames,
                    sampleRate = TWOSF_SAMPLE_RATE,
                    cold = cold,
                    warm = warm.map { it.first },
                ),
            ),
            error = null,
        )
    } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
        CacheOutcome(relative, row = null, error = t.message ?: t.javaClass.simpleName)
    }
}

/**
 * Builds the production [Track] for a corpus file, resolving 2SF `_lib` siblings into staging
 * chain files exactly as the scanner does. The render budget is [seconds] of audio so cold and
 * warm passes are bounded and comparable across tracks.
 */
private fun buildTrack(
    id: Long,
    selected: SelectedTrack,
    file: File,
    bytes: ByteArray,
    seconds: Int,
    hatchet: Hatchet,
): Track {
    val tagInfo = PsfReader(hatchet).readTagInfo(bytes)
    val chainFiles = tagInfo?.libReferences.orEmpty().mapNotNull { reference ->
        File(file.parentFile, reference)
            .takeIf { it.isFile }
            ?.let { ChainFile(filename = it.name, uri = it.absolutePath) }
    }
    return Track(
        id = id,
        path = file.absolutePath,
        source = "file",
        title = selected.title,
        trackLengthMs = seconds * MILLIS_PER_SECOND,
        trackNumber = 0,
        fadeLengthMs = 0L,
        game = null,
        artists = null,
        chainFiles = chainFiles,
        extension = selected.extension,
        platform = Platform.NDS,
    )
}

/**
 * Opens one source, reads it to end-of-track, and closes it. Time-to-first-frame starts before
 * `open` on purpose: it is the user-visible latency, including everything the factory does before
 * it can hand back a readable source.
 */
private suspend fun measurePhase(
    factory: RealPcmTrackSourceFactory,
    counting: CountingEmulator,
    track: Track,
    bytes: ByteArray,
    buffer: ShortArray,
): Pair<CachePhase, Long> {
    counting.reset()
    val started = System.nanoTime()
    val source = factory.open(track, bytes)
    val (frames, firstFrameNanos) = try {
        readToEnd(source, buffer)
    } finally {
        source.close()
    }
    if (frames == 0L) throw IllegalStateException("no audio produced")
    val wall = (System.nanoTime() - started) / NANOS_PER_SECOND
    val firstFrame = if (firstFrameNanos < 0) Double.NaN else (firstFrameNanos - started) / NANOS_PER_SECOND
    return CachePhase(
        wallSeconds = wall,
        firstFrameSeconds = firstFrame,
        emulatorLoads = counting.loads,
        emulatorTeardowns = counting.teardowns,
        sourceClass = source.javaClass.simpleName,
    ) to frames
}

/**
 * Reads [source] to end-of-track, returning `(framesRead, firstFrameNanos)`. A render-ahead writer
 * may not have produced the frames under the cursor yet; the generator contract is to poll rather
 * than treat one empty read as end-of-track. The deadline bounds a genuinely stalled writer.
 */
private suspend fun readToEnd(source: PcmTrackSource, buffer: ShortArray): Pair<Long, Long> {
    var frames = 0L
    var firstFrameNanos = -1L
    val deadline = System.currentTimeMillis() + MAX_PHASE_WALL_SECONDS * MILLIS_PER_SECOND
    while (!source.isOver) {
        if (System.currentTimeMillis() > deadline) {
            throw IllegalStateException("phase exceeded ${MAX_PHASE_WALL_SECONDS}s wall cap")
        }
        val read = source.readFrames(buffer)
        if (read > 0) {
            if (firstFrameNanos < 0) firstFrameNanos = System.nanoTime()
            frames += read.toLong()
            continue
        }
        source.getLastError()?.let { throw IllegalStateException(it) }
        yield()
    }
    return frames to firstFrameNanos
}

/**
 * Thin counting decorator around the real production backend. It forwards every call to the
 * singleton [TwosfEmulator] and only records native track-load / teardown calls, so the cache
 * command measures the production path rather than a fake emulator.
 */
private class CountingEmulator(private val delegate: Emulator) : Emulator() {
    var loads: Int = 0
        private set

    var teardowns: Int = 0
        private set

    fun reset() {
        loads = 0
        teardowns = 0
    }

    override fun loadNativeLib() = delegate.loadNativeLib()

    override suspend fun ensureNativeLibReady() = delegate.ensureNativeLibReady()

    override fun loadTrackInternal(path: String) {
        loads += 1
        delegate.loadTrackInternal(path)
    }

    override fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int): Int =
        delegate.generateBufferInternal(buffer, framesPerBuffer)

    override fun teardownInternal() {
        teardowns += 1
        delegate.teardownInternal()
    }

    override fun getLastError(): String? = delegate.getLastError()

    override fun getDiagnostics(): String? = delegate.getDiagnostics()

    override fun getSampleRateInternal(): Int = delegate.getSampleRateInternal()

    // Delegated so a fixed-rate backend (TwosfEmulator) actually takes the factory's warm-hit
    // path instead of falling back to native rate negotiation.
    override val knownSampleRate: Int? get() = delegate.knownSampleRate

    override val supportedFileExtensions: List<String> = delegate.supportedFileExtensions

    override fun setTrackNumber(number: Int) = delegate.setTrackNumber(number)
}

private fun cacheTsv(outcomes: List<CacheOutcome>): String = buildString {
    appendLine(
        "path\tframes\tcold_wall_s\tcold_ttff_s\tcold_loads\tcold_teardowns\tcold_source\t" +
            "warm_opens\twarm_wall_median_s\twarm_wall_min_s\twarm_wall_max_s\t" +
            "warm_ttff_median_s\twarm_loads_per_open\twarm_teardowns_per_open\twarm_source\terror",
    )
    for (outcome in outcomes) {
        val row = outcome.row
        if (row == null) {
            appendLine("${outcome.path}\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t${outcome.error.orEmpty()}")
            continue
        }
        appendLine(
            listOf(
                row.path,
                row.frames.toString(),
                format(row.coldWallSeconds),
                format(row.coldFirstFrameSeconds),
                format(row.coldEmulatorLoads),
                format(row.coldEmulatorTeardowns),
                row.coldSourceClass,
                row.warmOpens.toString(),
                format(row.warmWallMedianSeconds),
                format(row.warmWallMinSeconds),
                format(row.warmWallMaxSeconds),
                format(row.warmFirstFrameMedianSeconds),
                format(row.warmEmulatorLoadsPerOpen),
                format(row.warmEmulatorTeardownsPerOpen),
                row.warmSourceClass,
                "",
            ).joinToString("\t"),
        )
    }
}

private fun format(value: Double): String = if (value.isNaN()) "nan" else "%.6f".format(value)

private fun printCacheTable(outcomes: List<CacheOutcome>) {
    val rows = outcomes.mapNotNull { it.row }
    println(
        "%-64s %9s %9s %5s %9s %9s %5s  %s".format(
            "track",
            "cold_s",
            "cold_ttff",
            "loads",
            "warm_s",
            "warm_ttff",
            "loads",
            "warm source",
        ),
    )
    for (row in rows) {
        println(
            "%-64s %9.3f %9.3f %5.0f %9.3f %9.3f %5.0f  %s".format(
                row.path.take(TRACK_COLUMN_WIDTH),
                row.coldWallSeconds,
                row.coldFirstFrameSeconds,
                row.coldEmulatorLoads,
                row.warmWallMedianSeconds,
                row.warmFirstFrameMedianSeconds,
                row.warmEmulatorLoadsPerOpen,
                row.warmSourceClass,
            ),
        )
    }
    outcomes.filter { it.row == null }.forEach { println("${it.path}: ERROR: ${it.error}") }
    if (rows.isNotEmpty()) {
        val coldTotal = rows.sumOf { it.coldWallSeconds }
        val warmTotal = rows.sumOf { it.warmWallMedianSeconds }
        println(
            "aggregate: ${rows.size} tracks  cold=${"%.2f".format(coldTotal)}s  " +
                "warm=${"%.2f".format(warmTotal)}s  " +
                "speedup=${"%.1f".format(coldTotal / warmTotal)}x  " +
                "warm_loads_per_open=${"%.2f".format(rows.sumOf { it.warmEmulatorLoadsPerOpen } / rows.size)}  " +
                "warm_teardowns_per_open=${"%.2f".format(rows.sumOf { it.warmEmulatorTeardownsPerOpen } / rows.size)}",
        )
    }
}
