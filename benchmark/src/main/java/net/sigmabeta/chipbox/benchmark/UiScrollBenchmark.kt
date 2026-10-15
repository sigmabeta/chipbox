package net.sigmabeta.chipbox.benchmark

import android.annotation.SuppressLint
import android.os.SystemClock
import androidx.benchmark.ExperimentalBenchmarkConfigApi
import androidx.benchmark.json.BenchmarkData
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.macrobenchmarkWithStartupMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import net.sigmabeta.chipbox.perfbench.DeviceEnvironment
import net.sigmabeta.chipbox.perfbench.PerfConfig
import net.sigmabeta.chipbox.perfbench.PerfResult
import net.sigmabeta.chipbox.perfbench.TraceSectionMeasurement
import net.sigmabeta.chipbox.perfbench.UiAxis
import net.sigmabeta.chipbox.perfbench.UiAxisConfig
import net.sigmabeta.chipbox.perfbench.UiMeasurement
import net.sigmabeta.chipbox.perfbench.UiResult
import net.sigmabeta.chipbox.perfbench.UiScenario
import net.sigmabeta.chipbox.perfbench.uiResult
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scrolls each core chipbox surface — the BrowseByGame grid, Home, the BrowseAllTracks list, and
 * artist detail — and records frame timing plus the trace-section sums behind the app's
 * `TraceSection`s (none for the text-only tracks list). Every scenario is written into one perfbench
 * `results-ui.json` (device fingerprint + `ui` axis) in the run's additional test output directory,
 * so no scenario overwrites another.
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
class UiScrollBenchmark {

    // Suppressed because the JUnit4 rule discards the typed result this needs, so it calls the
    // macrobenchmark entry point the rule wraps directly.
    @SuppressLint("RestrictedApi")
    @OptIn(ExperimentalMetricApi::class, ExperimentalBenchmarkConfigApi::class)
    @Test
    fun scrollScenarios() {
        // Probe the device facts (including the thermal status "at start") before the measured
        // loop; the fingerprint must not record the post-run thermal state.
        val environment = DeviceBenchmark.environment()
        val results = UiScenario.all.map { scenario ->
            runScenario(SCENARIOS.first { it.scenario == scenario })
        }

        DeviceBenchmark.write(
            DeviceBenchmark.perfResult(
                environment = environment,
                config = PerfConfig(scenarios = UiScenario.all.map { it.id }),
                axes = mapOf(
                    "ui" to UiAxis(
                        config = UiAxisConfig(iterations = DeviceBenchmark.ITERATIONS),
                        results = results,
                    ),
                ),
            ),
        )
    }

    /** Runs one measured scroll scenario and returns its frame + trace-section result. */
    @SuppressLint("RestrictedApi")
    @OptIn(ExperimentalMetricApi::class, ExperimentalBenchmarkConfigApi::class)
    private fun runScenario(scenario: UiScrollScenario): UiResult {
        val testResult = macrobenchmarkWithStartupMode(
            uniqueName = "UiScrollBenchmark_${scenario.testName}",
            className = this::class.java.name,
            testName = scenario.testName,
            packageName = DeviceBenchmark.TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()) +
                scenario.traceSections.map { TraceSectionMetric(it, TraceSectionMetric.Mode.Sum) },
            compilationMode = CompilationMode.DEFAULT,
            iterations = DeviceBenchmark.ITERATIONS,
            experimentalConfig = null,
            startupMode = StartupMode.WARM,
            setupBlock = {
                DeviceBenchmark.grantStorageAccess(device)
                pressHome()
                startActivityAndWait()
                scenario.navigate(this)
            },
        ) {
            scenario.gesture(this)
        }

        return uiResult(uiMeasurement(scenario, testResult))
    }

    /** Pools the macrobenchmark's frame samples and labelled trace sums into a [UiMeasurement]. */
    private fun uiMeasurement(scenario: UiScrollScenario, testResult: BenchmarkData.TestResult): UiMeasurement =
        UiMeasurement(
            scenario = scenario.scenario.id,
            // FrameTimingMetric reports frameDurationCpuMs/frameOverrunMs as pooled samples, one
            // list per iteration; flatten so percentiles and jank come from every measured frame.
            frameCpuMillis = testResult.sampledMetrics[FRAME_DURATION_METRIC]?.runs?.flatten().orEmpty(),
            frameOverrunMillis = testResult.sampledMetrics[FRAME_OVERRUN_METRIC]?.runs?.flatten().orEmpty(),
            traceSections = scenario.traceSections.mapNotNull { name ->
                val sum = testResult.metrics["${name}SumMs"] ?: return@mapNotNull null
                val count = testResult.metrics["${name}Count"]
                TraceSectionMeasurement(name = name, sumMs = sum.median, count = count?.median?.toInt() ?: 0)
            },
        )

    /**
     * One scroll scenario's device recipe: how to navigate to its screen, the gesture to run inside
     * the measured loop, and the trace sections that screen emits. Keeping all three together makes
     * adding a screen a local change.
     */
    private class UiScrollScenario(
        val scenario: UiScenario,
        val testName: String,
        val traceSections: List<String>,
        val navigate: MacrobenchmarkScope.() -> Unit,
        val gesture: MacrobenchmarkScope.() -> Unit,
    )

    private companion object {
        const val NAV_TIMEOUT_MS = 10_000L
        const val FLINGS_PER_DIRECTION = 3
        const val GESTURE_MARGIN_DIVISOR = 5
        const val GRID_SETTLE_POLL_MS = 100L
        const val FRAME_DURATION_METRIC = "frameDurationCpuMs"
        const val FRAME_OVERRUN_METRIC = "frameOverrunMs"

        /** The scenarios, keyed to [UiScenario] so [scrollScenarios] reports them in enum order. */
        val SCENARIOS = listOf(
            UiScrollScenario(
                scenario = UiScenario.BROWSE_BY_GAME_SCROLL,
                testName = "browseByGameScroll",
                traceSections = listOf("GridImage", "CrossfadeImage"),
                navigate = {
                    // The app opens on Home; "Browse by Game" is a row on the Library tab.
                    (device.wait(Until.findObject(By.text("Library")), NAV_TIMEOUT_MS)
                        ?: error("Could not find the 'Library' tab")).click()
                    (device.wait(Until.findObject(By.text("Browse by Game")), NAV_TIMEOUT_MS)
                        ?: error("Could not find 'Browse by Game' on Library screen")).click()

                    device.wait(Until.hasObject(By.scrollable(true)), NAV_TIMEOUT_MS)
                    device.waitForIdle()
                },
                gesture = { flingVertically() },
            ),
            UiScrollScenario(
                scenario = UiScenario.HOME_SCROLL,
                testName = "homeScroll",
                traceSections = listOf("GridImage", "CrossfadeImage"),
                navigate = {
                    // The app opens on Home. Home's modules emit asynchronously, so wait for a
                    // section the seeded benchmark snapshot always shows before flinging.
                    check(device.wait(Until.hasObject(By.text("Game of the day")), NAV_TIMEOUT_MS)) {
                        "Home did not show the 'Game of the day' section"
                    }
                    device.waitForIdle()
                },
                gesture = { flingVertically() },
            ),
            UiScrollScenario(
                scenario = UiScenario.BROWSE_ALL_TRACKS_SCROLL,
                testName = "browseAllTracksScroll",
                // Text-only rows: the tracks list emits no trace sections.
                traceSections = emptyList(),
                navigate = {
                    (device.wait(Until.findObject(By.text("Library")), NAV_TIMEOUT_MS)
                        ?: error("Could not find the 'Library' tab")).click()
                    (device.wait(Until.findObject(By.text("Browse All Tracks")), NAV_TIMEOUT_MS)
                        ?: error("Could not find 'Browse All Tracks' on Library screen")).click()

                    device.wait(Until.hasObject(By.scrollable(true)), NAV_TIMEOUT_MS)
                    device.waitForIdle()
                },
                gesture = { flingVertically() },
            ),
            UiScrollScenario(
                scenario = UiScenario.ARTIST_DETAIL_SCROLL,
                testName = "artistDetailScroll",
                // Artist detail renders a hero image and a horizontal game strip, each a
                // CrossfadeImage; unlike the grids it has no GridImage cells.
                traceSections = listOf("CrossfadeImage"),
                navigate = {
                    (device.wait(Until.findObject(By.text("Library")), NAV_TIMEOUT_MS)
                        ?: error("Could not find the 'Library' tab")).click()
                    (device.wait(Until.findObject(By.text("Browse by Artist")), NAV_TIMEOUT_MS)
                        ?: error("Could not find 'Browse by Artist' on Library screen")).click()

                    // The Library menu is itself a scrollable list, so wait until it is gone before
                    // resolving "the grid" — otherwise By.scrollable could still match the menu and
                    // the coordinate tap would land on a menu row.
                    device.wait(Until.gone(By.text("Favorites")), NAV_TIMEOUT_MS)
                    val grid = device.wait(Until.findObject(By.scrollable(true)), NAV_TIMEOUT_MS)
                        ?: error("Browse by Artist did not show its grid")
                    awaitPopulatedGrid(device, grid)

                    // The first artist tile has no static visible label — the artist name is
                    // data-dependent — so it cannot be selected with By.text. Tap it by coordinate
                    // derived from the grid node's visible bounds instead. The grid is two fixed
                    // columns, so width/4 lands inside the first cell (≈ first-column centre) and,
                    // because the cell is square with a 16dp top inset, width/4 is also roughly its
                    // vertical centre. This is inherently brittle: it assumes a COMPACT-width phone
                    // (a tablet widens the layout to a different grid) and tolerates only small
                    // changes to cell size, insets, or column count — tune it on a device run.
                    val bounds = grid.visibleBounds
                    device.click(bounds.left + bounds.width() / 4, bounds.top + bounds.width() / 4)

                    // "Play All" is the artist-detail CTA and is stable regardless of the artist's
                    // name, so it confirms the coordinate tap actually navigated (a no-op tap would
                    // otherwise let the gesture fling the browse grid instead).
                    check(device.wait(Until.hasObject(By.text("Play All")), NAV_TIMEOUT_MS)) {
                        "The artist tile coordinate tap did not reach artist detail"
                    }
                    device.waitForIdle()
                },
                gesture = { flingVertically() },
            ),
        )

        /** Flings the scenario screen's outer vertical list down then back up. */
        fun MacrobenchmarkScope.flingVertically() {
            val scroller = verticalScroller(device)
            scroller.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
            repeat(FLINGS_PER_DIRECTION) { scroller.fling(Direction.DOWN) }
            repeat(FLINGS_PER_DIRECTION) { scroller.fling(Direction.UP) }
        }

        /**
         * The screen's outer vertical scrollable. Home nests horizontal `LazyRow`s inside its
         * vertical `LazyColumn`, so `By.scrollable` matches several nodes; pick the largest upright
         * one rather than trusting accessibility-tree order (an implementation detail, not a
         * contract).
         */
        fun verticalScroller(device: UiDevice): UiObject2 =
            device.findObjects(By.scrollable(true))
                .filter { it.visibleBounds.height() > it.visibleBounds.width() }
                .maxByOrNull { it.visibleBounds.height() * it.visibleBounds.width() }
                ?: error("No vertical scrollable container")

        /**
         * Waits until [grid] shows real tiles rather than `LoadingType.SQUARE` placeholders. A
         * populated `GridImage` tile is clickable, whereas a loading placeholder is not, so the
         * first clickable node inside the grid's bounds is the signal — it needs no knowledge of
         * the data-dependent artist name.
         */
        fun awaitPopulatedGrid(device: UiDevice, grid: UiObject2) {
            val gridBounds = grid.visibleBounds
            val deadline = SystemClock.uptimeMillis() + NAV_TIMEOUT_MS
            while (SystemClock.uptimeMillis() < deadline) {
                val tile = device.findObjects(By.clickable(true))
                    .firstOrNull { gridBounds.contains(it.visibleBounds) }
                if (tile != null) return
                device.waitForIdle(GRID_SETTLE_POLL_MS)
            }
            error("Browse-by-Artist grid never showed a tappable artist tile")
        }
    }
}
