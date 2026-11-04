package net.sigmabeta.chipbox.perfbench

import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import net.sigmabeta.chipbox.abrender.CountingEmulator
import net.sigmabeta.chipbox.abrender.SelectedTrack
import net.sigmabeta.chipbox.abrender.productionTrack
import net.sigmabeta.chipbox.abrender.walkCorpus
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.contentsource.LocalFileContentSource
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.cache.PcmTrackSource
import net.sigmabeta.chipbox.player.cache.real.RealPcmTrackSourceFactory
import net.sigmabeta.chipbox.player.cache.real.UncachedPcmTrackSourceFactory
import net.sigmabeta.chipbox.player.emulators.twosf.TwosfEmulator
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
 * every selected track through the real cache factories and measures playback:
 *
 * - **cached** (default) — the [RealPcmTrackSourceFactory] path: **cold** opens a fresh cache
 *   directory (staging + native track load + render-through into the cache file, read to
 *   end-of-track), **warm** re-opens that sealed directory (`Factory.open` must return
 *   `CachedFilePcmSource`; repeats are pooled to a median wall time and time-to-first-frame).
 * - **uncached** (`--uncached`) — the [UncachedPcmTrackSourceFactory] path, i.e. what playback
 *   does when no cache is used: every open stages, loads the native core, and drains a live
 *   `EmulatorPcmSource`. It is the regression axis for "the cache work did not make the uncached
 *   path slower" — the same per-open drain, measured repeatedly.
 *
 * A counting decorator around the real [TwosfEmulator] records native track loads and teardowns per
 * phase, so the report shows whether a warm hit still pays for emulator construction. Nothing here
 * is a fake backend. Only 2SF files are selected: the factory is built with the 2SF backend alone,
 * so walking any other supported extension would only produce per-track errors.
 */
fun runCache(args: CacheArgs) {
    args.outDir.mkdirs()
    val root = args.corpusDir.absoluteFile
    val matchesFilter = corpusPathFilter(args.filter)
    val tracks = walkCorpus(
        root = root,
        // The factory holds only TwosfEmulator, so only its extensions are drivable here.
        supported = TwosfEmulator.supportedFileExtensions.toSet(),
        extensionFilter = null,
        gameFilter = null,
        everySong = true,
        subsong = 0,
    ).filter { matchesFilter(corpusRelativePath(root, it.path)) }

    if (tracks.isEmpty()) {
        println("No renderable 2SF tracks under $root${args.filter?.let { " matching '$it'" } ?: ""}")
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
        "cache: corpus=$root mode=${if (args.uncached) "uncached" else "cached"} " +
            "seconds=${args.sampleSeconds} repeat=${args.repeat} tracks=${tracks.size}",
    )

    if (args.uncached) {
        val results = tracks.mapIndexed { index, selected ->
            measureUncachedTrack(index, selected, args, registry, counting, buffer, hatchet)
        }
        val outFile = File(args.outDir, "uncached.tsv")
        outFile.writeText(uncachedTsv(results))
        printUncachedTable(results)
        println("Wrote $outFile")
        return
    }

    val results = tracks.mapIndexed { index, selected ->
        measureCachedTrack(index, selected, args, registry, counting, buffer, hatchet)
    }
    val outFile = File(args.outDir, "cache.tsv")
    outFile.writeText(cacheTsv(results))
    printCacheTable(results)
    println("Wrote $outFile")
}

/** One track's result: a measured row, or the failure that stopped it (never silently dropped). */
private sealed interface TrackResult {
    val path: String
}

private data class CachedResult(override val path: String, val row: CacheRow) : TrackResult

private data class UncachedResult(override val path: String, val row: UncachedRow) : TrackResult

private data class FailedResult(override val path: String, val error: String) : TrackResult

/**
 * Cold + warm measurement for one track. A fresh per-track work directory guarantees the cold open
 * is a miss; the sealed `.pcm` then makes every warm open a complete hit.
 */
@Suppress("LongParameterList")
private fun measureCachedTrack(
    index: Int,
    selected: SelectedTrack,
    args: CacheArgs,
    registry: ContentSourceRegistry,
    counting: CountingEmulator,
    buffer: ShortArray,
    hatchet: Hatchet,
): TrackResult {
    val relative = corpusRelativePath(args.corpusDir.absoluteFile, selected.path)
    val workDir = File(args.outDir, "work/$index").apply { deleteRecursively() }
    return try {
        val (track, bytes) = productionTrack((index + 1).toLong(), selected, args.sampleSeconds, hatchet)
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
        CachedResult(
            path = relative,
            row = cacheRow(
                CacheMeasurement(
                    path = relative,
                    frames = coldFrames,
                    sampleRate = cold.sampleRate,
                    cold = cold,
                    warm = warm.map { it.first },
                ),
            ),
        )
    } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
        FailedResult(relative, t.message ?: t.javaClass.simpleName)
    }
}

/**
 * Repeated uncached opens for one track. Every open stages, loads the native core, and drains a live
 * emulator source; the median (and min/max) per-open wall time is the regression number. A fresh
 * work directory per track keeps staging state out of the other tracks' measurements.
 */
@Suppress("LongParameterList")
private fun measureUncachedTrack(
    index: Int,
    selected: SelectedTrack,
    args: CacheArgs,
    registry: ContentSourceRegistry,
    counting: CountingEmulator,
    buffer: ShortArray,
    hatchet: Hatchet,
): TrackResult {
    val relative = corpusRelativePath(args.corpusDir.absoluteFile, selected.path)
    val workDir = File(args.outDir, "work/$index").apply { deleteRecursively() }
    return try {
        val (track, bytes) = productionTrack((index + 1).toLong(), selected, args.sampleSeconds, hatchet)
        val factory = UncachedPcmTrackSourceFactory(
            emulators = listOf(counting),
            stagingDir = File(workDir, "staging").toOkioPath(),
            fileSystem = FileSystem.SYSTEM,
            contentSourceRegistry = registry,
            hatchet = hatchet,
        )
        val phases = runBlocking {
            List(args.repeat) { measurePhase(factory, counting, track, bytes, buffer) }
        }
        phases.forEach { (phase, frames) ->
            require(phase.sourceClass == "EmulatorPcmSource") {
                "uncached open used ${phase.sourceClass} instead of EmulatorPcmSource"
            }
            require(frames > 0) { "uncached open produced no audio" }
        }
        UncachedResult(relative, uncachedRow(relative, phases.map { it.first }, phases.first().second))
    } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
        FailedResult(relative, t.message ?: t.javaClass.simpleName)
    }
}

/**
 * Opens one source, reads it to end-of-track, and closes it. Time-to-first-frame starts before
 * `open` on purpose: it is the user-visible latency, including everything the factory does before
 * it can hand back a readable source.
 */
private suspend fun measurePhase(
    factory: PcmTrackSource.Factory,
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
        sampleRate = source.sampleRate,
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

private fun cacheTsv(results: List<TrackResult>): String = buildString {
    appendLine(
        "path\tframes\tsample_rate\tcold_wall_s\tcold_ttff_s\tcold_loads\tcold_teardowns\tcold_source\t" +
            "warm_opens\twarm_wall_median_s\twarm_wall_min_s\twarm_wall_max_s\t" +
            "warm_ttff_median_s\twarm_loads_per_open\twarm_teardowns_per_open\twarm_source\terror",
    )
    for (result in results) {
        when (result) {
            is CachedResult -> with(result.row) {
                appendLine(
                    listOf(
                        path,
                        frames.toString(),
                        sampleRate.toString(),
                        format(coldWallSeconds),
                        format(coldFirstFrameSeconds),
                        format(coldEmulatorLoads),
                        format(coldEmulatorTeardowns),
                        coldSourceClass,
                        warmOpens.toString(),
                        format(warmWallMedianSeconds),
                        format(warmWallMinSeconds),
                        format(warmWallMaxSeconds),
                        format(warmFirstFrameMedianSeconds),
                        format(warmEmulatorLoadsPerOpen),
                        format(warmEmulatorTeardownsPerOpen),
                        warmSourceClass,
                        "",
                    ).joinToString("\t"),
                )
            }

            is FailedResult -> appendLine("${result.path}\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t${result.error}")

            // The run is one mode at a time, so this writer never sees the other mode's rows.
            is UncachedResult -> error("uncached row in a cached run")
        }
    }
}

private fun uncachedTsv(results: List<TrackResult>): String = buildString {
    appendLine(
        "path\tframes\tsample_rate\topens\twall_median_s\twall_min_s\twall_max_s\t" +
            "ttff_median_s\tloads_per_open\tteardowns_per_open\tsource\terror",
    )
    for (result in results) {
        when (result) {
            is UncachedResult -> with(result.row) {
                appendLine(
                    listOf(
                        path,
                        frames.toString(),
                        sampleRate.toString(),
                        opens.toString(),
                        format(wallMedianSeconds),
                        format(wallMinSeconds),
                        format(wallMaxSeconds),
                        format(firstFrameMedianSeconds),
                        format(loadsPerOpen),
                        format(teardownsPerOpen),
                        sourceClass,
                        "",
                    ).joinToString("\t"),
                )
            }

            is FailedResult -> appendLine("${result.path}\t\t\t\t\t\t\t\t\t\t\t${result.error}")

            is CachedResult -> error("cached row in an uncached run")
        }
    }
}

private fun format(value: Double): String = if (value.isNaN()) "nan" else "%.6f".format(value)

private fun printCacheTable(results: List<TrackResult>) {
    val rows = results.filterIsInstance<CachedResult>().map { it.row }
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
    printFailures(results)
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

private fun printUncachedTable(results: List<TrackResult>) {
    val rows = results.filterIsInstance<UncachedResult>().map { it.row }
    println(
        "%-64s %9s %9s %9s %5s  %s".format(
            "track",
            "median_s",
            "min_s",
            "ttff",
            "loads",
            "source",
        ),
    )
    for (row in rows) {
        println(
            "%-64s %9.3f %9.3f %9.3f %5.0f  %s".format(
                row.path.take(TRACK_COLUMN_WIDTH),
                row.wallMedianSeconds,
                row.wallMinSeconds,
                row.firstFrameMedianSeconds,
                row.loadsPerOpen,
                row.sourceClass,
            ),
        )
    }
    printFailures(results)
    if (rows.isNotEmpty()) {
        println(
            "aggregate: ${rows.size} tracks  median_total=${"%.2f".format(rows.sumOf { it.wallMedianSeconds })}s  " +
                "loads_per_open=${"%.2f".format(rows.sumOf { it.loadsPerOpen } / rows.size)}",
        )
    }
}

private fun printFailures(results: List<TrackResult>) {
    results.filterIsInstance<FailedResult>().forEach { println("${it.path}: ERROR: ${it.error}") }
}
