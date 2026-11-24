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
import androidx.test.platform.app.InstrumentationRegistry
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
        DeviceBenchmark.beginTest("scrollScenarios")
        try {
            // Probe the device facts (including the thermal status "at start") before the measured
            // loop; the fingerprint must not record the post-run thermal state.
            val environment = DeviceBenchmark.environment()
            val scenarios = selectedScenarios()
            val results = scenarios.map { scenario ->
                DeviceBenchmark.section(scenario.scenario.id) {
                    runScenario(scenario)
                }
            }

            DeviceBenchmark.write(
                DeviceBenchmark.perfResult(
                    environment = environment,
                    config = PerfConfig(scenarios = scenarios.map { it.scenario.id }),
                    axes = mapOf(
                        "ui" to UiAxis(
                            config = UiAxisConfig(iterations = DeviceBenchmark.ITERATIONS),
                            results = results,
                        ),
                    ),
                ),
            )
        } finally {
            DeviceBenchmark.endTest("scrollScenarios")
        }
    }

    /**
     * The scenarios to run: all of them, or the one named by the `scenario` instrumentation argument
     * (e.g. `...args=scenario:ui.artist_detail_scroll`). A single-scenario run is the fast way to tune
     * one device recipe without paying for the whole suite.
     */
    private fun selectedScenarios(): List<UiScrollScenario> {
        val id = InstrumentationRegistry.getArguments().getString(SCENARIO_ARG) ?: return SCENARIOS
        val scenario = UiScenario.fromId(id) ?: error("Unknown scenario id '$id' (from --scenario)")
        return SCENARIOS.filter { it.scenario == scenario }
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
                DeviceBenchmark.lockPortrait(device)
                pressHome()
                startActivityAndWait()
                scenario.navigate(this)
                scenario.warmUp(this)
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
     * One scroll scenario's device recipe: how to navigate to its screen, an optional unmeasured
     * [warmUp] run after navigating, the gesture to run inside the measured loop, and the trace
     * sections that screen emits. Keeping them together makes adding a screen a local change.
     *
     * [warmUp] runs in the setup block before every iteration, so each measured pass starts from the
     * same state. Without it the first iteration decodes the screen's images into a cold cache and
     * the rest hit a warm one.
     */
    private class UiScrollScenario(
        val scenario: UiScenario,
        val testName: String,
        val traceSections: List<String>,
        val navigate: MacrobenchmarkScope.() -> Unit,
        val gesture: MacrobenchmarkScope.() -> Unit,
        val warmUp: MacrobenchmarkScope.() -> Unit = {},
    )

    private companion object {
        const val NAV_TIMEOUT_MS = 10_000L
        const val SCENARIO_ARG = "scenario"
        const val FLINGS_PER_DIRECTION = 3
        const val GESTURE_ATTEMPTS = 3
        const val GESTURE_MARGIN_DIVISOR = 5
        const val SEARCH_FIELD_CLASS = "android.widget.EditText"
        const val SEARCH_RESULTS_TIMEOUT_MS = 15_000L
        const val SEARCH_SETTLE_POLL_MS = 100L
        const val SEARCH_SCROLL_ATTEMPTS = 6
        const val SEARCH_SCROLL_FRACTION = 0.5f
        const val SEARCH_TILE_TAPS = 2
        const val ARTIST_DETAIL_CTA = "Play All"

        /** Stroke length, as a share of the scroller's gesture area: far enough to dispose the strip. */
        const val ARTIST_DETAIL_SCROLL_PERCENT = 1.0f

        /** Stroke speed in dp/s, scaled by density so each stroke takes about as long on any phone. */
        const val ARTIST_DETAIL_SCROLL_DP_PER_SECOND = 1_200f

        /** Down-and-back cycles per measured pass; more cycles mean more frames per iteration. */
        const val ARTIST_DETAIL_SCROLL_CYCLES = 6

        /** Unmeasured cycles in the setup block, so every pass sees the same warm image cache. */
        const val ARTIST_DETAIL_WARM_UP_CYCLES = 1

        const val FRAME_DURATION_METRIC = "frameDurationCpuMs"
        const val FRAME_OVERRUN_METRIC = "frameOverrunMs"

        /**
         * The section headers the Search results grid renders. Their absence is what distinguishes
         * the history view (which also lists query text) from the submitted results.
         */
        val SEARCH_SECTION_HEADERS = listOf("Games", "Songs", "Artists")

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
                        (
                            device.wait(Until.findObject(By.text("Library")), NAV_TIMEOUT_MS)
                            ?: error("Could not find the 'Library' tab")
                        ).click()
                        (
                            device.wait(Until.findObject(By.text("Browse by Game")), NAV_TIMEOUT_MS)
                            ?: error("Could not find 'Browse by Game' on Library screen")
                        ).click()

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
                        (
                            device.wait(Until.findObject(By.text("Library")), NAV_TIMEOUT_MS)
                            ?: error("Could not find the 'Library' tab")
                        ).click()
                        (
                            device.wait(Until.findObject(By.text("Browse All Tracks")), NAV_TIMEOUT_MS)
                            ?: error("Could not find 'Browse All Tracks' on Library screen")
                        ).click()

                        device.wait(Until.hasObject(By.scrollable(true)), NAV_TIMEOUT_MS)
                        device.waitForIdle()
                    },
                    gesture = { flingVertically() },
                )

                UiScenario.ARTIST_DETAIL_SCROLL -> UiScrollScenario(
                    scenario = scenario,
                    testName = "artistDetailScroll",
                    // Artist detail's only images are the horizontal game strip's `WideItem`s — the
                    // hero renders only when the artist has a photo, which nothing supplies yet — so it
                    // records `CrossfadeImage` and, unlike the grids, no `GridImage`.
                    traceSections = listOf("CrossfadeImage"),
                    navigate = {
                        // Search, not Browse by Artist: the artist grid is dozens of screens long
                        // and its tiles only exist while composed, so scrolling to the target raced
                        // the grid settling and could spend the whole budget without a match. A
                        // name query matches the Artists section directly, wherever it sorts.
                        (
                            device.wait(Until.findObject(By.text("Search")), NAV_TIMEOUT_MS)
                            ?: error("Could not find the 'Search' tab")
                        ).click()

                        // Open an artist with many tracks: the alphabetically-first artist can have a
                        // single track, whose detail page fits on screen and exposes no scrollable node
                        // (so there would be nothing to scroll). The target is read from the snapshot
                        // this run is pinned to, so it tracks the corpus rather than a hard-coded name.
                        val artist = BenchmarkArtists.richestArtistName(DeviceBenchmark.snapshotFile())
                            ?: error("Could not read an artist from the pushed library snapshot")
                        check(openArtistBySearch(device, artist)) {
                            "Could not open artist '$artist' from search results"
                        }
                        device.waitForIdle()
                    },
                    warmUp = { scrollDownAndBack(ARTIST_DETAIL_WARM_UP_CYCLES) },
                    gesture = {
                        checkAtArtistDetailTop("before the measured pass")
                        scrollDownAndBack(ARTIST_DETAIL_SCROLL_CYCLES)
                    },
                )
            }
        }

        /**
         * Flings the scenario screen's outer vertical list toward its end, then back to its start.
         *
         * uiautomator's `fling(direction)` names where the content goes, so [Direction.DOWN] moves
         * toward the end of the list. Flinging UP first, from the top, is a no-op.
         */
        fun MacrobenchmarkScope.flingVertically() {
            repeat(FLINGS_PER_DIRECTION) { flingOnce(Direction.DOWN) }
            repeat(FLINGS_PER_DIRECTION) { flingOnce(Direction.UP) }
        }

        /**
         * Scrolls the outer vertical list toward its end and back, [cycles] times, as a fixed
         * workload: the same strokes, from the same offset, every pass.
         *
         * Unlike [flingVertically] the cycle is bounded and symmetric. On a very long page (an artist
         * with hundreds of songs), flinging to the end and back does not return to the top, so the
         * game strip stays disposed and its `CrossfadeImage` trace reads zero. A bounded cycle leaves
         * and re-enters the top region, so the strip recomposes inside the measured window.
         *
         * Each stroke is a `UiObject2.scroll`: a constant-speed drag that holds still for 250 ms
         * before lifting, so the release velocity is zero and the list does not fling. `device.drag`
         * cannot do that: it moves in a few 5 ms steps and lifts at full speed, so the page flung and
         * the next drag caught it at a random offset, and the frame count per pass varied by 7–17%.
         * Each stroke then waits for idle, and each cycle must end back at the top.
         */
        fun MacrobenchmarkScope.scrollDownAndBack(cycles: Int) {
            val density = InstrumentationRegistry.getInstrumentation().context.resources.displayMetrics.density
            val speed = (ARTIST_DETAIL_SCROLL_DP_PER_SECOND * density).toInt()
            repeat(cycles) { cycle ->
                // `scroll` names the content direction: DOWN moves toward the end, UP back to the top.
                scrollOnce(Direction.DOWN, speed)
                scrollOnce(Direction.UP, speed)
                checkAtArtistDetailTop("after scroll cycle ${cycle + 1} of $cycles")
            }
        }

        /** One no-fling scroll stroke on a freshly resolved scroller, then waits for the list to settle. */
        fun MacrobenchmarkScope.scrollOnce(direction: Direction, speed: Int) {
            withVerticalScroller("Scroll $direction") { it.scroll(direction, ARTIST_DETAIL_SCROLL_PERCENT, speed) }
            device.waitForIdle()
        }

        /**
         * Fails unless artist detail is scrolled back to its top, which is where [ARTIST_DETAIL_CTA]
         * is visible. A pass that starts anywhere else measures a different workload, so the run
         * fails instead of recording it.
         */
        fun MacrobenchmarkScope.checkAtArtistDetailTop(whenChecked: String) {
            check(device.hasObject(By.text(ARTIST_DETAIL_CTA))) {
                "Artist detail is not at its top $whenChecked ('$ARTIST_DETAIL_CTA' is not visible)"
            }
        }

        /** One fling on a freshly resolved scroller. */
        fun MacrobenchmarkScope.flingOnce(direction: Direction) {
            withVerticalScroller("Fling $direction") { it.fling(direction) }
        }

        /**
         * Runs [gesture] on a freshly resolved scroller, retrying if the node detached between lookup
         * and gesture. A `LazyColumn` recomposes as it settles (artist detail in particular composes
         * a game strip after navigating), so a node found a moment earlier can go stale
         * (`StaleObjectException`) before the gesture reads its bounds. Re-resolving per gesture
         * survives that, so a screen whose scroller is stale but can be found again does not fail
         * the run.
         */
        fun MacrobenchmarkScope.withVerticalScroller(name: String, gesture: (UiObject2) -> Unit) {
            var lastStale: StaleObjectException? = null
            repeat(GESTURE_ATTEMPTS) {
                try {
                    val scroller = verticalScroller(device)
                    scroller.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
                    gesture(scroller)
                    return
                } catch (stale: StaleObjectException) {
                    lastStale = stale
                    device.waitForIdle()
                }
            }
            throw lastStale ?: error("$name failed after $GESTURE_ATTEMPTS attempts")
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
         * Opens [name] through Search instead of the Browse-by-Artist grid, and reports whether
         * artist detail appeared.
         *
         * The query goes into the Compose `BasicTextField`, which surfaces to accessibility as an
         * `android.widget.EditText`: `setText` (ACTION_SET_TEXT) is the fast path, with shell
         * `input text` for a field that ignores it. Either way the field is read back, so a dropped
         * edit fails here instead of looking like a missing artist.
         *
         * Recent searches are the hazard: while `submittedQuery` is blank the grid lists history
         * rows, and one can read exactly [name] (each earlier iteration records this query after
         * a 3 s linger). Results and history share one grid, so the first `By.text(name)` match can
         * be a history row whose tap only refills the query. [awaitArtistTile] waits for a results
         * header first, which the history view never renders.
         */
        fun openArtistBySearch(device: UiDevice, name: String): Boolean {
            val field = awaitSearchField(device)
            val searchBarBottom = field?.let { runCatching { it.visibleBounds.bottom }.getOrDefault(0) } ?: 0
            return field != null &&
                enterSearchQuery(device, field, name) &&
                tapArtistTile(device, name, searchBarBottom)
        }

        /** The search box as the accessibility tree currently exposes it, re-resolved per poll. */
        fun searchField(device: UiDevice): UiObject2? =
            device.findObject(By.clazz(SEARCH_FIELD_CLASS))
                ?: device.findObjects(By.focused(true)).firstOrNull()

        /** Blocks until the Search screen's field is composed, then returns it (`null` on timeout). */
        fun awaitSearchField(device: UiDevice): UiObject2? {
            val deadline = SystemClock.uptimeMillis() + NAV_TIMEOUT_MS
            var field = searchField(device)
            while (field == null && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(SEARCH_SETTLE_POLL_MS)
                field = searchField(device)
            }
            return field
        }

        /**
         * Enters [query] into [field] and waits until it holds the text. `setText` is one
         * ACTION_SET_TEXT; a field that rejects it gets an IME-level `input text` into the focused
         * field instead, which needs spaces written as `%s` (see [inputTextArgument]).
         */
        fun enterSearchQuery(device: UiDevice, field: UiObject2, query: String): Boolean {
            runCatching { field.setText(query) }
            if (awaitSearchFieldText(device, query)) return true
            runCatching { field.click() }
            device.executeShellCommand("input text ${inputTextArgument(query)}")
            return awaitSearchFieldText(device, query)
        }

        /** Whether the search field now holds [expected] — re-resolved, since typing recomposes it. */
        fun awaitSearchFieldText(device: UiDevice, expected: String): Boolean {
            val deadline = SystemClock.uptimeMillis() + NAV_TIMEOUT_MS
            var text: String? = null
            while (text != expected && SystemClock.uptimeMillis() < deadline) {
                text = searchField(device)?.let { runCatching { it.text }.getOrNull() }
                if (text != expected) SystemClock.sleep(SEARCH_SETTLE_POLL_MS)
            }
            return text == expected
        }

        /**
         * [query] as an `adb shell input text` argument: `input` splits the command into arguments
         * unless each space is written `%s`, and the string is single-quoted (embedded quotes
         * escaped) so names with apostrophes reach the field verbatim.
         */
        fun inputTextArgument(query: String): String =
            "'" + query.replace(" ", "%s").replace("'", "'\\''") + "'"

        /**
         * Waits for, then taps, the artist result labelled [name]. The tap is retried once: one
         * that lands while the results grid is still settling is absorbed as a scroll-stop rather
         * than a click.
         */
        fun tapArtistTile(device: UiDevice, name: String, searchBarBottom: Int): Boolean {
            var opened = device.hasObject(By.text(ARTIST_DETAIL_CTA))
            var attempt = 0
            while (!opened && attempt < SEARCH_TILE_TAPS) {
                val tile = awaitArtistTile(device, name, searchBarBottom)
                opened = (tile != null && tapResultTile(device, tile)) ||
                    device.hasObject(By.text(ARTIST_DETAIL_CTA))
                attempt++
            }
            return opened
        }

        /**
         * Waits out the query's ~300 ms debounce and the results flows, then returns the tile for
         * [name] (`null` if it never appears, e.g. the snapshot's artist is not in this library).
         *
         * A section header is the "results are in" signal: the history view renders rows and an
         * empty prompt but never one, and the grid scrolls back to item 0 on a query change, so the
         * first header of a result set is on screen when it appears. Only after that does the tile
         * lookup run, so a same-named history row cannot be mistaken for the result.
         *
         * A title-matching query can push the Artists section below the fold, where a lazy grid has
         * not composed it yet; a bounded run of `scroll` strokes reaches it. Each stroke pauses
         * before lifting, so it carries no fling momentum and cannot skip the single-row Artists
         * section.
         */
        fun awaitArtistTile(device: UiDevice, name: String, searchBarBottom: Int): UiObject2? {
            val deadline = SystemClock.uptimeMillis() + SEARCH_RESULTS_TIMEOUT_MS
            var resultsShown = false
            while (!resultsShown && SystemClock.uptimeMillis() < deadline) {
                resultsShown = SEARCH_SECTION_HEADERS.any { device.hasObject(By.text(it)) }
                if (!resultsShown) SystemClock.sleep(SEARCH_SETTLE_POLL_MS)
            }

            var scrolls = 0
            while (resultsShown) {
                val tile = resultTile(device, name, searchBarBottom)
                if (tile != null) return tile
                if (scrolls >= SEARCH_SCROLL_ATTEMPTS) break
                scrollSearchResults(device)
                scrolls++
                SystemClock.sleep(SEARCH_SETTLE_POLL_MS)
            }
            return null
        }

        /**
         * The first fully-visible result tile labelled [name], below the search bar. The search
         * box's own node carries the query (which is [name] here), so [searchBarBottom] is what
         * keeps the field out of the match; a clipped tile is skipped so the tap does not land
         * off-screen or under the keyboard.
         */
        fun resultTile(device: UiDevice, name: String, searchBarBottom: Int): UiObject2? =
            device.findObjects(By.text(name)).firstOrNull { node ->
                val bounds = runCatching { node.visibleBounds }.getOrNull()
                bounds != null && bounds.height() > 0 && bounds.top > searchBarBottom &&
                    bounds.bottom <= device.displayHeight
            }

        /**
         * Taps [tile] — or the clickable cell containing its centre, since a grid tile's semantics
         * may sit on the cell rather than on its caption — and reports whether artist detail
         * opened. Settles first: a tap during an in-flight scroll is absorbed as a scroll-stop.
         */
        fun tapResultTile(device: UiDevice, tile: UiObject2): Boolean {
            device.waitForIdle()
            val bounds = runCatching { tile.visibleBounds }.getOrNull() ?: return false
            val cell = device.findObjects(By.clickable(true)).firstOrNull {
                runCatching { it.visibleBounds.contains(bounds.centerX(), bounds.centerY()) }
                    .getOrDefault(false)
            }
            runCatching { (cell ?: tile).click() }
            return device.wait(Until.hasObject(By.text(ARTIST_DETAIL_CTA)), NAV_TIMEOUT_MS)
        }

        /**
         * Drags the search-results grid toward its end by [SEARCH_SCROLL_FRACTION] of the screen.
         * The grid is lazy, so an artist section below the fold is not in the accessibility tree
         * until it composes. A `scroll` pauses 250 ms before lifting, so it carries no fling momentum
         * and cannot jump over a single-row section.
         */
        fun scrollSearchResults(device: UiDevice) {
            runCatching {
                val grid = verticalScroller(device)
                grid.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
                grid.scroll(Direction.UP, SEARCH_SCROLL_FRACTION)
            }
        }
    }
}
