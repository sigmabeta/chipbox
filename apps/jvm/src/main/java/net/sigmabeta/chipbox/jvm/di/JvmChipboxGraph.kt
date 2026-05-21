package net.sigmabeta.chipbox.jvm.di

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import java.io.File
import net.sigmabeta.chipbox.jvm.LocalFileContentSource
import net.sigmabeta.chipbox.player.generator.real.RealGenerator
import net.sigmabeta.chipbox.player.speaker.file.FileSpeaker
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.real.RealScanner
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

/**
 * Metro graph for the headless JVM / desktop target — the JVM equivalent of
 * [net.sigmabeta.chipbox.di.ChipboxAppGraph] on the Android side. Owns every `AppScope`-scoped
 * binding the JVM target needs; consumed via accessors on `Main.kt` for the headless
 * `scan` / `play` modes and via `metroViewModel<T>()` (through the inherited
 * [ViewModelGraph]) for the Compose Multiplatform desktop UI.
 *
 * Caller-supplied paths (db file, render-cache workdir, WAV output dir) enter the graph
 * through a `@DependencyGraph.Factory` taking the three `@Named`-qualified params via
 * `@Provides` factory params — replaces the old Dagger `@Component.Factory` /
 * `@BindsInstance` plumbing that lived in `JvmChipboxComponent` before M6.
 */
@SingleIn(AppScope::class)
@DependencyGraph(AppScope::class)
interface JvmChipboxGraph : ViewModelGraph {
    val hatchet: Hatchet
    val stringProvider: StringProvider
    val repository: Repository
    val librarySource: LocalFileContentSource
    val scanner: RealScanner
    val generator: RealGenerator
    val speaker: FileSpeaker

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides @Named("dbPath") dbPath: String,
            @Provides @Named("workDir") workDir: File,
            @Provides @Named("outputDir") outputDir: File,
        ): JvmChipboxGraph
    }
}
