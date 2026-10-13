package net.sigmabeta.chipbox.benchmark

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.benchmark.ExperimentalBenchmarkConfigApi
import androidx.benchmark.json.BenchmarkData
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.macrobenchmarkWithStartupMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import java.io.File
import java.time.OffsetDateTime
import kotlinx.serialization.encodeToString
import net.sigmabeta.chipbox.perfbench.CorpusRef
import net.sigmabeta.chipbox.perfbench.DeviceBenchmarkLayout
import net.sigmabeta.chipbox.perfbench.DeviceEnvironment
import net.sigmabeta.chipbox.perfbench.PerfConfig
import net.sigmabeta.chipbox.perfbench.PerfResult
import net.sigmabeta.chipbox.perfbench.PerfbenchJson
import net.sigmabeta.chipbox.perfbench.TraceSectionMeasurement
import net.sigmabeta.chipbox.perfbench.UiAxis
import net.sigmabeta.chipbox.perfbench.UiAxisConfig
import net.sigmabeta.chipbox.perfbench.UiMeasurement
import net.sigmabeta.chipbox.perfbench.UiScenario
import net.sigmabeta.chipbox.perfbench.deviceFingerprint
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
        val environment = deviceEnvironment()

        val testResult = macrobenchmarkWithStartupMode(
            uniqueName = "GridImageScrollBenchmark_browseByGameScroll",
            className = this::class.java.name,
            testName = "browseByGameScroll",
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                FrameTimingMetric(),
                // Mode.Sum totals time spent in this section across the iteration.
                TraceSectionMetric("GridImage", TraceSectionMetric.Mode.Sum),
                TraceSectionMetric("CrossfadeImage", TraceSectionMetric.Mode.Sum),
            ),
            compilationMode = CompilationMode.DEFAULT,
            iterations = ITERATIONS,
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

        writeResultsJson(testResult, environment)
    }

    /** Assembles and writes the perfbench `results.json` for one measured scroll. */
    private fun writeResultsJson(
        testResult: BenchmarkData.TestResult,
        environment: DeviceEnvironment,
    ): PerfResult {
        val commits = deviceCommits()
        val result = PerfResult(
            generated = OffsetDateTime.now().toString(),
            chipboxCommit = commits.first,
            sageCommit = commits.second,
            config = PerfConfig(
                repeat = ITERATIONS,
                scenarios = listOf(UiScenario.BROWSE_BY_GAME_SCROLL.id),
            ),
            machine = deviceFingerprint(environment, commits.first, commits.second),
            // A UI run reads no audio, so the manifest is empty; the root records the fixed device
            // corpus dir the snapshot was rebased onto. The seed's snapshot hash is app-side and
            // not visible to this process, so it is omitted here.
            corpus = CorpusRef(root = deviceCorpusRoot().absolutePath),
            axes = mapOf(
                "ui" to UiAxis(
                    config = UiAxisConfig(iterations = ITERATIONS),
                    results = listOf(uiResult(uiMeasurement(testResult))),
                ),
            ),
        )

        val outFile = File(resultsOutputDir(), RESULTS_FILE_NAME)
        outFile.parentFile?.mkdirs()
        outFile.writeText(PerfbenchJson.encodeToString(result))
        return result
    }

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

    /** The device facts a [DeviceEnvironment] records, probed from the running benchmark process. */
    private fun deviceEnvironment(): DeviceEnvironment = DeviceEnvironment(
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        androidBuild = Build.FINGERPRINT,
        abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
        thermalStatus = thermalStatusName(InstrumentationRegistry.getInstrumentation().targetContext),
    )

    /** The two projected commits, passed as instrumentation arguments from the build (else unknown). */
    private fun deviceCommits(): Pair<String, String> {
        val args = InstrumentationRegistry.getArguments()
        return (args.getString(CHIPBOX_COMMIT_ARG) ?: UNKNOWN) to (args.getString(SAGE_COMMIT_ARG) ?: UNKNOWN)
    }

    /**
     * The fixed location the benchmark build seeds its library from (see [DeviceBenchmarkLayout]),
     * as an absolute path under the target app's external files dir. The macrobenchmark process
     * can't read that directory on modern Android, so it constructs the path for the record only.
     */
    private fun deviceCorpusRoot(): File = File(
        File(externalStorageDir(), "Android/data/$TARGET_PACKAGE/files"),
        "${DeviceBenchmarkLayout.ROOT_DIR}/${DeviceBenchmarkLayout.CORPUS_DIR_NAME}",
    )

    @Suppress("DEPRECATION")
    private fun externalStorageDir(): File = android.os.Environment.getExternalStorageDirectory()

    /** The directory AGP pulls additional test output from, falling back to app files when unset. */
    private fun resultsOutputDir(): File {
        val additional = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        return if (additional != null) {
            File(additional)
        } else {
            File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "perfbench")
        }
    }

    /** The platform thermal-status label, or `"unknown"` on API levels that can't report it. */
    private fun thermalStatusName(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return UNKNOWN
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return UNKNOWN
        return when (powerManager.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "none"
            PowerManager.THERMAL_STATUS_LIGHT -> "light"
            PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
            else -> UNKNOWN
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "net.sigmabeta.chipbox"
        const val ITERATIONS = 5
        const val NAV_TIMEOUT_MS = 10_000L
        const val FLINGS_PER_DIRECTION = 3
        const val GESTURE_MARGIN_DIVISOR = 5
        const val RESULTS_FILE_NAME = "results.json"
        const val FRAME_DURATION_METRIC = "frameDurationCpuMs"
        const val FRAME_OVERRUN_METRIC = "frameOverrunMs"
        const val CHIPBOX_COMMIT_ARG = "chipboxCommit"
        const val SAGE_COMMIT_ARG = "sageCommit"
        const val UNKNOWN = "unknown"
        val TRACE_SECTIONS = listOf("GridImage", "CrossfadeImage")
    }
}
