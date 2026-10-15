package net.sigmabeta.chipbox.benchmark

import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import net.sigmabeta.chipbox.abrender.EmulatorBank
import net.sigmabeta.chipbox.abrender.SelectedTrack
import net.sigmabeta.chipbox.abrender.walkCorpus
import net.sigmabeta.chipbox.perfbench.AudioAxis
import net.sigmabeta.chipbox.perfbench.AudioAxisConfig
import net.sigmabeta.chipbox.perfbench.AudioMeasurement
import net.sigmabeta.chipbox.perfbench.AudioResult
import net.sigmabeta.chipbox.perfbench.PerfConfig
import net.sigmabeta.chipbox.perfbench.audioResult
import net.sigmabeta.chipbox.perfbench.corpusRelativePath
import net.sigmabeta.chipbox.perfbench.erroredAudioResult
import net.sigmabeta.chipbox.perfbench.scratchWavName
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures the audio axis on a real device: real-time factor (RTF) for every supported file in the
 * pushed corpus, one result per file keyed by its corpus-relative path, reusing the raw-core render
 * path unchanged. For each file it warms up once, then times [REPEATS] renders and reduces them with
 * [audioResult] — identical math to the host axis, only the emulator cores differ (the arm64 `.so`s
 * in this APK rather than the host's x86_64 ones). A file that fails to render is recorded as an
 * errored result rather than dropped, so a run never silently shrinks. It writes `results-audio.json`
 * alongside the UI and startup artifacts, carrying the same device fingerprint + corpus block via
 * [DeviceBenchmark.perfResult].
 *
 * RTF is the one device axis that is inherently machine-independent, so this is the device ground
 * truth for the host audio measurements. The pushed corpus + snapshot described in the module README
 * must be in place first. Run via:
 *
 *   ./gradlew :benchmark:connectedBenchmarkAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class DeviceAudioBenchmark {

    @Test
    fun audioRealTimeFactor() {
        DeviceBenchmark.beginTest("audioRealTimeFactor")
        try {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            // The corpus lives under shared external storage, like the other device axes; reading it
            // needs the same All Files Access they grant themselves in their setup blocks.
            DeviceBenchmark.section("grant-storage") {
                DeviceBenchmark.grantStorageAccess(UiDevice.getInstance(instrumentation))
            }

            val environment = DeviceBenchmark.environment()
            val bank = EmulatorBank()
            val corpusRoot = DeviceBenchmark.corpusRoot()
            val wavDir = File(instrumentation.context.cacheDir, SCRATCH_DIR).apply { mkdirs() }

            val tracks = DeviceBenchmark.section("walk-corpus") {
                walkCorpus(
                    root = corpusRoot,
                    supported = bank.supportedExtensions,
                    extensionFilter = null,
                    gameFilter = null,
                    everySong = true,
                    subsong = 0,
                )
            }

            val results = tracks.map { track ->
                val path = corpusRelativePath(corpusRoot, track.path)
                DeviceBenchmark.section("render", "path" to path) {
                    measureTrack(bank, track, path, wavDir)
                }
            }

            DeviceBenchmark.section("write-result") {
                DeviceBenchmark.write(
                    DeviceBenchmark.perfResult(
                        environment = environment,
                        config = PerfConfig(scenarios = results.map { it.path }),
                        axes = mapOf(
                            "audio" to AudioAxis(
                                config = AudioAxisConfig(sampleSeconds = SAMPLE_SECONDS, repeats = REPEATS),
                                results = results,
                            ),
                        ),
                    ),
                )
            }
        } finally {
            DeviceBenchmark.endTest("audioRealTimeFactor")
        }
    }

    /**
     * Warms up once (untimed), then times [REPEATS] renders, reducing them to one result. A warm-up
     * failure, or all repeats failing, becomes an errored result carrying the failure — never an
     * omission.
     */
    private fun measureTrack(
        bank: EmulatorBank,
        track: SelectedTrack,
        path: String,
        wavDir: File,
    ): AudioResult {
        val wav = File(wavDir, scratchWavName(path))
        val warmUp = bank.render(track, SAMPLE_SECONDS, wav, MAX_WALL_MILLIS)
        if (warmUp.error.isNotEmpty()) return erroredAudioResult(path, warmUp.error)

        val wallSeconds = mutableListOf<Double>()
        val cpuSeconds = mutableListOf<Double>()
        var frames = warmUp.frames
        var sampleRate = warmUp.sampleRate
        var repeatError: String? = null
        repeat(REPEATS) {
            val wallStart = System.nanoTime()
            val cpuStart = Debug.threadCpuTimeNanos()
            val metrics = bank.render(track, SAMPLE_SECONDS, wav, MAX_WALL_MILLIS)
            val wall = (System.nanoTime() - wallStart) / NANOS_PER_SECOND
            val cpuEnd = Debug.threadCpuTimeNanos()
            if (metrics.error.isNotEmpty()) {
                repeatError = metrics.error
                return@repeat
            }
            frames = metrics.frames
            sampleRate = metrics.sampleRate
            wallSeconds += wall
            if (cpuStart >= 0 && cpuEnd >= 0) cpuSeconds += (cpuEnd - cpuStart) / NANOS_PER_SECOND
        }

        return if (wallSeconds.isEmpty()) {
            erroredAudioResult(path, repeatError ?: "no timed repeat produced audio")
        } else {
            audioResult(AudioMeasurement(path, frames, sampleRate, wallSeconds, cpuSeconds))
        }
    }

    private companion object {
        const val SAMPLE_SECONDS = 30
        const val REPEATS = 3
        const val MILLIS_PER_SECOND = 1000L
        const val MAX_WALL_MILLIS = 120L * MILLIS_PER_SECOND
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val SCRATCH_DIR = "perfbench-audio"
    }
}
