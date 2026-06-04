package net.sigmabeta.chipbox.player.cache.real

import java.io.File
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.emulators.Emulator
import net.sigmabeta.sage.logging.BluntHatchet
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Confinement guard: every native-core call an [EmulatorPcmSource] makes must run on the single
 * emulator thread, no matter which thread drives the source. The emulators are process-wide
 * singletons with no internal locking, so a generate racing a teardown/load is a use-after-free
 * that crashes the native cores (seen as SIGSEGV in the GBA/USF cores during rapid skips). The
 * factory constructs the source on the emulator dispatcher; here we replicate that and then drive
 * it from the test thread, asserting nothing reached into native off-thread.
 */
internal class EmulatorPcmSourceConfinementTest {

    private val fileSystem = FileSystem.SYSTEM
    private lateinit var workDir: File

    @BeforeTest
    fun setUp() {
        workDir = Files.createTempDirectory("emulator-pcm-confinement-").toFile()
    }

    @AfterTest
    fun tearDown() {
        workDir.deleteRecursively()
    }

    @Test
    fun `every native call runs on the emulator dispatcher thread`() = runBlocking {
        val emulatorThreadName = "test-emulator-thread"
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, emulatorThreadName)
        }
        val dispatcher = executor.asCoroutineDispatcher()
        try {
            val emulator = ThreadRecordingEmulator()
            val stagingTrackDir = workDir.absolutePath.toPath() / "track-1"
            fileSystem.createDirectories(stagingTrackDir)
            val stagedFile = stagingTrackDir / "main.bin"
            fileSystem.write(stagedFile) { writeUtf8("x") }

            // Construct on the emulator thread, exactly as the factory does (the constructor calls
            // into native via loadTrack).
            val source = withContext(dispatcher) {
                EmulatorPcmSource(
                    emulator = emulator,
                    track = trackOf(),
                    stagedFile = stagedFile,
                    stagingTrackDir = stagingTrackDir,
                    fileSystem = fileSystem,
                    hatchet = BluntHatchet(),
                    emulatorDispatcher = dispatcher,
                )
            }

            // Drive from the test (main) thread — confinement must still hop to the emulator thread.
            source.readFrames(ShortArray(BUFFER_SHORTS))
            source.close()

            assertTrue(emulator.callThreads.isNotEmpty(), "the source should have called into native")
            // The coroutines debug agent appends " @coroutine#N" to the thread name; strip it to
            // compare the underlying OS thread.
            val osThreads = emulator.callThreads.map { it.substringBefore(" @") }.toSet()
            assertEquals(
                setOf(emulatorThreadName),
                osThreads,
                "all native calls must run on the emulator thread; saw ${emulator.callThreads}",
            )
        } finally {
            executor.shutdown()
        }
    }

    /** Records the name of the thread each native call lands on. */
    private class ThreadRecordingEmulator : Emulator() {
        val callThreads: MutableList<String> = Collections.synchronizedList(mutableListOf())

        private fun record() {
            callThreads.add(Thread.currentThread().name)
        }

        override val supportedFileExtensions = listOf("bin")
        override fun loadNativeLib() = record()
        override fun loadTrackInternal(path: String) = record()
        override fun teardownInternal() = record()

        override fun getLastError(): String? {
            record()
            return null
        }

        override fun getDiagnostics(): String? {
            record()
            return null
        }

        override fun getSampleRateInternal(): Int {
            record()
            return SAMPLE_RATE
        }

        override fun setTrackNumber(number: Int) = record()

        override fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int): Int {
            record()
            return framesPerBuffer
        }
    }

    private companion object {
        const val SAMPLE_RATE = 32_000
        const val BUFFER_SHORTS = 8

        fun trackOf(): Track = Track(
            id = 1L,
            path = "/library/track.bin",
            source = "test",
            title = "Track",
            trackLengthMs = 60_000L,
            trackNumber = 0,
            fadeLengthMs = 0L,
            game = null,
            artists = null,
            platform = Platform.OTHER,
        )
    }
}
