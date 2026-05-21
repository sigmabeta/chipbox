package net.sigmabeta.chipbox.jvm.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import java.io.File
import net.sigmabeta.chipbox.jvm.LocalFileContentSource
import net.sigmabeta.chipbox.jvm.SourceDataLineSpeaker
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.generator.real.RealGenerator
import net.sigmabeta.chipbox.player.speaker.Speaker
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

    /**
     * Exposed for the headless CLI `play` mode in `Main.kt`, which routes audio to a WAV file
     * rather than the speakers. The Compose Desktop / `gui` mode goes through `Director`,
     * which receives the [Speaker] binding below ([SourceDataLineSpeaker]) — the WAV speaker
     * isn't used there.
     */
    val fileSpeaker: FileSpeaker

    /** Live speaker used by the desktop UI; bound to [Speaker] below. */
    val liveSpeaker: SourceDataLineSpeaker

    @Binds
    val RealGenerator.generatorBinding: Generator

    /**
     * Director receives this binding via `DirectorModule.provideDirector(speaker = …)`. The
     * desktop UI plays through it; the `play` CLI mode bypasses the binding and uses
     * [fileSpeaker] directly to write WAVs.
     */
    @Binds
    val SourceDataLineSpeaker.speakerBinding: Speaker

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides @Named("dbPath") dbPath: String,
            @Provides @Named("workDir") workDir: File,
            @Provides @Named("outputDir") outputDir: File,
        ): JvmChipboxGraph
    }
}
