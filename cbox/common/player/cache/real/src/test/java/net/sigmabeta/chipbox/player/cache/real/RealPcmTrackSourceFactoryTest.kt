package net.sigmabeta.chipbox.player.cache.real

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.sigmabeta.chipbox.contentsource.ContentSource
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.abrender.CountingEmulator
import net.sigmabeta.chipbox.models.ChainFile
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.cache.PcmCacheKey
import net.sigmabeta.chipbox.player.emulators.Emulator
import net.sigmabeta.sage.logging.BluntHatchet
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * Cache-identity and warm-open tests for [RealPcmTrackSourceFactory].
 *
 * The point is that a complete cache entry is reachable **before** staging or constructing the
 * native emulator, for every backend, so a warm open performs zero native loads and zero native
 * teardowns. These tests drive the real factory with a counting fake backend (not a mocked
 * factory) and a real on-disk cache entry, so they exercise the actual staging, hashing and
 * header-validation code.
 */
internal class RealPcmTrackSourceFactoryTest {

    private val fileSystem = FileSystem.SYSTEM
    private lateinit var workDir: File
    private lateinit var stagingDir: Path
    private lateinit var cacheDir: Path

    @BeforeTest
    fun setUp() {
        workDir = Files.createTempDirectory("real-pcm-factory-").toFile()
        stagingDir = File(workDir, "staging").toOkioPath()
        cacheDir = File(workDir, "pcm").toOkioPath()
    }

    @AfterTest
    fun tearDown() {
        workDir.deleteRecursively()
    }

    @Test
    fun `warm hit returns the cached file with zero native loads and teardowns`() = runBlocking {
        val bytes = "main-bytes".toByteArray()
        val track = trackOf()
        val registry = registryOf()
        val emulator = counting(renderVersion = RENDER_VERSION, reportedSampleRate = SAMPLE_RATE)
        val factory = factoryOf(emulator, registry)
        seedCache(PcmCacheKey(hashOf(registry, track, bytes), track.trackNumber, RENDER_VERSION))

        val source = factory.open(track, bytes)

        assertIs<CachedFilePcmSource>(source)
        assertEquals(0, emulator.loads, "a warm hit must not load the native track")
        assertEquals(0, emulator.teardowns, "a warm hit must not tear the native core down")
        assertFalse(
            fileSystem.exists(stagingDir / "track-${track.id}"),
            "a warm hit must not stage the source",
        )
        source.close()
    }

    @Test
    fun `warm hit takes its sample rate from the cache header, not the backend`() = runBlocking {
        val bytes = "main-bytes".toByteArray()
        val track = trackOf()
        val registry = registryOf()
        // The backend would report 44.1 kHz if loaded; the entry was rendered at 65536 Hz (a GSF
        // driver's settled rate). The hit must play it at the rate it was rendered at.
        val emulator = counting(renderVersion = RENDER_VERSION, reportedSampleRate = SAMPLE_RATE)
        val factory = factoryOf(emulator, registry)
        seedCache(
            PcmCacheKey(hashOf(registry, track, bytes), track.trackNumber, RENDER_VERSION),
            sampleRate = 65_536,
        )

        val source = factory.open(track, bytes)

        assertIs<CachedFilePcmSource>(source)
        assertEquals(65_536, source.sampleRate)
        assertEquals(0, emulator.loads)
        source.close()
    }

    @Test
    fun `bumping the render version misses the old entry`() = runBlocking {
        val bytes = "main-bytes".toByteArray()
        val track = trackOf()
        val registry = registryOf()
        val emulator = counting(renderVersion = RENDER_VERSION + 1, reportedSampleRate = SAMPLE_RATE)
        val factory = factoryOf(emulator, registry)
        seedCache(PcmCacheKey(hashOf(registry, track, bytes), track.trackNumber, RENDER_VERSION))

        val source = factory.open(track, bytes)

        assertIs<CachingPcmSource>(source, "an entry from an older render version must not be served")
        assertEquals(1, emulator.loads, "the miss must stage and construct the emulator")
        assertEquals(SAMPLE_RATE, source.sampleRate, "a fresh render runs at the backend's rate")
        source.close()
    }

    @Test
    fun `changing a chain file invalidates the warm cache entry`() = runBlocking {
        val bytes = "main-bytes".toByteArray()
        val chainUri = "chain://game-lib"
        val contents = mutableMapOf<String, ByteArray>(chainUri to byteArrayOf(1, 2, 3))
        val registry = ContentSourceRegistry(setOf(MapContentSource("test", contents)))
        val track = trackOf(chainFiles = listOf(ChainFile(filename = "game.2sflib", uri = chainUri)))
        val emulator = counting(renderVersion = RENDER_VERSION, reportedSampleRate = SAMPLE_RATE)
        val factory = factoryOf(emulator, registry)
        val oldHash = hashOf(registry, track, bytes)
        seedCache(PcmCacheKey(oldHash, track.trackNumber, RENDER_VERSION))

        // A byte-identical source with different chain-file bytes must not reuse the old entry.
        contents[chainUri] = byteArrayOf(9, 9, 9)

        val source = factory.open(track, bytes)

        assertIs<CachingPcmSource>(
            source,
            "a chain-file change must miss the cache and start a fresh render",
        )
        assertEquals(1, emulator.loads, "the miss must stage and construct the emulator")
        source.close()
    }

    // ---- helpers ----

    private suspend fun hashOf(
        registry: ContentSourceRegistry,
        track: Track,
        bytes: ByteArray,
    ): String = PcmCacheHasher(registry).hash(track, bytes)

    private fun factoryOf(
        emulator: Emulator,
        registry: ContentSourceRegistry,
    ): RealPcmTrackSourceFactory = RealPcmTrackSourceFactory(
        emulators = listOf(emulator),
        stagingDir = stagingDir,
        pcmCacheDir = cacheDir,
        fileSystem = fileSystem,
        contentSourceRegistry = registry,
        hatchet = BluntHatchet(),
        emulatorDispatcher = Dispatchers.Unconfined,
    )

    private fun seedCache(
        key: PcmCacheKey,
        frames: ShortArray = shortArrayOf(111, 111, 222, 222),
        sampleRate: Int = SAMPLE_RATE,
    ) {
        val writer = PcmCacheFile.openForWrite(
            fileSystem = fileSystem,
            cacheDir = cacheDir,
            key = key,
            sampleRate = sampleRate,
            trackId = 1L,
            trackLengthMs = 100L,
        )
        writer.appendFrames(frames, frames.size / 2)
        writer.complete(trackId = 1L, trackLengthMs = 100L, integratedLufs = -14.0, truePeakDbtp = -1.0)
        writer.promote()
    }

    private fun registryOf(): ContentSourceRegistry =
        ContentSourceRegistry(setOf(MapContentSource("test", mutableMapOf())))

    private fun trackOf(chainFiles: List<ChainFile> = emptyList()): Track = Track(
        id = 1L,
        path = "/library/track.2sf",
        source = "test",
        title = "Factory Track",
        trackLengthMs = 100L,
        trackNumber = 0,
        fadeLengthMs = 0L,
        game = null,
        artists = null,
        chainFiles = chainFiles,
        extension = "2sf",
        platform = Platform.NDS,
    )

    private class MapContentSource(
        override val sourceId: String,
        private val contents: Map<String, ByteArray>,
    ) : ContentSource {
        override suspend fun openBytes(identifier: String): ByteArray? = contents[identifier]
    }

    /** The shared counting decorator around a deterministic fixed-rate backend. */
    private fun counting(renderVersion: Int, reportedSampleRate: Int): CountingEmulator =
        CountingEmulator(
            FixedRateBackend(renderVersion = renderVersion, reportedSampleRate = reportedSampleRate),
        )

    /** Deterministic native backend; [reportedSampleRate] is what the native core would report. */
    private class FixedRateBackend(
        override val renderVersion: Int,
        private val reportedSampleRate: Int,
    ) : Emulator() {
        private var phase = 0

        override val supportedFileExtensions = listOf("2sf")

        override fun loadNativeLib() = Unit

        override fun loadTrackInternal(path: String) = Unit

        override fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int): Int {
            for (f in 0 until framesPerBuffer) {
                val sample = (phase++ % 1_000).toShort()
                buffer[f * 2] = sample
                buffer[f * 2 + 1] = sample
            }
            return framesPerBuffer
        }

        override fun teardownInternal() = Unit

        override fun getLastError(): String? = null

        override fun getSampleRateInternal(): Int = reportedSampleRate
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val RENDER_VERSION = 3
    }
}
