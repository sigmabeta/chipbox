package net.sigmabeta.chipbox.player.cache.real

import java.io.File
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.cache.PcmCacheKey
import net.sigmabeta.chipbox.player.emulators.Emulator
import net.sigmabeta.sage.logging.BluntHatchet
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the loudness ownership split introduced for the 2SF render-perf work: the live emulator
 * source can skip its own BS.1770 pass when the cache writer is going to measure the same frames
 * anyway, while an uncached / standalone emulator source keeps measuring for progressive
 * normalization.
 *
 * The third test is the guard that the skip did not remove measurement from the playback path:
 * a non-measuring [EmulatorPcmSource] wrapped in [CachingPcmSource] must still surface a finite
 * loudness figure, proving the surviving measurer is the one the cache consumes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class EmulatorPcmSourceLoudnessTest {

    private val fileSystem = FileSystem.SYSTEM
    private lateinit var workDir: File

    @BeforeTest
    fun setUp() {
        workDir = Files.createTempDirectory("emulator-pcm-loudness-").toFile()
    }

    @AfterTest
    fun tearDown() {
        workDir.deleteRecursively()
    }

    @Test
    fun `measureLoudness false leaves loudness and true peak at their defaults`() = runTest {
        val source = newEmulatorSource(measureLoudness = false)

        drain(source)

        assertTrue(source.loudnessLufs.isNaN(), "skipped measurement must leave LUFS unset")
        assertEquals(
            Double.NEGATIVE_INFINITY,
            source.truePeakDbtp,
            "skipped measurement must leave true peak unset",
        )
        source.close()
    }

    @Test
    fun `measureLoudness true measures audible audio`() = runTest {
        val source = newEmulatorSource(measureLoudness = true)

        drain(source)

        assertFalse(source.loudnessLufs.isNaN(), "measured source must report finite LUFS")
        assertTrue(
            source.truePeakDbtp > Double.NEGATIVE_INFINITY,
            "measured source must report a real true peak",
        )
        source.close()
    }

    @Test
    fun `CachingPcmSource measures frames the emulator source left unmeasured`() = runTest {
        val emulatorSource = newEmulatorSource(measureLoudness = false)

        val key = PcmCacheKey(sourceHash = "loudness", trackNumber = 0, sampleRate = SAMPLE_RATE)
        val writer = PcmCacheFile.openForWrite(
            fileSystem = fileSystem,
            cacheDir = workDir.absolutePath.toPath(),
            key = key,
            trackId = 1L,
            trackLengthMs = 0L,
        )
        val caching = CachingPcmSource(
            emulatorSource = emulatorSource,
            writer = writer,
            fileSystem = fileSystem,
            track = trackOf(),
            key = key,
            hatchet = BluntHatchet(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        // Let the render-ahead writer drain the emulator source.
        testScheduler.advanceUntilIdle()

        assertTrue(
            emulatorSource.loudnessLufs.isNaN(),
            "the inner source must not have measured when measureLoudness=false",
        )
        assertFalse(
            caching.loudnessLufs.isNaN(),
            "the cache writer must be the measurer that feeds loudness reporting",
        )
        assertTrue(
            caching.truePeakDbtp > Double.NEGATIVE_INFINITY,
            "the cache writer must report a real true peak",
        )
        caching.close()
    }

    private fun TestScope.newEmulatorSource(measureLoudness: Boolean): EmulatorPcmSource {
        val stagingTrackDir = workDir.absolutePath.toPath() / "track-1"
        fileSystem.createDirectories(stagingTrackDir)
        val stagedFile = stagingTrackDir / "main.bin"
        fileSystem.write(stagedFile) { writeUtf8("x") }
        return EmulatorPcmSource(
            emulator = SineEmulator(),
            track = trackOf(),
            stagedFile = stagedFile,
            stagingTrackDir = stagingTrackDir,
            fileSystem = fileSystem,
            hatchet = BluntHatchet(),
            emulatorDispatcher = UnconfinedTestDispatcher(testScheduler),
            measureLoudness = measureLoudness,
        )
    }

    private suspend fun drain(source: EmulatorPcmSource) {
        val buffer = ShortArray(BUFFER_FRAMES * 2)
        var guard = 0
        while (!source.isOver && guard++ < MAX_READS) {
            source.readFrames(buffer)
        }
        assertTrue(source.isOver, "the fake emulator should reach its declared track length")
    }

    /**
     * A full-scale 1 kHz sine at 48 kHz for [TRACK_LENGTH_MS], long enough to fill several
     * BS.1770 400 ms gating blocks. Only the loudness path cares about the waveform; the cache
     * tests just need frames.
     */
    private class SineEmulator : Emulator() {
        private var phase = 0.0

        override val supportedFileExtensions = listOf("bin")

        override fun loadNativeLib() = Unit

        override fun loadTrackInternal(path: String) = Unit

        override fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int): Int {
            for (f in 0 until framesPerBuffer) {
                val sample = (sin(2.0 * PI * SINE_HZ * phase / SAMPLE_RATE) * AMPLITUDE).toInt()
                buffer[f * 2] = sample.toShort()
                buffer[f * 2 + 1] = sample.toShort()
                phase += 1.0
            }
            return framesPerBuffer
        }

        override fun teardownInternal() = Unit

        override fun getLastError(): String? = null

        override fun getSampleRateInternal(): Int = SAMPLE_RATE
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val SINE_HZ = 1_000.0
        const val AMPLITUDE = 30_000.0
        const val TRACK_LENGTH_MS = 1_000L
        const val BUFFER_FRAMES = 4_096
        const val MAX_READS = 1_000

        fun trackOf(): Track = Track(
            id = 1L,
            path = "/library/track.bin",
            source = "test",
            title = "Loudness Track",
            trackLengthMs = TRACK_LENGTH_MS,
            trackNumber = 0,
            fadeLengthMs = 0L,
            game = null,
            artists = null,
            platform = Platform.OTHER,
        )
    }
}
