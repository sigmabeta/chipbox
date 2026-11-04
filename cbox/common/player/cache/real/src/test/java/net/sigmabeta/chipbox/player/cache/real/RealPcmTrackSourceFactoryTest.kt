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
 * The point of the fixed-rate path is that a complete cache entry is reachable **before** staging
 * or constructing the native emulator, so a warm open performs zero native loads and zero native
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
    fun `known-rate warm hit returns the cached file with zero native loads and teardowns`() = runBlocking {
        val bytes = "main-bytes".toByteArray()
        val track = trackOf()
        val registry = registryOf()
        val emulator = counting(SAMPLE_RATE, SAMPLE_RATE)
        val factory = factoryOf(emulator, registry)
        seedCache(PcmCacheKey(hashOf(registry, track, bytes), track.trackNumber, SAMPLE_RATE))

        val source = factory.open(track, bytes)

        assertIs<CachedFilePcmSource>(source)
        assertEquals(0, emulator.loads, "a known-rate warm hit must not load the native track")
        assertEquals(0, emulator.teardowns, "a known-rate warm hit must not tear the native core down")
        assertFalse(
            fileSystem.exists(stagingDir / "track-${track.id}"),
            "a known-rate warm hit must not stage the source",
        )
        source.close()
    }

    @Test
    fun `null-rate backend still negotiates natively and serves a complete hit`() = runBlocking {
        val bytes = "main-bytes".toByteArray()
        val track = trackOf()
        val registry = registryOf()
        val reportedRate = 32_000
        val emulator = counting(knownSampleRate = null, reportedSampleRate = reportedRate)
        val factory = factoryOf(emulator, registry)
        seedCache(PcmCacheKey(hashOf(registry, track, bytes), track.trackNumber, reportedRate))

        val source = factory.open(track, bytes)

        assertIs<CachedFilePcmSource>(source)
        assertEquals(1, emulator.loads, "an unknown-rate backend must negotiate the rate natively")
        assertEquals(1, emulator.teardowns, "the negotiated emulator must be closed on the hit")
        assertFalse(
            fileSystem.exists(stagingDir / "track-${track.id}"),
            "closing the unused emulator source must remove its staging directory",
        )
        source.close()
    }

    @Test
    fun `rate mismatch rebuilds the key from the negotiated rate instead of aliasing the hint`() = runBlocking {
        val bytes = "main-bytes".toByteArray()
        val track = trackOf()
        val registry = registryOf()
        // The hint is deliberately wrong (44.1 kHz hint, 48 kHz native output). Only the entry at
        // the negotiated rate exists, so a correct implementation constructs the emulator,
        // notices the mismatch, rebuilds the key, and then serves that entry.
        val emulator = counting(SAMPLE_RATE, 48_000)
        val factory = factoryOf(emulator, registry)
        seedCache(
            PcmCacheKey(hashOf(registry, track, bytes), track.trackNumber, 48_000),
            frames = shortArrayOf(777, 777),
        )

        val source = factory.open(track, bytes)

        assertIs<CachedFilePcmSource>(source)
        val buffer = ShortArray(8)
        assertEquals(1, source.readFrames(buffer), "the entry at the negotiated rate should be readable")
        assertEquals(777.toShort(), buffer[0], "must read the actual-rate entry, not a stale alias")
        assertEquals(1, emulator.loads)
        assertEquals(1, emulator.teardowns)
        source.close()
    }

    @Test
    fun `changing a chain file invalidates the warm cache entry`() = runBlocking {
        val bytes = "main-bytes".toByteArray()
        val chainUri = "chain://game-lib"
        val contents = mutableMapOf<String, ByteArray>(chainUri to byteArrayOf(1, 2, 3))
        val registry = ContentSourceRegistry(setOf(MapContentSource("test", contents)))
        val track = trackOf(chainFiles = listOf(ChainFile(filename = "game.2sflib", uri = chainUri)))
        val emulator = counting(SAMPLE_RATE, SAMPLE_RATE)
        val factory = factoryOf(emulator, registry)
        val oldHash = hashOf(registry, track, bytes)
        seedCache(PcmCacheKey(oldHash, track.trackNumber, SAMPLE_RATE))

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

    private fun seedCache(key: PcmCacheKey, frames: ShortArray = shortArrayOf(111, 111, 222, 222)) {
        val writer = PcmCacheFile.openForWrite(
            fileSystem = fileSystem,
            cacheDir = cacheDir,
            key = key,
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
    private fun counting(knownSampleRate: Int?, reportedSampleRate: Int): CountingEmulator =
        CountingEmulator(
            FixedRateBackend(knownSampleRate = knownSampleRate, reportedSampleRate = reportedSampleRate),
        )

    /**
     * Deterministic native backend. [knownSampleRate] is the factory's non-native hint; the value
     * the native core would report is [reportedSampleRate].
     */
    private class FixedRateBackend(
        override val knownSampleRate: Int?,
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
    }
}
