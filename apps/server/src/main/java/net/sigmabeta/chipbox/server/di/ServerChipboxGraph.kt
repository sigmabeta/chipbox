package net.sigmabeta.chipbox.server.di

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import java.io.File
import kotlinx.coroutines.CoroutineScope
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.contentsource.LocalFileContentSource
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.real.RealScanner
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

/**
 * Metro graph for the headless HTTP server — the server twin of
 * [net.sigmabeta.chipbox.jvm.di.JvmChipboxGraph]. Same DB/scanner/repository wiring, none of the
 * player/speaker/buffer/UI plumbing the desktop app needs. Accessors are what `Main.kt` and the
 * Ktor route layer reach for; everything else is composed transitively by the binding containers
 * in [ServerModules].
 *
 * Caller-supplied paths (db file, work dir for `library-locations.txt`) enter the graph through
 * `@DependencyGraph.Factory`. The configured library directory is added to the
 * [LocalFileContentSource] *after* graph construction (Main.kt does it) — that keeps the factory
 * focused on filesystem layout, not policy.
 */
@SingleIn(AppScope::class)
@DependencyGraph(AppScope::class)
interface ServerChipboxGraph {
    val hatchet: Hatchet
    val stringProvider: StringProvider
    val repository: Repository
    val localFileContentSource: LocalFileContentSource
    val contentSources: ContentSourceRegistry
    val scanner: RealScanner

    /**
     * App-lifetime supervised scope (from `ServerCoroutinesModule`). Main.kt uses it to
     * subscribe to `scanner.state()` for the `/api/health` `scanning` flag — long-running but
     * not tied to a Ktor request.
     */
    val appScope: CoroutineScope

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides @Named("dbPath") dbPath: String,
            @Provides @Named("workDir") workDir: File,
        ): ServerChipboxGraph
    }
}
