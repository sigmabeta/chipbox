package net.sigmabeta.chipbox.benchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scrolls the BrowseByGame screen — chipbox's main `GridImage` surface — and
 * records frame timing plus a few targeted Compose section metrics. Run via:
 *
 *   ./gradlew :benchmark:connectedBenchmarkAndroidTest
 *
 * The captured Perfetto trace per iteration lands under
 *   benchmark/build/outputs/connected_android_test_additional_output/.../
 * and can be opened directly in https://ui.perfetto.dev.
 */
@RunWith(AndroidJUnit4::class)
class GridImageScrollBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun browseByGameScroll() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            FrameTimingMetric(),
            // Mode.Sum totals time spent in this section across the iteration.
            TraceSectionMetric("GridImage", TraceSectionMetric.Mode.Sum),
            TraceSectionMetric("CrossfadeImage", TraceSectionMetric.Mode.Sum),
        ),
        iterations = ITERATIONS,
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

    private companion object {
        const val TARGET_PACKAGE = "net.sigmabeta.chipbox"
        const val ITERATIONS = 5
        const val NAV_TIMEOUT_MS = 10_000L
        const val FLINGS_PER_DIRECTION = 3
        const val GESTURE_MARGIN_DIVISOR = 5
    }
}
