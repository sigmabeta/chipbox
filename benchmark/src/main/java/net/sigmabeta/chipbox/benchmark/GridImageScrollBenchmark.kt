package net.sigmabeta.chipbox.benchmark

import android.annotation.SuppressLint
import androidx.benchmark.ExperimentalBenchmarkConfigApi
import androidx.benchmark.json.BenchmarkData
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.macrobenchmarkWithStartupMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import net.sigmabeta.chipbox.perfbench.DeviceEnvironment
import net.sigmabeta.chipbox.perfbench.PerfConfig
import net.sigmabeta.chipbox.perfbench.PerfResult
import net.sigmabeta.chipbox.perfbench.TraceSectionMeasurement
import net.sigmabeta.chipbox.perfbench.UiAxis
import net.sigmabeta.chipbox.perfbench.UiAxisConfig
import net.sigmabeta.chipbox.perfbench.UiMeasurement
import net.sigmabeta.chipbox.perfbench.UiScenario
import net.sigmabeta.chipbox.perfbench.uiResult
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scrolls the BrowseByGame screen — chipbox's main `GridImage` surface — and records frame timing
 * plus the `GridImage` / `CrossfadeImage` trace-section sums behind the app's `TraceSection`s. The
 * frame metrics and the labelled trace sums are written to a perfbench `results.json` (device
 * fingerprint + `ui` axis) in the run's additional test output directory.
 *
 * The benchmark build must be seeded from a pushed snapshot first — see the module README. Run via:
 *
 *   ./gradlew :benchmark:connectedBenchmarkAndroidTest
 *
 * The captured Perfetto trace per iteration lands under
 *   benchmark/build/outputs/connected_android_test_additional_output/.../
 * and can be opened directly in https://ui.perfetto.dev.
 */
@RunWith(AndroidJUnit4::class)
class GridImageScrollBenchmark {

    // Suppressed because the JUnit4 rule discards the typed result this needs, so it calls the
    // macrobenchmark entry point the rule wraps directly.
    @SuppressLint("RestrictedApi")
    @OptIn(ExperimentalMetricApi::class, ExperimentalBenchmarkConfigApi::class)
    @Test
    fun browseByGameScroll() {
        // Probe the device facts (including the thermal status "at start") before the measured
        // loop; the fingerprint must not record the post-run thermal state.
        val environment = DeviceBenchmark.environment()

        val testResult = macrobenchmarkWithStartupMode(
            uniqueName = "GridImageScrollBenchmark_browseByGameScroll",
            className = this::class.java.name,
            testName = "browseByGameScroll",
            packageName = DeviceBenchmark.TARGET_PACKAGE,
            metrics = listOf(
                FrameTimingMetric(),
                // Mode.Sum totals time spent in this section across the iteration.
                TraceSectionMetric("GridImage", TraceSectionMetric.Mode.Sum),
                TraceSectionMetric("CrossfadeImage", TraceSectionMetric.Mode.Sum),
            ),
            compilationMode = CompilationMode.DEFAULT,
            iterations = DeviceBenchmark.ITERATIONS,
            experimentalConfig = null,
            startupMode = StartupMode.WARM,
            setupBlock = {
                pressHome()
                startActivityAndWait()

                val byGame = device.wait(
                    Until.findObject(By.text("Browse by Game")),
                    NAV_TIMEOUT_MS,
                ) ?: error("Could not find 'Browse by Game' on Library screen")
                byGame.click()

                // Wait for the GridImage grid to appear and settle.
                device.wait(Until.hasObject(By.scrollable(true)), NAV_TIMEOUT_MS)
                device.waitForIdle()
            },
        ) {
            val grid = device.findObject(By.scrollable(true))
                ?: error("No scrollable container on BrowseByGame")
            grid.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)

            repeat(FLINGS_PER_DIRECTION) { grid.fling(Direction.DOWN) }
            repeat(FLINGS_PER_DIRECTION) { grid.fling(Direction.UP) }
        }

        DeviceBenchmark.write(perfResult(testResult, environment))
    }

    /** Assembles the perfbench `results.json` for one measured scroll. */
    private fun perfResult(
        testResult: BenchmarkData.TestResult,
        environment: DeviceEnvironment,
    ): PerfResult = DeviceBenchmark.perfResult(
        environment = environment,
        config = PerfConfig(
            repeat = DeviceBenchmark.ITERATIONS,
            scenarios = listOf(UiScenario.BROWSE_BY_GAME_SCROLL.id),
        ),
        axes = mapOf(
            "ui" to UiAxis(
                config = UiAxisConfig(iterations = DeviceBenchmark.ITERATIONS),
                results = listOf(uiResult(uiMeasurement(testResult))),
            ),
        ),
    )

    /** Pools the macrobenchmark's frame samples and labelled trace sums into a [UiMeasurement]. */
    private fun uiMeasurement(testResult: BenchmarkData.TestResult): UiMeasurement = UiMeasurement(
        scenario = UiScenario.BROWSE_BY_GAME_SCROLL.id,
        // FrameTimingMetric reports frameDurationCpuMs/frameOverrunMs as pooled samples, one list
        // per iteration; flatten so percentiles and jank come from every measured frame.
        frameCpuMillis = testResult.sampledMetrics[FRAME_DURATION_METRIC]?.runs?.flatten().orEmpty(),
        frameOverrunMillis = testResult.sampledMetrics[FRAME_OVERRUN_METRIC]?.runs?.flatten().orEmpty(),
        traceSections = TRACE_SECTIONS.mapNotNull { name ->
            val sum = testResult.metrics["${name}SumMs"] ?: return@mapNotNull null
            val count = testResult.metrics["${name}Count"]
            TraceSectionMeasurement(name = name, sumMs = sum.median, count = count?.median?.toInt() ?: 0)
        },
    )

    private companion object {
        const val NAV_TIMEOUT_MS = 10_000L
        const val FLINGS_PER_DIRECTION = 3
        const val GESTURE_MARGIN_DIVISOR = 5
        const val FRAME_DURATION_METRIC = "frameDurationCpuMs"
        const val FRAME_OVERRUN_METRIC = "frameOverrunMs"
        val TRACE_SECTIONS = listOf("GridImage", "CrossfadeImage")
    }
}
