package net.sigmabeta.chipbox.jvm.di

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import javax.inject.Named
import javax.inject.Singleton
import net.sigmabeta.chipbox.jvm.HelloViewModel
import net.sigmabeta.chipbox.jvm.LocalFileContentSource
import net.sigmabeta.chipbox.player.generator.real.RealGenerator
import net.sigmabeta.chipbox.player.speaker.file.FileSpeaker
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.real.RealScanner
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider
import java.io.File

/**
 * Metro counterpart to [JvmChipboxComponent] — Hilt → Metro migration M4c
 * (see docs/metro-migration.md). The plain-Dagger graph still owns runtime DI for the
 * desktop entry point; this interface only exists so Metro processes the same
 * `@ContributesTo(AppScope::class)` modules under [JvmModules] alongside Dagger's KSP,
 * proving the JVM target can be flipped to a Metro-only graph in a later slice.
 *
 * Mirrors `JvmChipboxComponent.Builder`'s three `@BindsInstance` parameters
 * (dbPath / workDir / outputDir) via a `@DependencyGraph.Factory`; each becomes a
 * `@Provides`-annotated factory param that Metro binds into the graph alongside the
 * contributed modules.
 *
 * Graph carries both `@Singleton` (javax) and `@SingleIn(AppScope::class)` scopes for the
 * same reason `ChipboxAppGraph` does: the JVM modules use the original `@Singleton`
 * annotations and Metro treats `javax.inject.Singleton` as a distinct scope.
 *
 * Does NOT extend `ViewModelGraph` yet — JVM-side `metrox-viewmodel-compose` wiring
 * (and removing the hand-rolled `JvmViewModelProvider`) is deferred to a later slice
 * after Android-side VMs have all migrated off `@HiltViewModel`.
 */
@Singleton
@SingleIn(AppScope::class)
@DependencyGraph(AppScope::class)
interface JvmChipboxGraph {
    val hatchet: Hatchet
    val stringProvider: StringProvider
    val repository: Repository
    val librarySource: LocalFileContentSource
    val scanner: RealScanner
    val generator: RealGenerator
    val speaker: FileSpeaker

    val helloViewModel: HelloViewModel

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides @Named("dbPath") dbPath: String,
            @Provides @Named("workDir") workDir: File,
            @Provides @Named("outputDir") outputDir: File,
        ): JvmChipboxGraph
    }
}
