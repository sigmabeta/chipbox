package net.sigmabeta.chipbox

import android.app.Application
import dev.zacsweers.metro.createGraphFactory
import net.sigmabeta.chipbox.artworkprovider.api.ArtworkProviderGraph
import net.sigmabeta.chipbox.di.ChipboxAppGraph
import net.sigmabeta.chipbox.services.api.ChipboxServiceGraph
import net.sigmabeta.sage.ui.perf.isPerfMeasurementEnabled

class ChipboxApplication :
    Application(),
    ArtworkProviderGraph,
    ChipboxServiceGraph {
    override fun onCreate() {
        super.onCreate()
        // Release builds skip the measureTime { content() } wrapping in sage common/ui/perf-compose
        // so release composition is not paying for two System.nanoTime() calls per item.
        // (Was a compile-time `BuildConfig.DEBUG` const-fold pre-M9 — AGP 9's KMP Android
        // Library DSL doesn't expose buildConfig generation, so it's a runtime toggle now.)
        isPerfMeasurementEnabled = BuildConfig.DEBUG

        // Install the fatal-exception handler as early as possible so a crash during startup is
        // still serialized. Touching appGraph here forces the lazy graph to build now, which is
        // fine — onCreate runs once and the graph is reused by every later Activity/Service.
        appGraph.crashReporter.install()
    }

    /**
     * Application-wide Metro graph. Owned here so any Activity / Service / ContentProvider can
     * pull it via `(application as ChipboxApplication).appGraph` and read the accessors on
     * [ChipboxAppGraph] directly — the M6 replacement for Hilt's `@HiltAndroidApp` +
     * `@AndroidEntryPoint` / `@EntryPoint` member-injection machinery.
     *
     * `lazy` so the graph is constructed on first use, not at process start — `ArtworkProvider`
     * may run before `onCreate` in process-init, so the graph has to defer until something
     * actually reads it. The factory call binds `this` Application into the graph; modules
     * that take `Context` get the Context from `provideAppContext`.
     */
    val appGraph: ChipboxAppGraph by lazy {
        createGraphFactory<ChipboxAppGraph.Factory>().create(this)
    }

    // ArtworkProvider + ChipboxPlaybackService live in cbox/* modules and can't see
    // ChipboxAppGraph directly without a dependency cycle; they cast `applicationContext`
    // to their narrow graph interface and read what they need through it.
    override fun repository() = appGraph.repository
    override fun fileContentSource() = appGraph.fileContentSource
    override fun libraryBrowser() = appGraph.libraryBrowser
    override fun director() = appGraph.director
    override fun hatchet() = appGraph.hatchet
    override fun playbackSessionPersister() = appGraph.playbackSessionPersister
}
