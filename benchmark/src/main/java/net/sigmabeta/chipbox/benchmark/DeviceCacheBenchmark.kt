package net.sigmabeta.chipbox.benchmark

import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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
import net.sigmabeta.sage.android.logging.AndroidHatchet
import net.sigmabeta.sage.logging.Hatchet
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The production PCM-cache path on a real device.
 *
 * `abrender`/`perfbench audio` time the raw emulator core; they never open a cache file, run the
 * BS.1770 measurer, trim trailing silence, or skip a track mid-render. This test drives the same
 * [RealPcmTrackSourceFactory] the app uses — real staging (source + `_lib` siblings), real hashing,
 * real cache-file I/O, the render-ahead writer thread — over the pushed corpus, and records what
 * only the device can prove:
 *
 * - **cold** open of a mini2sf with an 11 MB `.2sflib` sibling: native load, render-through,
 *   finite loudness/true-peak from the cache writer;
 * - **warm** re-opens: `CachedFilePcmSource`, and **zero** native loads/teardowns (ticket 05's
 *   fixed-rate path — a counting decorator around the real backend proves it, nothing is faked);
 * - **seek** into the cached range on both a live writer and a complete file;
 * - **trailing-silence** completion on a short track (the cache seals at the music's end, not the
 *   declared length);
 * - **error propagation** for a corrupt source (no crash, `getLastError()` non-null);
 * - **sustained load**: 60 cold open/read-a-few-buffers/close "skips" with the native heap measured
 *   before and after, asserting bounded live native memory (ticket 08's ownership fix).
 *
 * It writes `cache-device.tsv` into the run's additional-test-output directory, next to the axis
 * results, and logs every phase through `DeviceBenchmark.section`. The pushed corpus is the one
 * `scripts/benchmark/device-common.sh` stages; run it standalone with:
 *
 *   ./gradlew -Pandroid.testInstrumentationRunnerArguments.class=\
 *     net.sigmabeta.chipbox.benchmark.DeviceCacheBenchmark :benchmark:connectedBenchmarkAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class DeviceCacheBenchmark {

    @Test
    fun productionCacheColdWarmSeekTrimAndMemory() {
        DeviceBenchmark.beginTest("productionCacheColdWarmSeekTrimAndMemory")
        try {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            DeviceBenchmark.section("grant-storage") {
                DeviceBenchmark.grantStorageAccess(UiDevice.getInstance(instrumentation))
            }
            val work = File(instrumentation.context.cacheDir, SCRATCH_DIR).apply {
                deleteRecursively()
                mkdirs()
            }
            val hatchet = AndroidHatchet(enabled = true)
            val counting = CountingEmulator(TwosfEmulator)
            val registry = registryFor(work)
            val corpusRoot = DeviceBenchmark.corpusRoot()
            val buffer = ShortArray(READ_BUFFER_FRAMES * SHORTS_PER_FRAME)

            val tracks = DeviceBenchmark.section("walk-corpus") {
                walkCorpus(
                    root = corpusRoot,
                    supported = TwosfEmulator.supportedFileExtensions.toSet(),
                    extensionFilter = null,
                    gameFilter = null,
                    everySong = true,
                    subsong = 0,
                )
            }
            val psg = trackMatching(tracks, PSG_TRACK_MARKER)
            val short = trackMatching(tracks, SHORT_TRACK_MARKER)

            val rows = mutableListOf<CacheRow>()
            rows += measureSeekScenario(work, hatchet, counting, registry, psg, buffer)
            rows += measureTrimScenario(work, hatchet, counting, registry, short, buffer)
            rows += measureErrorScenario(work, hatchet, counting, registry, psg, buffer)
            rows += measureSustainedLoad(work, hatchet, counting, registry, psg, buffer)

            DeviceBenchmark.section("write-result") {
                DeviceBenchmark.writeArtifact(RESULT_FILE, cacheTsv(rows))
            }
        } finally {
            DeviceBenchmark.endTest("productionCacheColdWarmSeekTrimAndMemory")
        }
    }

    /**
     * Cold + warm + seek on the one PSG/BLEP track that carries a `.2sflib` sibling. The cold phase
     * seeks back into the region the writer has already committed, which is exactly the
     * `CachingPcmSource.seek` path the app takes when you scrub a track that is still rendering.
     */
    private fun measureSeekScenario(
        work: File,
        hatchet: Hatchet,
        counting: CountingEmulator,
        registry: ContentSourceRegistry,
        selected: SelectedTrack,
        buffer: ShortArray,
    ): CacheRow = DeviceBenchmark.section("cache-cold-warm-seek", "path" to selected.trackKey) {
        val (track, bytes) = productionTrack(selected, SECONDS, hatchet)
        val factory = factory(File(work, "psg"), hatchet, counting, registry)
        val cold = runBlocking { openAndDrain(factory, counting, track, bytes, buffer, seekAt = SEEK_FRACTION) }
        assertEquals("cold open of a fresh cache must miss", MISS_SOURCE, cold.sourceClass)
        assertEquals("cold open must load the native core exactly once", 1, cold.emulatorLoads)
        assertTrue("cold open produced no audio", cold.frames > 0)
        assertTrue("cold seek into the cached range returned no frames", cold.seekFrames > 0)
        assertTrue("cold seek wall time was not measured", cold.seekWallSeconds >= 0.0)
        assertEquals("2SF is a fixed 44.1 kHz backend", SAMPLE_RATE, cold.sampleRate)
        assertTrue("cache writer did not measure loudness", cold.loudnessLufs.isFinite())
        assertTrue("cache writer did not measure true peak", cold.truePeakDbtp.isFinite())

        val warm = runBlocking {
            List(WARM_REPEATS) {
                openAndDrain(factory, counting, track, bytes, buffer, seekAt = SEEK_FRACTION)
            }
        }
        warm.forEach { phase ->
            assertEquals("warm open must hit the complete cache", HIT_SOURCE, phase.sourceClass)
            assertEquals("warm hit must not load the native core", 0, phase.emulatorLoads)
            assertEquals("warm hit must not tear the native core down", 0, phase.emulatorTeardowns)
            assertTrue("warm seek into the cached range returned no frames", phase.seekFrames > 0)
            assertTrue("warm source did not report loudness", phase.loudnessLufs.isFinite())
            assertTrue("warm source did not report true peak", phase.truePeakDbtp.isFinite())
        }

        val warmMedian = median(warm.map { it.wallSeconds })
        assertTrue(
            "warm re-open (${warmMedian}s) was not faster than cold (${cold.wallSeconds}s)",
            warmMedian < cold.wallSeconds,
        )
        CacheRow(
            scenario = "cold-warm-seek",
            path = selected.trackKey,
            coldWallSeconds = cold.wallSeconds,
            coldFirstFrameSeconds = cold.firstFrameSeconds,
            coldLoads = cold.emulatorLoads,
            coldSource = cold.sourceClass,
            warmWallMedianSeconds = warmMedian,
            warmFirstFrameMedianSeconds = median(warm.map { it.firstFrameSeconds }),
            warmLoadsPerOpen = warm.sumOf { it.emulatorLoads }.toDouble() / warm.size,
            warmTeardownsPerOpen = warm.sumOf { it.emulatorTeardowns }.toDouble() / warm.size,
            warmSource = warm.first().sourceClass,
            cachedFrames = cold.cachedFrames,
            sampleRate = cold.sampleRate,
            loudnessLufs = cold.loudnessLufs,
            seekFrames = cold.seekFrames,
            memoryGrowthBytes = 0L,
            opens = warm.size + 1,
            note = "",
        )
    }

    /**
     * Trailing-silence completion: the short Pokémon track renders ~2.7 s of music inside a 30 s
     * budget, so the cache must seal at the music's end and a warm open must report that shorter
     * length — not the declared 30 s.
     */
    private fun measureTrimScenario(
        work: File,
        hatchet: Hatchet,
        counting: CountingEmulator,
        registry: ContentSourceRegistry,
        selected: SelectedTrack,
        buffer: ShortArray,
    ): CacheRow = DeviceBenchmark.section("cache-trailing-silence", "path" to selected.trackKey) {
        val (track, bytes) = productionTrack(selected, SECONDS, hatchet)
        val factory = factory(File(work, "trim"), hatchet, counting, registry)
        val cold = runBlocking { openAndDrain(factory, counting, track, bytes, buffer, seekAt = null) }
        val budgetFrames = SECONDS.toLong() * SAMPLE_RATE
        assertTrue("trailing-silence track produced no audio", cold.cachedFrames > 0)
        assertTrue(
            "trailing silence was not trimmed (cached ${cold.cachedFrames} frames of $budgetFrames)",
            cold.cachedFrames < budgetFrames,
        )
        val warm = runBlocking { openAndDrain(factory, counting, track, bytes, buffer, seekAt = null) }
        assertEquals("warm open of a trimmed track must hit the cache", HIT_SOURCE, warm.sourceClass)
        assertEquals("trimmed warm length must match the sealed cache", cold.cachedFrames, warm.totalFrames)
        CacheRow(
            scenario = "trailing-silence",
            path = selected.trackKey,
            coldWallSeconds = cold.wallSeconds,
            coldFirstFrameSeconds = cold.firstFrameSeconds,
            coldLoads = cold.emulatorLoads,
            coldSource = cold.sourceClass,
            warmWallMedianSeconds = warm.wallSeconds,
            warmFirstFrameMedianSeconds = warm.firstFrameSeconds,
            warmLoadsPerOpen = warm.emulatorLoads.toDouble(),
            warmTeardownsPerOpen = warm.emulatorTeardowns.toDouble(),
            warmSource = warm.sourceClass,
            cachedFrames = cold.cachedFrames,
            sampleRate = cold.sampleRate,
            loudnessLufs = cold.loudnessLufs,
            seekFrames = 0,
            memoryGrowthBytes = 0L,
            opens = 2,
            note = "budget=$budgetFrames",
        )
    }

    /**
     * Error propagation: truncated source bytes must surface through `getLastError()` (or throw
     * from `open`) without taking the process down — the app's skip-to-next-track path depends on
     * a failed track being reportable, not fatal.
     */
    private fun measureErrorScenario(
        work: File,
        hatchet: Hatchet,
        counting: CountingEmulator,
        registry: ContentSourceRegistry,
        selected: SelectedTrack,
        buffer: ShortArray,
    ): CacheRow = DeviceBenchmark.section("cache-error-propagation", "path" to selected.trackKey) {
        val (track, bytes) = productionTrack(selected, SECONDS, hatchet)
        val truncated = bytes.copyOf(minOf(bytes.size, CORRUPT_BYTES))
        val factory = factory(File(work, "error"), hatchet, counting, registry)
        val error = runBlocking {
            try {
                val source = factory.open(track, truncated)
                try {
                    readOneBuffer(source, buffer)
                    source.getLastError()
                } finally {
                    source.close()
                }
            } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
                t.message ?: t.javaClass.simpleName
            }
        }
        assertNotEquals("a corrupt 2SF must report an error", null, error)
        CacheRow(
            scenario = "error-propagation",
            path = selected.trackKey,
            coldWallSeconds = 0.0,
            coldFirstFrameSeconds = 0.0,
            coldLoads = counting.loads,
            coldSource = "",
            warmWallMedianSeconds = 0.0,
            warmFirstFrameMedianSeconds = 0.0,
            warmLoadsPerOpen = 0.0,
            warmTeardownsPerOpen = 0.0,
            warmSource = "",
            cachedFrames = 0L,
            sampleRate = SAMPLE_RATE,
            loudnessLufs = Double.NaN,
            seekFrames = 0,
            memoryGrowthBytes = 0L,
            opens = 1,
            note = "reported=${error?.replace('\t', ' ')?.take(NOTE_LIMIT)}",
        )
    }

    /**
     * Sustained load: 60 production "skips" — open a fresh cache (cold), read a few buffers, close
     * mid-render. Each iteration gets its own cache directory so every open really misses, and the
     * live native heap is sampled after warm-up and at the end. The bounded-growth assertion is the
     * device counterpart of ticket 08's host sustained-load gate.
     */
    private fun measureSustainedLoad(
        work: File,
        hatchet: Hatchet,
        counting: CountingEmulator,
        registry: ContentSourceRegistry,
        selected: SelectedTrack,
        buffer: ShortArray,
    ): CacheRow = DeviceBenchmark.section("cache-sustained-load", "path" to selected.trackKey) {
        val (track, bytes) = productionTrack(selected, SECONDS, hatchet)
        val root = File(work, "sustained")
        fun skip(index: Int) = runBlocking {
            val factory = factory(File(root, "open-$index"), hatchet, counting, registry)
            val source = factory.open(track, bytes)
            try {
                repeat(SKIP_BUFFERS) { readOneBuffer(source, buffer) }
            } finally {
                source.close()
            }
        }
        repeat(WARMUP_OPENS) { skip(it) }
        val before = nativeHeapAfterGc()
        for (index in WARMUP_OPENS until WARMUP_OPENS + SUSTAINED_OPENS) skip(index)
        val after = nativeHeapAfterGc()
        root.deleteRecursively()

        val growth = after - before
        val perOpen = growth.toDouble() / SUSTAINED_OPENS
        assertTrue(
            "native heap grew ${growth}B over $SUSTAINED_OPENS cold skips " +
                "(${perOpen}B/open) — unbounded per-track native ownership",
            perOpen < MAX_GROWTH_BYTES_PER_OPEN,
        )
        CacheRow(
            scenario = "sustained-load",
            path = selected.trackKey,
            coldWallSeconds = 0.0,
            coldFirstFrameSeconds = 0.0,
            coldLoads = SUSTAINED_OPENS,
            coldSource = MISS_SOURCE,
            warmWallMedianSeconds = 0.0,
            warmFirstFrameMedianSeconds = 0.0,
            warmLoadsPerOpen = 0.0,
            warmTeardownsPerOpen = 0.0,
            warmSource = "",
            cachedFrames = 0L,
            sampleRate = SAMPLE_RATE,
            loudnessLufs = Double.NaN,
            seekFrames = 0,
            memoryGrowthBytes = growth,
            opens = SUSTAINED_OPENS,
            note = "before=$before after=$after per_open=$perOpen",
        )
    }

    /**
     * Opens [track] once, reads it (optionally seeking back into the committed range first), and
     * closes it. Time-to-first-frame starts before `open` so it includes staging, hashing and the
     * native load — the user-visible latency.
     */
    private suspend fun openAndDrain(
        factory: RealPcmTrackSourceFactory,
        counting: CountingEmulator,
        track: Track,
        bytes: ByteArray,
        buffer: ShortArray,
        seekAt: Double?,
    ): Phase {
        counting.reset()
        val started = System.nanoTime()
        val source = factory.open(track, bytes)
        val drain = try {
            drainSource(source, buffer, seekAt)
        } finally {
            source.close()
        }
        return Phase(
            wallSeconds = (System.nanoTime() - started) / NANOS_PER_SECOND,
            firstFrameSeconds = if (drain.firstFrameNanos < 0) {
                Double.NaN
            } else {
                (drain.firstFrameNanos - started) / NANOS_PER_SECOND
            },
            frames = drain.frames,
            seekFrames = drain.seek.frames,
            seekWallSeconds = drain.seek.wallSeconds,
            cachedFrames = source.cachedFrames,
            totalFrames = source.totalFrames ?: -1L,
            sampleRate = source.sampleRate,
            emulatorLoads = counting.loads,
            emulatorTeardowns = counting.teardowns,
            sourceClass = source.javaClass.simpleName,
            loudnessLufs = source.loudnessLufs,
            truePeakDbtp = source.truePeakDbtp,
        )
    }

    /**
     * Reads [source] to end-of-track, optionally probing a seek into the committed range once the
     * first buffer has arrived. Every empty read is polled rather than treated as end-of-track,
     * because a render-ahead writer may not have produced the frames under the cursor yet.
     */
    private suspend fun drainSource(
        source: PcmTrackSource,
        buffer: ShortArray,
        seekAt: Double?,
    ): Drain {
        var frames = 0L
        var firstFrameNanos = -1L
        var seek = SeekProbe(0, 0.0)
        val deadline = System.currentTimeMillis() + MAX_PHASE_WALL_MILLIS
        while (!source.isOver) {
            check(System.currentTimeMillis() <= deadline) { "phase exceeded its wall cap" }
            val read = source.readFrames(buffer)
            if (read > 0) {
                if (firstFrameNanos < 0) firstFrameNanos = System.nanoTime()
                frames += read
            }
            val failure = source.getLastError()
            if (failure != null) error("cache source failed: $failure")
            if (seekAt != null && frames > 0 && seek.frames == 0) {
                seek = seekIntoCommittedRange(source, buffer, seekAt)
            }
            yield()
        }
        return Drain(frames, firstFrameNanos, seek)
    }

    /** Reads one buffer, polling while a render-ahead writer catches up, bounded by the wall cap. */
    private suspend fun readOneBuffer(source: PcmTrackSource, buffer: ShortArray): Int {
        val deadline = System.currentTimeMillis() + MAX_PHASE_WALL_MILLIS
        var frames = 0
        while (frames == 0 && System.currentTimeMillis() <= deadline) {
            frames = source.readFrames(buffer)
            val stalled = frames == 0 && (source.getLastError() != null || source.isOver)
            if (stalled) break
            if (frames == 0) yield()
        }
        return frames
    }

    /** Seeks to [fraction] of the committed range and reads the first buffer back from it. */
    private suspend fun seekIntoCommittedRange(
        source: PcmTrackSource,
        buffer: ShortArray,
        fraction: Double,
    ): SeekProbe {
        val target = (source.cachedFrames * fraction).toLong()
        val started = System.nanoTime()
        source.seek(target)
        val frames = source.readFrames(buffer)
        return SeekProbe(frames, (System.nanoTime() - started) / NANOS_PER_SECOND)
    }

    /** Collecting the live native heap is inherently non-deterministic; ask for a collection. */
    @Suppress("ExplicitGarbageCollectionCall")
    private fun nativeHeapAfterGc(): Long {
        System.gc()
        Thread.sleep(GC_SETTLE_MILLIS)
        return Debug.getNativeHeapAllocatedSize()
    }

    private fun factory(
        workDir: File,
        hatchet: Hatchet,
        counting: CountingEmulator,
        registry: ContentSourceRegistry,
    ): RealPcmTrackSourceFactory {
        workDir.mkdirs()
        return RealPcmTrackSourceFactory(
            emulators = listOf(counting),
            stagingDir = File(workDir, "staging").toOkioPath(),
            pcmCacheDir = File(workDir, "pcm").toOkioPath(),
            fileSystem = FileSystem.SYSTEM,
            contentSourceRegistry = registry,
            hatchet = hatchet,
        )
    }

    private fun registryFor(workDir: File): ContentSourceRegistry = ContentSourceRegistry(
        setOf(
            LocalFileContentSource(
                fileSystem = FileSystem.SYSTEM,
                locationsFile = File(workDir, "locations.txt").toOkioPath(),
            ),
        ),
    )

    private fun trackMatching(tracks: List<SelectedTrack>, marker: String): SelectedTrack =
        tracks.firstOrNull { it.trackKey.contains(marker) }
            ?: error("corpus has no track matching '$marker' — is the 2SF corpus pushed?")

    /**
     * Builds the production [Track] for a corpus file, resolving 2SF `_lib` siblings into staging
     * chain files exactly as the scanner does (same shape as `perfbench cache`).
     */
    private fun productionTrack(
        selected: SelectedTrack,
        seconds: Int,
        hatchet: Hatchet,
    ): Pair<Track, ByteArray> {
        val file = File(selected.path)
        val bytes = file.readBytes()
        val tagInfo = PsfReader(hatchet).readTagInfo(bytes)
        val chainFiles = tagInfo?.libReferences.orEmpty().mapNotNull { reference ->
            File(file.parentFile, reference)
                .takeIf { it.isFile }
                ?.let { ChainFile(filename = it.name, uri = it.absolutePath) }
        }
        val track = Track(
            id = TRACK_ID,
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
        return track to bytes
    }

    /** The frames and wall time of a seek into the already-committed cache range. */
    private data class SeekProbe(val frames: Int, val wallSeconds: Double)

    /** Frames read, the first-frame timestamp, and the optional seek probe from one drain. */
    private data class Drain(val frames: Long, val firstFrameNanos: Long, val seek: SeekProbe)

    /** One open's measurement. Durations are seconds; frames are stereo frames. */
    private data class Phase(
        val wallSeconds: Double,
        val firstFrameSeconds: Double,
        val frames: Long,
        val seekFrames: Int,
        val seekWallSeconds: Double,
        val cachedFrames: Long,
        val totalFrames: Long,
        val sampleRate: Int,
        val emulatorLoads: Int,
        val emulatorTeardowns: Int,
        val sourceClass: String,
        val loudnessLufs: Double,
        val truePeakDbtp: Double,
    )

    /** One artefact row; the TSV is the evidence quoted in the ticket answer. */
    private data class CacheRow(
        val scenario: String,
        val path: String,
        val coldWallSeconds: Double,
        val coldFirstFrameSeconds: Double,
        val coldLoads: Int,
        val coldSource: String,
        val warmWallMedianSeconds: Double,
        val warmFirstFrameMedianSeconds: Double,
        val warmLoadsPerOpen: Double,
        val warmTeardownsPerOpen: Double,
        val warmSource: String,
        val cachedFrames: Long,
        val sampleRate: Int,
        val loudnessLufs: Double,
        val seekFrames: Int,
        val memoryGrowthBytes: Long,
        val opens: Int,
        val note: String,
    )

    /**
     * Thin counting decorator around the real production backend: it forwards every call to the
     * singleton [TwosfEmulator] and only records native track-load/teardown calls, so the test
     * measures the production path rather than a fake emulator.
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

        // Delegated so the fixed-rate backend actually takes the factory's warm-hit path instead of
        // falling back to native rate negotiation.
        override val knownSampleRate: Int? get() = delegate.knownSampleRate

        override val supportedFileExtensions: List<String> = delegate.supportedFileExtensions

        override fun setTrackNumber(number: Int) = delegate.setTrackNumber(number)
    }

    private companion object {
        const val SECONDS = 30
        const val SAMPLE_RATE = 44_100
        const val TRACK_ID = 1L
        const val READ_BUFFER_FRAMES = 4096
        const val SHORTS_PER_FRAME = 2
        const val MILLIS_PER_SECOND = 1000L
        const val MAX_PHASE_WALL_MILLIS = 180L * MILLIS_PER_SECOND
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val GC_SETTLE_MILLIS = 250L
        const val CORRUPT_BYTES = 12
        const val NOTE_LIMIT = 160
        const val SKIP_BUFFERS = 4
        const val WARMUP_OPENS = 5
        const val SUSTAINED_OPENS = 60
        const val WARM_REPEATS = 3
        const val SEEK_FRACTION = 0.5
        const val MAX_GROWTH_BYTES_PER_OPEN = 32.0 * 1024
        const val SCRATCH_DIR = "device-cache"
        const val RESULT_FILE = "cache-device.tsv"
        const val MISS_SOURCE = "CachingPcmSource"
        const val HIT_SOURCE = "CachedFilePcmSource"
        const val PSG_TRACK_MARKER = "Phoenix Wright_ Ace Attorney Justice for All"
        const val SHORT_TRACK_MARKER = "Pokemon Heart Gold & Soul Silver/108 Get Item"

        fun median(values: List<Double>): Double {
            val sorted = values.sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
        }

        fun cacheTsv(rows: List<CacheRow>): String = buildString {
            appendLine(
                "scenario\tpath\topens\tcold_wall_s\tcold_ttff_s\tcold_loads\tcold_source\t" +
                    "warm_wall_median_s\twarm_ttff_median_s\twarm_loads_per_open\t" +
                    "warm_teardowns_per_open\twarm_source\tcached_frames\tsample_rate\t" +
                    "loudness_lufs\tseek_frames\tmemory_growth_bytes\tnote",
            )
            rows.forEach { row ->
                appendLine(
                    listOf(
                        row.scenario,
                        row.path,
                        row.opens.toString(),
                        row.coldWallSeconds.format(),
                        row.coldFirstFrameSeconds.format(),
                        row.coldLoads.toString(),
                        row.coldSource,
                        row.warmWallMedianSeconds.format(),
                        row.warmFirstFrameMedianSeconds.format(),
                        row.warmLoadsPerOpen.format(),
                        row.warmTeardownsPerOpen.format(),
                        row.warmSource,
                        row.cachedFrames.toString(),
                        row.sampleRate.toString(),
                        row.loudnessLufs.format(),
                        row.seekFrames.toString(),
                        row.memoryGrowthBytes.toString(),
                        row.note,
                    ).joinToString("\t"),
                )
            }
        }

        fun Double.format(): String = if (isFinite()) String.format(java.util.Locale.ROOT, "%.4f", this) else toString()
    }
}
