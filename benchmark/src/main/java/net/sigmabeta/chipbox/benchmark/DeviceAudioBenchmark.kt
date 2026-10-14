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
import net.sigmabeta.chipbox.perfbench.AudioScenario
import net.sigmabeta.chipbox.perfbench.PerfConfig
import net.sigmabeta.chipbox.perfbench.audioResult
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures the audio axis on a real device: real-time factor (RTF) for the representative emulator
 * subset ([AudioScenario.all] — USF, VGM, PSF), reusing the raw-core render path unchanged. For each
 * scenario it picks one representative track from the pushed corpus with the same [walkCorpus] the
 * host axis uses, warms up once, then times [REPEATS] renders and reduces them with [audioResult] —
 * identical math to the host axis, only the emulator cores differ (the arm64 `.so`s in this APK
 * rather than the host's x86_64 ones). It writes `results-audio.json` alongside the UI and startup
 * artifacts, carrying the same device fingerprint + corpus block via [DeviceBenchmark.perfResult].
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
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // The corpus lives under shared external storage, like the other device axes; reading it
        // needs the same All Files Access they grant themselves in their setup blocks.
        DeviceBenchmark.grantStorageAccess(UiDevice.getInstance(instrumentation))

        val environment = DeviceBenchmark.environment()
        val bank = EmulatorBank()
        val wavDir = File(instrumentation.context.cacheDir, SCRATCH_DIR).apply { mkdirs() }
        val scenarios = AudioScenario.all

        val results = mutableListOf<AudioResult>()
        for (scenario in scenarios) {
            val track = representativeTrack(bank, DeviceBenchmark.corpusRoot(), scenario) ?: continue
            measureScenario(bank, track, scenario, wavDir)?.let { results += audioResult(it) }
        }

        DeviceBenchmark.write(
            DeviceBenchmark.perfResult(
                environment = environment,
                config = PerfConfig(scenarios = scenarios.map { it.id }),
                axes = mapOf(
                    "audio" to AudioAxis(
                        config = AudioAxisConfig(sampleSeconds = SAMPLE_SECONDS, repeats = REPEATS),
                        results = results,
                    ),
                ),
            ),
        )
    }

    /** One track per scenario, chosen by the same deterministic walker the host axis and A/B run use. */
    private fun representativeTrack(
        bank: EmulatorBank,
        corpusDir: File,
        scenario: AudioScenario,
    ): SelectedTrack? = walkCorpus(
        root = corpusDir,
        supported = bank.supportedExtensions,
        extensionFilter = scenario.extensions,
        gameFilter = null,
        everySong = false,
        subsong = 0,
    ).firstOrNull()

    /** Warm-up once (untimed), then [REPEATS] timed renders; `null` if every repeat failed. */
    private fun measureScenario(
        bank: EmulatorBank,
        track: SelectedTrack,
        scenario: AudioScenario,
        wavDir: File,
    ): AudioMeasurement? {
        val wav = File(wavDir, "${scenario.id}.wav")
        val warmUp = bank.render(track, SAMPLE_SECONDS, wav, MAX_WALL_MILLIS)
        if (warmUp.error.isNotEmpty()) return null

        val wallSeconds = mutableListOf<Double>()
        val cpuSeconds = mutableListOf<Double>()
        var frames = warmUp.frames
        var sampleRate = warmUp.sampleRate
        repeat(REPEATS) {
            val wallStart = System.nanoTime()
            val cpuStart = Debug.threadCpuTimeNanos()
            val metrics = bank.render(track, SAMPLE_SECONDS, wav, MAX_WALL_MILLIS)
            val wall = (System.nanoTime() - wallStart) / NANOS_PER_SECOND
            val cpuEnd = Debug.threadCpuTimeNanos()
            if (metrics.error.isNotEmpty()) return@repeat
            frames = metrics.frames
            sampleRate = metrics.sampleRate
            wallSeconds += wall
            if (cpuStart >= 0 && cpuEnd >= 0) cpuSeconds += (cpuEnd - cpuStart) / NANOS_PER_SECOND
        }

        return wallSeconds.takeIf { it.isNotEmpty() }?.let {
            AudioMeasurement(scenario.id, frames, sampleRate, it, cpuSeconds)
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
