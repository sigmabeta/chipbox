package net.sigmabeta.chipbox.benchmark

import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
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
import net.sigmabeta.chipbox.player.emulators.twosf.TwosfEmulator
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
 * - **sustained load**: 60 cold opens per fixture, with the native heap measured before and after.
 *   One fixture is the corpus track through the cache factory (the per-load `NDS_state` core
 *   ownership path); the other is the same bytes with a synthetic reserved `SAVE` chunk, driven
 *   through the raw backend's load/generate/teardown cycle (the `state.state` SAVE-section
 *   alloc/restore/free path; a zeroed state renders silence, which the cache's no-audio watchdog
 *   would reject, so it bypasses the factory). The bound is 2 KiB/open: the fixed build measures a
 *   few hundred bytes/open of allocator noise, while the pre-ticket-08 build leaked 6,392 B per
 *   teardown (core) and the whole 512 KiB SAVE buffer per open — both far outside it.
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
        val (track, bytes) = productionTrack(TRACK_ID, selected, SECONDS, hatchet)
        val factory = factory(File(work, "psg"), hatchet, counting, registry)
        val cold = runBlocking { openAndDrain(factory, counting, track, bytes, buffer, seekAt = SEEK_FRACTION) }
        assertEquals("cold open of a fresh cache must miss", MISS_SOURCE, cold.sourceClass)
        assertEquals("cold open must load the native core exactly once", 1, cold.emulatorLoads)
        assertTrue("cold open produced no audio", cold.frames > 0)
        assertTrue("cold seek into the cached range returned no frames", cold.seekFrames > 0)
        assertTrue("cold seek wall time was not measured", cold.seekWallSeconds >= 0.0)
        assertTrue("2SF must report its real sample rate", cold.sampleRate > 0)
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
        FlowRow(
            scenario = "cold-warm-seek",
            path = selected.trackKey,
            opens = warm.size + 1,
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
        val (track, bytes) = productionTrack(TRACK_ID, selected, SECONDS, hatchet)
        val factory = factory(File(work, "trim"), hatchet, counting, registry)
        val cold = runBlocking { openAndDrain(factory, counting, track, bytes, buffer, seekAt = null) }
        assertTrue("cold open did not report the backend's sample rate", cold.sampleRate > 0)
        val budgetFrames = SECONDS.toLong() * cold.sampleRate
        assertTrue("trailing-silence track produced no audio", cold.cachedFrames > 0)
        assertTrue(
            "trailing silence was not trimmed (cached ${cold.cachedFrames} frames of $budgetFrames)",
            cold.cachedFrames < budgetFrames,
        )
        val warm = runBlocking { openAndDrain(factory, counting, track, bytes, buffer, seekAt = null) }
        assertEquals("warm open of a trimmed track must hit the cache", HIT_SOURCE, warm.sourceClass)
        assertEquals("trimmed warm length must match the sealed cache", cold.cachedFrames, warm.totalFrames)
        FlowRow(
            scenario = "trailing-silence",
            path = selected.trackKey,
            opens = 2,
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
        val (track, bytes) = productionTrack(TRACK_ID, selected, SECONDS, hatchet)
        val truncated = bytes.copyOf(minOf(bytes.size, CORRUPT_BYTES))
        val factory = factory(File(work, "error"), hatchet, counting, registry)
        counting.reset()
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
        ErrorRow(
            scenario = "error-propagation",
            path = selected.trackKey,
            loads = counting.loads,
            error = error?.replace('\t', ' ')?.take(NOTE_LIMIT).orEmpty(),
            note = "",
        )
    }

    /**
     * Sustained load: 60 cold opens per fixture, with the live native heap sampled after warm-up
     * and at the end. Two fixtures split the ownership paths:
     *
     * - **core** (`sustained-core`): the corpus mini2sf through the production cache factory — the
     *   app's actual skip path. Each iteration gets its own cache directory so every open really
     *   misses. A leaked `NDS_state` core is 6,392 B per teardown.
     * - **save** (`sustained-save`): the same bytes plus a synthetic 512 KiB reserved `SAVE` chunk,
     *   loaded and torn down through the real backend. The loader turns the chunk into
     *   `state.state`, restores it, and (fixed) frees it; a leaked SAVE buffer is 512 KiB per open.
     *   It deliberately bypasses the cache factory: a zeroed SAVE state renders silence, and the
     *   cache's no-audio watchdog rejects an all-silent track — which is a property of the fixture,
     *   not of the ownership being measured. The fixture's state *contents* are synthetic
     *   (zero-filled), so this covers the SAVE alloc/restore/free ownership, not real save-data
     *   restoration; that gap stays in ticket 02.
     */
    private fun measureSustainedLoad(
        work: File,
        hatchet: Hatchet,
        counting: CountingEmulator,
        registry: ContentSourceRegistry,
        selected: SelectedTrack,
        buffer: ShortArray,
    ): List<CacheRow> = DeviceBenchmark.section("cache-sustained-load", "path" to selected.trackKey) {
        val (track, bytes) = productionTrack(TRACK_ID, selected, SECONDS, hatchet)
        listOf(
            measureSustainedCore(work, hatchet, counting, registry, selected.trackKey, track, bytes, buffer),
            measureSustainedSave(work, counting, selected.trackKey, track, bytes, buffer),
        )
    }

    /** The app's real skip path: 60 fresh-cache opens, each read a few buffers then closed. */
    @Suppress("LongParameterList")
    private fun measureSustainedCore(
        work: File,
        hatchet: Hatchet,
        counting: CountingEmulator,
        registry: ContentSourceRegistry,
        path: String,
        track: Track,
        bytes: ByteArray,
        buffer: ShortArray,
    ): CacheRow = DeviceBenchmark.section(SUSTAINED_CORE) {
        val root = File(work, SUSTAINED_CORE)
        var sampleRate = 0

        fun skip(index: Int): Int = runBlocking {
            val factory = factory(File(root, "open-$index"), hatchet, counting, registry)
            val source = factory.open(track, bytes)
            try {
                if (sampleRate == 0) sampleRate = source.sampleRate
                var frames = 0
                repeat(SKIP_BUFFERS) { frames += readOneBuffer(source, buffer) }
                frames
            } finally {
                source.close()
            }
        }
        repeat(WARMUP_OPENS) { skip(it) }
        val before = nativeHeapAfterGc()
        counting.reset()
        var frames = 0
        for (index in WARMUP_OPENS until WARMUP_OPENS + SUSTAINED_OPENS) frames += skip(index)
        val after = nativeHeapAfterGc()
        val loads = counting.loads
        root.deleteRecursively()

        assertTrue("$SUSTAINED_CORE: no audio produced, the fixture did not load", frames > 0)
        assertEquals("$SUSTAINED_CORE: every cold skip must load the native core", SUSTAINED_OPENS, loads)
        assertTrue("$SUSTAINED_CORE: the source did not report a sample rate", sampleRate > 0)
        memoryRow(SUSTAINED_CORE, path, sampleRate, loads, before, after, "cold skips")
    }

    /**
     * The SAVE-section ownership path: 60 raw backend load/generate/teardown cycles over a fixture
     * carrying a synthetic 512 KiB SAVE chunk. No cache entry is involved (see [measureSustainedLoad]).
     */
    @Suppress("LongParameterList")
    private fun measureSustainedSave(
        work: File,
        counting: CountingEmulator,
        path: String,
        track: Track,
        bytes: ByteArray,
        buffer: ShortArray,
    ): CacheRow = DeviceBenchmark.section(SUSTAINED_SAVE) {
        val fixtureDir = File(work, SUSTAINED_SAVE).apply { mkdirs() }
        val main = File(fixtureDir, File(track.path).name)
        main.writeBytes(withSaveChunk(bytes, SAVE_STATE_BYTES))
        // The native loader resolves `_lib` siblings relative to the staged file, so they must sit
        // next to it.
        track.chainFiles.orEmpty().forEach { chain ->
            File(chain.uri).takeIf { it.isFile }?.copyTo(File(fixtureDir, chain.filename), overwrite = true)
        }
        val saveTrack = track.copy(path = main.absolutePath)

        fun cycle(): Int {
            counting.loadTrack(saveTrack)
            val loadError = counting.getLastError()
            check(loadError == null) { "$SUSTAINED_SAVE: load failed: $loadError" }
            var frames = 0
            repeat(SKIP_BUFFERS) { frames += counting.generateBufferInternal(buffer, READ_BUFFER_FRAMES) }
            counting.teardown()
            return frames
        }
        repeat(WARMUP_OPENS) { cycle() }
        val before = nativeHeapAfterGc()
        counting.reset()
        var frames = 0
        repeat(SUSTAINED_OPENS) { frames += cycle() }
        val after = nativeHeapAfterGc()
        val loads = counting.loads
        val sampleRate = counting.getSampleRateInternal()
        fixtureDir.deleteRecursively()

        assertTrue("$SUSTAINED_SAVE: no frames generated, the fixture did not load", frames > 0)
        assertEquals("$SUSTAINED_SAVE: every cycle must load the native core", SUSTAINED_OPENS, loads)
        assertTrue("$SUSTAINED_SAVE: the backend did not report a sample rate", sampleRate > 0)
        memoryRow(SUSTAINED_SAVE, path, sampleRate, loads, before, after, "load cycles")
    }

    /** Asserts the bounded-growth rule and reduces a sustained-load run to its artefact row. */
    private fun memoryRow(
        scenario: String,
        path: String,
        sampleRate: Int,
        loads: Int,
        before: Long,
        after: Long,
        unit: String,
    ): CacheRow {
        val growth = after - before
        val perOpen = growth.toDouble() / SUSTAINED_OPENS
        assertTrue(
            "$scenario: native heap grew ${growth}B over $SUSTAINED_OPENS $unit " +
                "(${perOpen}B/$unit) — unbounded per-track native ownership",
            perOpen < MAX_GROWTH_BYTES_PER_OPEN,
        )
        return MemoryRow(
            scenario = scenario,
            path = path,
            opens = SUSTAINED_OPENS,
            loads = loads,
            sampleRate = sampleRate,
            memoryGrowthBytes = growth,
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
     * Inserts a synthetic reserved `SAVE` chunk into a real mini2sf, mirroring
     * `notes/raw/08-ownership/make_inputs.py`:
     *
     *     chunk   = "SAVE" + <u32 zsize> + <u32 crc32(zdata)> + zdata
     *     payload = <u32 xofs=0> + <u32 xsize=N> + N zero bytes
     *     zdata   = zlib(payload)
     *
     * The vendored psflib reads header, reserved, exe, tag in that order, so the result is
     * `header' | reserved | exe | tag'`. The loader turns the chunk into the SAVE-section buffer
     * (`state.state`, N bytes) and restores it; zero content is fine because this exercises the
     * allocation/restore/free ownership, not save-data semantics.
     */
    private fun withSaveChunk(mini2sf: ByteArray, stateBytes: Int): ByteArray {
        require(mini2sf.size > PSF_HEADER_BYTES) { "fixture is not a PSF file" }
        require(
            mini2sf[0] == 'P'.code.toByte() &&
                mini2sf[1] == 'S'.code.toByte() &&
                mini2sf[2] == 'F'.code.toByte(),
        ) { "fixture is not a PSF file" }
        val version = mini2sf[PSF_VERSION_OFFSET]
        require(readU32Le(mini2sf, RESERVED_SIZE_OFFSET) == 0) { "fixture already has a reserved section" }
        val exeSize = readU32Le(mini2sf, EXE_SIZE_OFFSET)
        val exeCrc = readU32Le(mini2sf, EXE_CRC_OFFSET)
        val exe = mini2sf.copyOfRange(PSF_HEADER_BYTES, PSF_HEADER_BYTES + exeSize)
        val tail = mini2sf.copyOfRange(PSF_HEADER_BYTES + exeSize, mini2sf.size)

        val payload = ByteArray(SAVE_PAYLOAD_HEADER_BYTES + stateBytes) // xofs = 0, then xsize, zeros.
        writeU32Le(payload, SAVE_XSIZE_OFFSET, stateBytes)
        val compressed = ByteArrayOutputStream().also { out ->
            DeflaterOutputStream(out, Deflater(Deflater.BEST_COMPRESSION)).use { it.write(payload) }
        }.toByteArray()
        val crc = CRC32().apply { update(compressed) }.value.toInt()

        val chunk = ByteArrayOutputStream().also { out ->
            out.write(SAVE_TAG)
            out.write(u32Le(compressed.size))
            out.write(u32Le(crc))
            out.write(compressed)
        }.toByteArray()

        return ByteArrayOutputStream().also { out ->
            out.write('P'.code)
            out.write('S'.code)
            out.write('F'.code)
            out.write(version.toInt())
            out.write(u32Le(chunk.size))
            out.write(u32Le(exe.size))
            out.write(u32Le(exeCrc))
            out.write(chunk)
            out.write(exe)
            out.write(tail)
        }.toByteArray()
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

    /** One artefact row. Each scenario fills only the fields it measures; the TSV blanks the rest. */
    private sealed interface CacheRow {
        val scenario: String
        val path: String
        val note: String
    }

    /** Cold/warm timing scenarios (cache seek, trailing-silence completion). */
    private data class FlowRow(
        override val scenario: String,
        override val path: String,
        val opens: Int,
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
        override val note: String,
    ) : CacheRow

    /** A sustained-load fixture: opens, actual native loads, and the measured heap delta. */
    private data class MemoryRow(
        override val scenario: String,
        override val path: String,
        val opens: Int,
        val loads: Int,
        val sampleRate: Int,
        val memoryGrowthBytes: Long,
        override val note: String,
    ) : CacheRow

    /** A scenario that deliberately produced an error, which is the result worth recording. */
    private data class ErrorRow(
        override val scenario: String,
        override val path: String,
        val loads: Int,
        val error: String,
        override val note: String,
    ) : CacheRow

    private companion object {
        const val SECONDS = 30
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

        /**
         * 2 KiB/open. The fixed build measures 55–279 B/open (allocator noise); the pre-ticket-08
         * build leaked 6,392 B per teardown (core) and a full 512 KiB SAVE buffer per open. This
         * sits ~7× above the noise and ~3× below the smallest real leak.
         */
        const val MAX_GROWTH_BYTES_PER_OPEN = 2.0 * 1024

        /** The SAVE-section size the synthetic fixture asks for; a leak is unmissable at 512 KiB. */
        const val SAVE_STATE_BYTES = 512 * 1024

        const val SCRATCH_DIR = "device-cache"
        const val SUSTAINED_CORE = "sustained-core"
        const val SUSTAINED_SAVE = "sustained-save"
        const val RESULT_FILE = "cache-device.tsv"
        const val MISS_SOURCE = "CachingPcmSource"
        const val HIT_SOURCE = "CachedFilePcmSource"
        const val PSG_TRACK_MARKER = "Phoenix Wright_ Ace Attorney Justice for All"
        const val SHORT_TRACK_MARKER = "Pokemon Heart Gold & Soul Silver/108 Get Item"

        // PSF header: "PSF" + version byte + u32 reserved size + u32 exe size + u32 exe crc32.
        const val PSF_HEADER_BYTES = 16
        const val RESERVED_SIZE_OFFSET = 4
        const val EXE_SIZE_OFFSET = 8
        const val EXE_CRC_OFFSET = 12
        const val SAVE_PAYLOAD_HEADER_BYTES = 8
        const val SAVE_XSIZE_OFFSET = 4
        const val PSF_VERSION_OFFSET = 3
        const val BYTE_MASK = 0xFF
        const val BITS_PER_BYTE = 8
        const val U32_BYTES = 4
        val SAVE_TAG = byteArrayOf('S'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte())

        fun median(values: List<Double>): Double {
            val sorted = values.sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
        }

        fun readU32Le(bytes: ByteArray, offset: Int): Int {
            var value = 0
            for (index in 0 until U32_BYTES) {
                value = value or ((bytes[offset + index].toInt() and BYTE_MASK) shl (index * BITS_PER_BYTE))
            }
            return value
        }

        fun u32Le(value: Int): ByteArray = ByteArray(U32_BYTES) { index ->
            ((value ushr (index * BITS_PER_BYTE)) and BYTE_MASK).toByte()
        }

        fun writeU32Le(bytes: ByteArray, offset: Int, value: Int) {
            u32Le(value).copyInto(bytes, offset)
        }

        /**
         * Writes the union column set; a scenario's absent measurements are empty cells. `error`
         * and `sample_rate` are real columns, taken from what the source reported — never a
         * backend constant.
         */
        fun cacheTsv(rows: List<CacheRow>): String = buildString {
            appendLine(
                "scenario\tpath\topens\tcold_wall_s\tcold_ttff_s\tcold_loads\tcold_source\t" +
                    "warm_wall_median_s\twarm_ttff_median_s\twarm_loads_per_open\t" +
                    "warm_teardowns_per_open\twarm_source\tcached_frames\tsample_rate\t" +
                    "loudness_lufs\tseek_frames\tmemory_growth_bytes\terror\tnote",
            )
            rows.forEach { appendLine(cacheRow(it)) }
        }

        fun cacheRow(row: CacheRow): String {
            val cells = when (row) {
                is FlowRow -> listOf(
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
                    "",
                    "",
                    row.note,
                )

                is MemoryRow -> listOf(
                    row.scenario,
                    row.path,
                    row.opens.toString(),
                    "", "", row.loads.toString(), "",
                    "", "", "", "", "",
                    "",
                    row.sampleRate.toString(),
                    "",
                    "",
                    row.memoryGrowthBytes.toString(),
                    "",
                    row.note,
                )

                is ErrorRow -> listOf(
                    row.scenario,
                    row.path,
                    "", "", row.loads.toString(), "", "",
                    "", "", "", "", "",
                    "", "", "", "", "",
                    row.error,
                    row.note,
                )
            }
            return cells.joinToString("\t")
        }

        fun Double.format(): String = if (isFinite()) String.format(java.util.Locale.ROOT, "%.4f", this) else toString()
    }
}
