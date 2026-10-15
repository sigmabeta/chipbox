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
import androidx.test.uiautomator.StaleObjectException
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
        val results = SCENARIOS.map(::runScenario)

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
        const val FLING_ATTEMPTS = 3
        const val GESTURE_MARGIN_DIVISOR = 5
        const val GRID_SETTLE_POLL_MS = 100L
        const val ARTIST_SCROLL_TIMEOUT_MS = 30_000L
        const val ARTIST_SWIPE_FROM = 0.80f
        const val ARTIST_SWIPE_TO = 0.28f
        const val ARTIST_SWIPE_STEPS = 10
        const val FRAME_DURATION_METRIC = "frameDurationCpuMs"
        const val FRAME_OVERRUN_METRIC = "frameOverrunMs"

        /**
         * The scenarios, built by mapping [UiScenario.all] through an exhaustive `when`, so every
         * enum value has a row and a new one is a compile error here rather than a runtime lookup
         * failure. The mapping preserves enum order, so [scrollScenarios] reports them as declared.
         */
        val SCENARIOS: List<UiScrollScenario> = UiScenario.all.map { scenario ->
            when (scenario) {
                UiScenario.BROWSE_BY_GAME_SCROLL -> UiScrollScenario(
                    scenario = scenario,
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
                )

                UiScenario.HOME_SCROLL -> UiScrollScenario(
                    scenario = scenario,
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
                )

                UiScenario.BROWSE_ALL_TRACKS_SCROLL -> UiScrollScenario(
                    scenario = scenario,
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
                )

                UiScenario.ARTIST_DETAIL_SCROLL -> UiScrollScenario(
                    scenario = scenario,
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
                        // resolving "the grid" — otherwise By.scrollable could still match the menu.
                        device.wait(Until.gone(By.text("Favorites")), NAV_TIMEOUT_MS)
                        val grid = device.wait(Until.findObject(By.scrollable(true)), NAV_TIMEOUT_MS)
                            ?: error("Browse by Artist did not show its grid")
                        awaitPopulatedGrid(device, grid)

                        // Open an artist with many tracks: the alphabetically-first artist can have a
                        // single track, whose detail page fits on screen and exposes no scrollable node
                        // (so there would be nothing to scroll). The target is read from the snapshot
                        // this run is pinned to, so it tracks the corpus rather than a hard-coded name.
                        val artist = BenchmarkArtists.richestArtistName(DeviceBenchmark.snapshotFile())
                            ?: error("Could not read an artist from the pushed library snapshot")
                        scrollArtistIntoView(device, artist)

                        val tile = device.wait(Until.findObject(By.text(artist)), NAV_TIMEOUT_MS)
                            ?: error("Artist tile '$artist' not found after scrolling the grid")
                        tile.click()

                        // "Play All" is the artist-detail CTA and is stable regardless of the artist's
                        // name, so it confirms the tap navigated (a no-op would fling the browse grid).
                        check(device.wait(Until.hasObject(By.text("Play All")), NAV_TIMEOUT_MS)) {
                            "Opening artist '$artist' did not reach artist detail"
                        }
                        device.waitForIdle()
                    },
                    gesture = { flingVertically() },
                )
            }
        }

        /** Flings the scenario screen's outer vertical list toward its end, then back to its start. */
        fun MacrobenchmarkScope.flingVertically() {
            repeat(FLINGS_PER_DIRECTION) { flingOnce(Direction.UP) }
            repeat(FLINGS_PER_DIRECTION) { flingOnce(Direction.DOWN) }
        }

        /**
         * One fling on a freshly resolved scroller, retrying if the node detached between lookup and
         * gesture. A `LazyColumn` recomposes as it settles — artist detail in particular composes a
         * hero and a game strip after navigating — so a node found a moment earlier can go stale
         * (`StaleObjectException`) before `fling` reads its bounds. Re-resolving per fling survives
         * that; a genuinely stale-but-refindable screen does not fail the run.
         */
        fun MacrobenchmarkScope.flingOnce(direction: Direction) {
            var lastStale: StaleObjectException? = null
            repeat(FLING_ATTEMPTS) {
                try {
                    val scroller = verticalScroller(device)
                    scroller.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
                    scroller.fling(direction)
                    return
                } catch (stale: StaleObjectException) {
                    lastStale = stale
                    device.waitForIdle()
                }
            }
            throw lastStale ?: error("Fling $direction failed after $FLING_ATTEMPTS attempts")
        }

        /**
         * The screen's outer vertical scrollable. Home nests horizontal `LazyRow`s inside its
         * vertical `LazyColumn`, so `By.scrollable` matches several nodes; pick the largest upright
         * one rather than trusting accessibility-tree order (an implementation detail, not a
         * contract). A node that goes stale mid-lookup is skipped rather than failing the run.
         */
        fun verticalScroller(device: UiDevice): UiObject2 =
            device.findObjects(By.scrollable(true))
                .mapNotNull { node -> runCatching { node.visibleBounds }.getOrNull()?.let { node to it } }
                .filter { (_, bounds) -> bounds.height() > bounds.width() }
                .maxByOrNull { (_, bounds) -> bounds.height() * bounds.width() }
                ?.first
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

        /**
         * Scrolls the artist grid toward its end until [name]'s tile is composed (and so tappable).
         * Uses a raw finger-up swipe over the screen — the same gesture that advances the name-ordered
         * grid by hand — rather than a `UiObject2` scroll, so it needs no scrollable node and cannot
         * suffer the node-lookup staleness that a `UiObject2` gesture can. The wait is capped by
         * [ARTIST_SCROLL_TIMEOUT_MS].
         */
        fun scrollArtistIntoView(device: UiDevice, name: String) {
            val deadline = SystemClock.uptimeMillis() + ARTIST_SCROLL_TIMEOUT_MS
            while (SystemClock.uptimeMillis() < deadline && !device.hasObject(By.text(name))) {
                device.swipe(
                    device.displayWidth / 2,
                    (device.displayHeight * ARTIST_SWIPE_FROM).toInt(),
                    device.displayWidth / 2,
                    (device.displayHeight * ARTIST_SWIPE_TO).toInt(),
                    ARTIST_SWIPE_STEPS,
                )
                device.waitForIdle(GRID_SETTLE_POLL_MS)
            }
        }
    }
}
