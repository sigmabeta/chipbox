package net.sigmabeta.chipbox.benchmark

import android.annotation.SuppressLint
import androidx.benchmark.ExperimentalBenchmarkConfigApi
import androidx.benchmark.json.BenchmarkData
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.macrobenchmarkWithStartupMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.sigmabeta.chipbox.perfbench.PerfConfig
import net.sigmabeta.chipbox.perfbench.StartupAxis
import net.sigmabeta.chipbox.perfbench.StartupAxisConfig
import net.sigmabeta.chipbox.perfbench.StartupMeasurement
import net.sigmabeta.chipbox.perfbench.StartupResult
import net.sigmabeta.chipbox.perfbench.StartupScenario
import net.sigmabeta.chipbox.perfbench.startupResult
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures app startup — cold and warm — for the library grid, and writes a perfbench `results.json`
 * (device fingerprint + `startup` axis) to the run's additional test output directory.
 *
 * "First content" is the library grid showing its first items: the app's Home launch screen renders
 * game-cover grids, and in the `benchmark` build type the app calls `Activity.reportFullyDrawn()`
 * the first time a grid cell composes, which the macrobenchmark's [StartupTimingMetric] records as
 * `timeToFullDisplayMs`. That time-to-full-display is what this benchmark reports as
 * time-to-first-content. Navigating elsewhere would measure startup *plus* navigation, not startup.
 *
 * The benchmark build must be seeded from a pushed snapshot first — see the module README. Run via:
 *
 *   ./gradlew :benchmark:connectedBenchmarkAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class LibraryStartupBenchmark {

    // Suppressed because the JUnit4 rule discards the typed result this needs, so it calls the
    // macrobenchmark entry point the rule wraps directly.
    @SuppressLint("RestrictedApi")
    @OptIn(ExperimentalMetricApi::class, ExperimentalBenchmarkConfigApi::class)
    @Test
    fun coldAndWarmStartup() {
        // Probe the device facts (including the thermal status "at start") before the measured
        // loop; the fingerprint must not record the post-run thermal state.
        val environment = DeviceBenchmark.environment()
        val results = StartupScenario.all.map { startup(scenario = it) }

        DeviceBenchmark.write(
            DeviceBenchmark.perfResult(
                environment = environment,
                config = PerfConfig(
                    repeat = DeviceBenchmark.ITERATIONS,
                    scenarios = StartupScenario.all.map { it.id },
                ),
                axes = mapOf(
                    "startup" to StartupAxis(
                        config = StartupAxisConfig(iterations = DeviceBenchmark.ITERATIONS),
                        results = results,
                    ),
                ),
            ),
        )
    }

    /** Runs one measured startup scenario and returns its time-to-first-content result. */
    @SuppressLint("RestrictedApi")
    @OptIn(ExperimentalMetricApi::class, ExperimentalBenchmarkConfigApi::class)
    private fun startup(scenario: StartupScenario): StartupResult {
        val mode = when (scenario) {
            StartupScenario.COLD -> StartupMode.COLD
            StartupScenario.WARM -> StartupMode.WARM
        }
        val testName = "${scenario.name.lowercase()}Startup"
        val testResult = macrobenchmarkWithStartupMode(
            uniqueName = "LibraryStartupBenchmark_$testName",
            className = this::class.java.name,
            testName = testName,
            packageName = DeviceBenchmark.TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.DEFAULT,
            iterations = DeviceBenchmark.ITERATIONS,
            experimentalConfig = null,
            startupMode = mode,
            setupBlock = {
                DeviceBenchmark.grantStorageAccess(device)
                pressHome()
            },
        ) {
            startActivityAndWait()
        }

        return startupResult(startupMeasurement(scenario.id, testResult))
    }

    /** Pools the macrobenchmark's launch samples into a [StartupMeasurement]. */
    private fun startupMeasurement(scenario: String, testResult: BenchmarkData.TestResult): StartupMeasurement {
        // StartupTimingMetric emits a *single* metric (one value per launch), so it lands in
        // `metrics` — not `sampledMetrics`, which is for multi-sample metrics like frame timing.
        // timeToFullDisplayMs exists only if the app reported itself fully drawn.
        val samples = testResult.metrics[FULL_DISPLAY_METRIC]?.runs.orEmpty()
        check(samples.isNotEmpty()) {
            "No $FULL_DISPLAY_METRIC samples — the benchmark build must report the app fully drawn " +
                "when the library grid shows its first item. Is BenchmarkLibrarySeed seeded and " +
                "LocalFirstContentReporter wired?"
        }
        return StartupMeasurement(scenario = scenario, timeToFirstContentMillis = samples)
    }

    private companion object {
        const val FULL_DISPLAY_METRIC = "timeToFullDisplayMs"
    }
}
